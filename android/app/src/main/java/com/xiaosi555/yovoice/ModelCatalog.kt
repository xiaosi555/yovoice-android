package com.xiaosi555.yovoice

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class ModelAssetFile(
    val path: String,
    val size: Long,
    val sha256: String,
    val repo: String?,
    val revision: String?
)

data class ModelEntry(
    val id: String,
    val name: String,
    val family: String,
    val version: String,
    val precision: String,
    val remotePath: String,
    val size: Long,
    val sha256: String,
    val repo: String?,
    val modelScope: String?,
    val revision: String?,
    val directory: String?,
    val stripPrefix: String?,
    val engineMinimum: String?,
    val files: List<ModelAssetFile>
) {
    fun packageFiles(): List<ModelAssetFile> =
        files.ifEmpty { listOf(ModelAssetFile(remotePath, size, sha256, null, null)) }

    fun localRelativePath(path: String): String {
        val prefix = stripPrefix?.trim('/')?.takeIf { it.isNotEmpty() } ?: return path
        return if (path.startsWith("$prefix/")) path.removePrefix("$prefix/") else path
    }

    fun packageSize(): Long = packageFiles().sumOf { it.size }

    fun fileUrl(file: ModelAssetFile, source: String, catalogRevision: String): String {
        val selectedRepo = file.repo ?: repo
        val selectedRevision = file.revision ?: revision ?: catalogRevision
        val encodedPath = java.net.URLEncoder.encode(file.path, Charsets.UTF_8.name()).replace("+", "%20").replace("%2F", "/")
        if (selectedRepo != null) {
            return when (source) {
                "mirror" -> "https://hf-mirror.com/$selectedRepo/resolve/$selectedRevision/$encodedPath"
                "modelscope" -> if (!modelScope.isNullOrBlank() && file.repo == null) {
                    "https://modelscope.cn/models/$modelScope/resolve/master/$encodedPath"
                } else {
                    "https://hf-mirror.com/$selectedRepo/resolve/$selectedRevision/$encodedPath"
                }
                else -> "https://huggingface.co/$selectedRepo/resolve/$selectedRevision/$encodedPath"
            }
        }
        return when (source) {
            "mirror" -> "https://hf-mirror.com/audio-cpp/audio.cpp-gguf/resolve/$catalogRevision/$encodedPath"
            "modelscope" -> "https://modelscope.cn/models/HereIsMark/audio.cpp-gguf/resolve/$MODEL_SCOPE_REVISION/$encodedPath"
            else -> "https://huggingface.co/audio-cpp/audio.cpp-gguf/resolve/$catalogRevision/$encodedPath"
        }
    }

    fun formattedSize(): String = formatBytes(packageSize())

    companion object {
        private const val MODEL_SCOPE_REVISION = "c5046e531f656253f082b7f74384ca59ec6d2a4c"

        fun formatBytes(value: Long): String {
            if (value < 1024L) return "$value B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var amount = value.toDouble()
            var unit = -1
            do {
                amount /= 1024.0
                unit++
            } while (amount >= 1024.0 && unit < units.lastIndex)
            return String.format(Locale.US, "%.2f %s", amount, units[unit])
        }
    }
}

object ModelCatalog {
    @Volatile private var cachedRevision: String? = null
    @Volatile private var cachedModels: List<ModelEntry>? = null

    fun load(context: Context): List<ModelEntry> {
        cachedModels?.let { return it }
        synchronized(this) {
            cachedModels?.let { return it }
            val raw = context.assets.open("catalog.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val revision = root.getString("revision")
            require(revision.length == 40) { "模型目录 revision 无效" }
            val array = root.getJSONArray("models")
            val result = ArrayList<ModelEntry>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val fileArray = item.optJSONArray("files")
                val files = ArrayList<ModelAssetFile>()
                if (fileArray != null) {
                    for (j in 0 until fileArray.length()) {
                        val f = fileArray.getJSONObject(j)
                        files += ModelAssetFile(
                            path = f.getString("path"),
                            size = f.getLong("size"),
                            sha256 = f.getString("sha256"),
                            repo = f.optString("repo").takeIf { it.isNotBlank() },
                            revision = f.optString("revision").takeIf { it.isNotBlank() }
                        )
                    }
                }
                result += ModelEntry(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    family = item.getString("family"),
                    version = item.getString("version"),
                    precision = item.getString("precision"),
                    remotePath = item.getString("remotePath"),
                    size = item.getLong("size"),
                    sha256 = item.getString("sha256"),
                    repo = item.optString("repo").takeIf { it.isNotBlank() },
                    modelScope = item.optString("modelScope").takeIf { it.isNotBlank() },
                    revision = item.optString("revision").takeIf { it.isNotBlank() },
                    directory = item.optString("directory").takeIf { it.isNotBlank() },
                    stripPrefix = item.optString("stripPrefix").takeIf { it.isNotBlank() },
                    engineMinimum = item.optString("engineMinimum").takeIf { it.isNotBlank() },
                    files = files
                )
            }
            cachedRevision = revision
            cachedModels = result
            return result
        }
    }

    fun revision(context: Context): String {
        load(context)
        return cachedRevision ?: error("模型目录尚未加载")
    }

    fun installedDirectory(context: Context, id: String): File {
        val root = File(context.filesDir, "models")
        val installed = File(root, id)
        val backup = File(root, ".$id.backup")
        // 上次安装若在目录切换中被系统终止，优先恢复上一份完整模型。
        if (!installed.exists() && backup.exists()) backup.renameTo(installed)
        return installed
    }

    fun isInstalled(context: Context, model: ModelEntry): Boolean {
        val root = installedDirectory(context, model.id)
        val manifest = File(root, "yovoice-model.json")
        if (!manifest.isFile) return false
        return runCatching {
            val data = JSONObject(manifest.readText())
            if (data.optString("modelId") != model.id) return false
            if (!data.optBoolean("verified", false)) return false
            if (data.optString("version") != model.version) return false
            val files = data.getJSONArray("files")
            if (files.length() != model.packageFiles().size) return false
            for (i in 0 until files.length()) {
                val entry = files.getJSONObject(i)
                val file = File(root, entry.getString("localPath"))
                if (!file.isFile || file.length() != entry.getLong("size")) return false
            }
            true
        }.getOrDefault(false)
    }
}
