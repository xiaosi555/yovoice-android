package com.xiaosi555.yovoice

import android.content.Context
import android.net.Uri
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

data class MultiTtsSpeaker(
    val engineCode: String,
    val code: String,
    val name: String,
    val description: String,
    val locale: String,
    val datPath: String?,
    val descText: String?
) {
    val hasLocalData: Boolean get() = !datPath.isNullOrBlank()
}

/** Imports only a user-selected MultiTTS ZIP; no device-wide file scanning is performed. */
object MultiTtsPackageImporter {
    private const val PREFS = "yovoice-multitts"
    private const val MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L

    data class ImportedPackage(
        val uri: String,
        val engineName: String,
        val engineCode: String,
        val speakers: List<MultiTtsSpeaker>
    )

    fun import(context: Context, uri: Uri): ImportedPackage {
        val resolver = context.contentResolver
        val metaSize = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
        }.getOrDefault(-1L)
        require(metaSize <= MAX_ARCHIVE_BYTES || metaSize < 0L) { "音色包超过 1 GB，暂不支持导入" }
        val cacheRoot = File(context.cacheDir, "multitts-import").apply {
            if (!exists() && !mkdirs()) error("无法创建导入缓存目录")
        }
        val stat = StatFs(cacheRoot.absolutePath)
        require(metaSize < 0L || stat.availableBytes > metaSize + 8L * 1024L * 1024L) { "手机可用空间不足，无法临时读取音色包" }
        val archive = File(cacheRoot, "selected-package.zip")
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        require(copied <= MAX_ARCHIVE_BYTES) { "音色包超过 1 GB，暂不支持导入" }
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            } ?: error("无法读取所选音色包")
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val configEntry = entries.firstOrNull { it.name.substringAfterLast('/') == "config.yaml" }
                    ?: error("压缩包根目录或子目录中缺少 config.yaml")
                val enginesEntry = entries.firstOrNull { it.name.substringAfterLast('/') == "engines.yaml" }
                    ?: error("压缩包中缺少 engines.yaml")
                val config = zip.getInputStream(configEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val engines = zip.getInputStream(enginesEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val datPaths = entries.map { it.name.replace('\\', '/') }
                    .filter { it.contains("/isstts/xTTSRes/") && it.endsWith(".voc.dat", true) }
                val descTexts = entries.filter { it.name.endsWith(".desc", true) }.associate { entry ->
                    val key = normalize(entry.name.substringAfterLast('/').removeSuffix(".desc"))
                    key to zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText().take(16_384) }
                }
                val engineMap = parseEngineNames(engines)
                val parsed = parseSpeakers(config, datPaths, descTexts)
                require(parsed.isNotEmpty()) { "config.yaml 中没有找到可识别的 Speaker 音色条目" }
                val engineCode = parsed.first().engineCode
                val packageResult = ImportedPackage(
                    uri.toString(),
                    engineMap[engineCode] ?: engineCode,
                    engineCode,
                    parsed
                )
                persist(context, packageResult)
                return packageResult
            }
        } finally {
            archive.delete()
            cacheRoot.listFiles()?.forEach { it.delete() }
        }
    }

    fun load(context: Context): ImportedPackage? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString("index", null) ?: return null
        val root = JSONObject(raw)
        val speakersJson = root.getJSONArray("speakers")
        val speakers = (0 until speakersJson.length()).map { i ->
            val item = speakersJson.getJSONObject(i)
            MultiTtsSpeaker(
                item.getString("engineCode"), item.getString("code"), item.getString("name"),
                item.optString("description"), item.optString("locale"), item.optString("datPath").ifBlank { null },
                item.optString("descText").ifBlank { null }
            )
        }
        ImportedPackage(root.getString("uri"), root.getString("engineName"), root.getString("engineCode"), speakers)
    }.getOrNull()

    private fun persist(context: Context, value: ImportedPackage) {
        val speakers = JSONArray()
        value.speakers.forEach { speaker ->
            speakers.put(JSONObject()
                .put("engineCode", speaker.engineCode)
                .put("code", speaker.code)
                .put("name", speaker.name)
                .put("description", speaker.description)
                .put("locale", speaker.locale)
                .put("datPath", speaker.datPath ?: "")
                .put("descText", speaker.descText ?: ""))
        }
        val json = JSONObject()
            .put("uri", value.uri)
            .put("engineName", value.engineName)
            .put("engineCode", value.engineCode)
            .put("speakers", speakers)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("index", json.toString()).apply()
    }

    private fun parseEngineNames(yaml: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var code: String? = null
        yaml.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("- code:")) {
                code = scalar(trimmed.substringAfter(':'))
            } else if (trimmed.startsWith("name:") && code != null) {
                result[code!!] = scalar(trimmed.substringAfter(':'))
            }
        }
        return result
    }

    private fun parseSpeakers(
        yaml: String,
        datPaths: List<String>,
        descTexts: Map<String, String>
    ): List<MultiTtsSpeaker> {
        val datByBase = datPaths.associateBy {
            normalize(it.substringAfterLast('/').removeSuffix(".voc.dat"))
        }
        val result = mutableListOf<MultiTtsSpeaker>()
        var engineCode = ""
        var fields = linkedMapOf<String, String>()
        fun flush() {
            val code = fields["code"].orEmpty()
            val name = fields["name"].orEmpty()
            if (code.isNotBlank() && name.isNotBlank()) {
                val dat = datByBase[normalize(code)]
                val desc = descTexts[normalize(code)]
                    ?: dat?.substringAfterLast('/')?.removeSuffix(".voc.dat")?.let { descTexts[normalize(it)] }
                result += MultiTtsSpeaker(
                    engineCode, code, name, fields["desc"].orEmpty(),
                    fields["locale"].orEmpty(), dat, desc
                )
            }
            fields = linkedMapOf()
        }
        yaml.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (line.isNotBlank() && !line.startsWith(" ") && trimmed.endsWith(":") && !trimmed.startsWith("-")) {
                flush()
                engineCode = trimmed.removeSuffix(":").trim()
            } else if (trimmed.startsWith("- !!") && trimmed.contains("Speaker")) {
                flush()
            } else {
                val match = Regex("^\\s{2,}([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*(.*)$").matchEntire(line)
                if (match != null) fields[match.groupValues[1]] = scalar(match.groupValues[2])
            }
        }
        flush()
        return result
    }

    private fun scalar(raw: String): String {
        val value = raw.trim()
        if (value == "null" || value == "~") return ""
        return if ((value.startsWith("'") && value.endsWith("'")) ||
            (value.startsWith("\"") && value.endsWith("\""))) value.substring(1, value.length - 1)
        else value
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
