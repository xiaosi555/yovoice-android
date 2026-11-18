package com.xiaosi555.yovoice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ProjectRecord(
    val id: String,
    val title: String,
    val category: String,
    val detail: String,
    val filePath: String,
    val mimeType: String,
    val createdAt: Long
)

object ProjectStore {
    private const val PREFS = "yovoice_projects"
    private const val PROJECTS_KEY = "records"
    private const val MAX_RECORDS = 100

    fun list(context: Context): List<ProjectRecord> = runCatching {
        val json = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PROJECTS_KEY, "[]") ?: "[]"
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ProjectRecord(
                        item.optString("id"),
                        item.optString("title"),
                        item.optString("category"),
                        item.optString("detail"),
                        item.optString("filePath"),
                        item.optString("mimeType"),
                        item.optLong("createdAt")
                    )
                )
            }
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    fun save(
        context: Context,
        title: String,
        category: String,
        detail: String,
        file: File,
        mimeType: String
    ) {
        require(file.isFile && file.length() > 0L) { "项目文件不存在或为空" }
        val now = System.currentTimeMillis()
        val record = ProjectRecord(
            now.toString() + "-" + file.name.hashCode().toUInt().toString(16),
            title,
            category,
            detail,
            file.absolutePath,
            mimeType,
            now
        )
        val records = (list(context).filterNot { it.filePath == file.absolutePath } + record)
            .sortedByDescending { it.createdAt }
            .take(MAX_RECORDS)
        val array = JSONArray()
        records.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("category", item.category)
                    .put("detail", item.detail)
                    .put("filePath", item.filePath)
                    .put("mimeType", item.mimeType)
                    .put("createdAt", item.createdAt)
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PROJECTS_KEY, array.toString()).apply()
    }

    fun delete(context: Context, id: String) {
        val target = list(context).firstOrNull { it.id == id } ?: return
        val root = File(context.applicationContext.filesDir, "projects").canonicalFile
        val file = runCatching { File(target.filePath).canonicalFile }.getOrNull()
        if (file != null && file.path.startsWith(root.path + File.separator)) file.delete()
        val array = JSONArray()
        list(context).filterNot { it.id == id }.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("category", item.category)
                    .put("detail", item.detail)
                    .put("filePath", item.filePath)
                    .put("mimeType", item.mimeType)
                    .put("createdAt", item.createdAt)
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(PROJECTS_KEY, array.toString()).apply()
    }

    fun loadDraft(context: Context, key: String): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("draft.$key", "").orEmpty()

    fun saveDraft(context: Context, key: String, value: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("draft.$key", value).apply()
    }
}
