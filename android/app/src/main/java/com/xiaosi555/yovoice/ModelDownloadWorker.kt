package com.xiaosi555.yovoice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(
        inputData.getString(KEY_MODEL_ID) ?: "模型"
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return@withContext failure("缺少模型 ID")
        val source = inputData.getString(KEY_SOURCE) ?: "huggingface"
        try {
            setForeground(createForegroundInfo(modelId))
            val model = ModelCatalog.load(applicationContext).firstOrNull { it.id == modelId }
                ?: return@withContext failure("模型目录中不存在：$modelId")
            downloadPackage(model, source)
            Result.success()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: IOException) {
            if (runAttemptCount < 8) Result.retry()
            else failure("网络下载失败：${error.localizedMessage ?: "连接中断"}")
        } catch (error: Exception) {
            failure(error.localizedMessage ?: error.javaClass.simpleName)
        }
    }

    private suspend fun downloadPackage(model: ModelEntry, source: String) {
        val files = model.packageFiles()
        val total = files.sumOf { it.size }
        require(total > 0L) { "模型文件大小无效" }

        val modelsRoot = File(applicationContext.filesDir, "models")
        if (!modelsRoot.exists() && !modelsRoot.mkdirs()) throw IOException("无法创建模型目录")
        val staging = File(modelsRoot, ".${model.id}.staging")
        if (!staging.exists() && !staging.mkdirs()) throw IOException("无法创建临时下载目录")

        // 续传时只计算尚未下载的字节，避免把已完成的断点再次计入空间需求。
        var remaining = 0L
        for (file in files) {
            val relative = safeRelativePath(model.localRelativePath(file.path))
            val target = File(staging, relative)
            val validTarget = target.isFile &&
                target.length() == file.size &&
                sha256(target).equals(file.sha256, true)
            if (validTarget) continue
            if (target.exists() && !target.delete()) throw IOException("无法清理无效的临时模型文件")
            val part = File(target.parentFile ?: staging, target.name + ".part")
            val partialBytes = if (part.isFile) part.length().coerceIn(0L, file.size) else 0L
            remaining += file.size - partialBytes
        }
        val stat = StatFs(modelsRoot.absolutePath)
        val required = remaining + 64L * 1024L * 1024L
        if (stat.availableBytes < required) {
            throw IllegalStateException("手机可用空间不足，剩余下载至少需要 ${ModelEntry.formatBytes(required)}")
        }

        var completed = 0L
        val manifestFiles = JSONArray()

        for (file in files) {
            ensureActive()
            val relative = safeRelativePath(model.localRelativePath(file.path))
            val target = File(staging, relative)
            val parent = target.parentFile ?: throw IOException("模型文件路径无效")
            if (!parent.exists() && !parent.mkdirs()) throw IOException("无法创建模型子目录")

            if (target.isFile && target.length() == file.size && sha256(target).equals(file.sha256, true)) {
                completed += file.size
                manifestFiles.put(fileRecord(file, relative))
                setProgress(progressData(completed, total, model.name))
                continue
            }
            if (target.exists() && !target.delete()) throw IOException("无法清理旧模型文件")

            val part = File(target.parentFile, target.name + ".part")
            downloadFile(model.fileUrl(file, source, ModelCatalog.revision(applicationContext)), part, file.size, completed, total, model.name)
            if (part.length() != file.size) throw IOException("文件大小校验失败：${file.path}")
            if (!sha256(part).equals(file.sha256, true)) {
                part.delete()
                throw IllegalStateException("SHA-256 校验失败，已删除损坏文件：${file.path}")
            }
            if (!part.renameTo(target)) throw IOException("模型文件写入失败：${file.path}")
            completed += file.size
            manifestFiles.put(fileRecord(file, relative))
            setProgress(progressData(completed, total, model.name))
        }

        val mainRelative = safeRelativePath(model.localRelativePath(model.remotePath))
        val manifest = JSONObject()
            .put("modelId", model.id)
            .put("catalogRevision", ModelCatalog.revision(applicationContext))
            .put("version", model.version)
            .put("precision", model.precision)
            .put("mainFile", mainRelative)
            .put("files", manifestFiles)
            .put("verified", true)
            .put("verifiedAt", System.currentTimeMillis())
        File(staging, "yovoice-model.json").writeText(manifest.toString(), Charsets.UTF_8)

        val installed = ModelCatalog.installedDirectory(applicationContext, model.id)
        val backup = File(modelsRoot, ".${model.id}.backup")
        if (backup.exists()) backup.deleteRecursively()
        if (installed.exists() && !installed.renameTo(backup)) throw IOException("无法备份旧模型")
        if (!staging.renameTo(installed)) {
            if (backup.exists()) backup.renameTo(installed)
            throw IOException("模型安装切换失败")
        }
        backup.deleteRecursively()
        setProgress(progressData(total, total, model.name))
    }

    private suspend fun downloadFile(
        url: String,
        part: File,
        expectedSize: Long,
        completedBefore: Long,
        total: Long,
        modelName: String
    ) {
        var offset = if (part.isFile) part.length() else 0L
        if (offset > expectedSize) {
            part.delete()
            offset = 0L
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Yovoice-Android/0.1")
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = connection.responseCode
            if (code == 416 && offset == expectedSize) {
                return
            }
            if (code !in 200..299) throw IOException("下载服务器返回 HTTP $code")
            val append = offset > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (!append) offset = 0L
            FileOutputStream(part, append).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var lastUpdate = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        offset += count
                        if (offset > expectedSize) throw IOException("下载文件超过清单大小")
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 500L) {
                            setProgress(progressData(completedBefore + offset, total, modelName))
                            lastUpdate = now
                        }
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
        if (part.length() != expectedSize) throw IOException("下载未完成：${part.name}")
    }

    private fun fileRecord(file: ModelAssetFile, localPath: String) = JSONObject()
        .put("remotePath", file.path)
        .put("localPath", localPath)
        .put("size", file.size)
        .put("sha256", file.sha256)

    private fun safeRelativePath(value: String): String {
        val normalized = value.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized.split('/').none { it == ".." || it.isBlank() }) {
            "模型目录包含不安全路径"
        }
        return normalized
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ensureActive() {
        if (isStopped) throw kotlinx.coroutines.CancellationException("下载已暂停")
    }

    private fun progressData(done: Long, total: Long, name: String) =
        androidx.work.workDataOf("downloaded" to done, "total" to total, "modelName" to name)

    private fun failure(message: String): Result =
        Result.failure(androidx.work.workDataOf("error" to message))

    private fun createForegroundInfo(modelId: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "模型下载", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 Yovoice 模型")
            .setContentText(modelId)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_SOURCE = "source"
        private const val CHANNEL_ID = "yovoice-model-download"
        private const val NOTIFICATION_ID = 4108
    }
}
