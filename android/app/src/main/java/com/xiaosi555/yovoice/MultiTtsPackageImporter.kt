package com.xiaosi555.yovoice

import android.content.Context
import android.net.Uri
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
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

    data class ImportedPackage(
        val uri: String,
        val displayName: String,
        val fileSize: Long,
        val lastModified: Long,
        val engineName: String,
        val engineCode: String,
        val speakers: List<MultiTtsSpeaker>
    )

    fun importPackage(context: Context, uri: Uri): ImportedPackage {
        val resolver = context.contentResolver
        val metadata = readMetadata(context, uri)
        val metaSize = metadata.fileSize
        val cacheRoot = File(context.cacheDir, "multitts-import").apply {
            if (!exists() && !mkdirs()) error("无法创建导入缓存目录")
        }
        val stat = StatFs(cacheRoot.absolutePath)
        val availableBeforeCopy = stat.availableBytes
        val reserveBytes = 16L * 1024L * 1024L
        require(metaSize <= 0L || availableBeforeCopy > metaSize + reserveBytes) {
            "手机可用空间不足，无法临时读取音色包"
        }
        // Do not impose an arbitrary 1 GB package ceiling. When the document provider
        // cannot report a size, cap the copy to currently available space minus a reserve.
        val copyLimit = if (metaSize > 0L) metaSize else (availableBeforeCopy - reserveBytes).coerceAtLeast(0L)
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
                        require(copied <= copyLimit) { "音色包大小超过可用临时空间，已安全停止导入" }
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            } ?: error("无法读取所选音色包")
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val configEntry = entries.firstOrNull { it.name == "config.yaml" }
                    ?: error("压缩包根目录缺少 config.yaml")
                val enginesEntry = entries.firstOrNull { it.name == "engines.yaml" }
                    ?: error("压缩包根目录缺少 engines.yaml")
                val config = readZipText(zip, configEntry, 4 * 1024 * 1024)
                val engines = readZipText(zip, enginesEntry, 2 * 1024 * 1024)
                val datPaths = entries.map { it.name.replace('\\', '/') }
                    .filter { path ->
                        val normalizedPath = path.lowercase(Locale.ROOT)
                        (normalizedPath.startsWith("isstts/xttsres/") ||
                            normalizedPath.contains("/isstts/xttsres/")) &&
                            normalizedPath.endsWith(".dat")
                    }
                val descTexts = entries.asSequence()
                    .filter { it.name.endsWith(".desc", true) }
                    .take(1024)
                    .associate { entry ->
                        val key = normalize(entry.name.substringAfterLast('/').removeSuffix(".desc"))
                        key to zip.getInputStream(entry).use { readZipText(it, 16_384, truncate = true) }
                    }
                val engineMap = parseEngineNames(engines)
                val parsed = parseSpeakers(config, datPaths, descTexts)
                require(parsed.isNotEmpty()) { "config.yaml 中没有找到可识别的 Speaker 音色条目" }
                val engineCode = parsed.first().engineCode
                val packageResult = ImportedPackage(
                    uri.toString(),
                    metadata.displayName,
                    metadata.fileSize,
                    metadata.lastModified,
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
        val uri = Uri.parse(root.getString("uri"))
        val current = readMetadata(context, uri)
        val storedSize = root.optLong("fileSize", -1L)
        val storedModified = root.optLong("lastModified", -1L)
        val storedName = root.optString("displayName")
        if (storedName.isNotBlank() && current.displayName.isNotBlank() && storedName != current.displayName) return null
        if (storedSize > 0L && current.fileSize > 0L && storedSize != current.fileSize) return null
        if (storedModified > 0L && current.lastModified > 0L && storedModified != current.lastModified) return null
        context.contentResolver.openInputStream(uri)?.close() ?: return null
        val speakersJson = root.getJSONArray("speakers")
        val speakers = (0 until speakersJson.length()).map { i ->
            val item = speakersJson.getJSONObject(i)
            MultiTtsSpeaker(
                item.getString("engineCode"), item.getString("code"), item.getString("name"),
                item.optString("description"), item.optString("locale"), item.optString("datPath").ifBlank { null },
                item.optString("descText").ifBlank { null }
            )
        }
        ImportedPackage(root.getString("uri"), root.optString("displayName"), storedSize, storedModified,
            root.getString("engineName"), root.getString("engineCode"), speakers)
    }.getOrNull()

    fun hasSavedPackage(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("index")

    private data class UriMetadata(val displayName: String, val fileSize: Long, val lastModified: Long)

    private fun readMetadata(context: Context, uri: Uri): UriMetadata {
        var name = uri.lastPathSegment ?: "MultiTTS 音色包"
        var size = -1L
        var modified = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (index >= 0) modified = cursor.getLong(index)
                }
            }
        }
        return UriMetadata(name, size, modified)
    }

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
            .put("displayName", value.displayName)
            .put("fileSize", value.fileSize)
            .put("lastModified", value.lastModified)
            .put("engineName", value.engineName)
            .put("engineCode", value.engineCode)
            .put("speakers", speakers)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("index", json.toString()).apply()
    }

    internal fun parseEngineNames(yaml: String): Map<String, String> {
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

    internal fun parseSpeakers(
        yaml: String,
        datPaths: List<String>,
        descTexts: Map<String, String>
    ): List<MultiTtsSpeaker> {
        fun datBase(path: String): String {
            val fileName = path.substringAfterLast('/')
            val withoutExtension = when {
                fileName.endsWith(".voc.dat", true) -> fileName.dropLast(".voc.dat".length)
                fileName.endsWith(".dat", true) -> fileName.dropLast(".dat".length)
                else -> fileName
            }
            return normalize(withoutExtension)
        }
        val datByBase = datPaths.associateBy(::datBase)
        val result = mutableListOf<MultiTtsSpeaker>()
        var engineCode = ""
        var fields = linkedMapOf<String, String>()
        fun flush() {
            val code = fields["code"].orEmpty()
            val name = fields["name"].orEmpty()
            if (code.isNotBlank() && name.isNotBlank()) {
                val yamlDesc = fields["desc"].orEmpty()
                val descMatchedDat = if (yamlDesc.isBlank()) null else datPaths.firstOrNull { path ->
                    val base = datBase(path)
                    val content = descTexts[base].orEmpty()
                    content.isNotBlank() && normalize(content) == normalize(yamlDesc)
                }
                val dat = datByBase[normalize(code)] ?: descMatchedDat
                val desc = descTexts[normalize(code)]
                    ?: dat?.let(::datBase)?.let { descTexts[it] }
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
            } else if ((trimmed.startsWith("- !!") && trimmed.contains("Speaker")) ||
                (trimmed.startsWith("- ") && Regex("\\bcode\\s*:").containsMatchIn(trimmed))) {
                flush()
                val inline = Regex("\\b([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*(.*)$").find(trimmed.removePrefix("- "))
                if (inline != null) fields[inline.groupValues[1]] = scalar(inline.groupValues[2])
            } else {
                val match = Regex("^\\s{2,}([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*(.*)$").matchEntire(line)
                if (match != null) fields[match.groupValues[1]] = scalar(match.groupValues[2])
            }
        }
        flush()
        return result
    }


    private fun readZipText(zip: ZipFile, entry: java.util.zip.ZipEntry, maxBytes: Int): String =
        zip.getInputStream(entry).use { readZipText(it, maxBytes) }

    private fun readZipText(
        input: java.io.InputStream,
        maxBytes: Int,
        truncate: Boolean = false
    ): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < maxBytes) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (count < 0) break
            output.write(buffer, 0, count)
            total += count
        }
        if (!truncate && input.read() >= 0) {
            error("音色包中的 YAML 文件超过允许大小（${maxBytes / 1024} KB）")
        }
        return String(output.toByteArray(), Charsets.UTF_8)
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
