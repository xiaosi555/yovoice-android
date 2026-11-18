package com.xiaosi555.yovoice

import android.os.Bundle
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.setPadding
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import java.util.Locale
import org.json.JSONObject

class ModelCenterActivity : ComponentActivity() {
    private lateinit var workManager: WorkManager
    private lateinit var sourcePicker: Spinner
    private val downloadStates = mutableMapOf<String, WorkInfo.State?>()
    @Volatile private var runtimeFamilies: Set<String> = emptySet()
    @Volatile private var runtimeReady: Boolean = false
    private val themeIndex by lazy { getSharedPreferences("yovoice-ui", MODE_PRIVATE).getInt("theme", 0).coerceIn(0, 5) }
    private val pageBg get() = intArrayOf(0xFFF7F7F7.toInt(),0xFF0F0F11.toInt(),0xFFF1F6F6.toInt(),0xFFF3EEE4.toInt(),0xFFEFF3ED.toInt(),0xFF04181A.toInt())[themeIndex]
    private val surface get() = intArrayOf(0xFFFFFFFF.toInt(),0xFF18181B.toInt(),0xFFFFFFFF.toInt(),0xFFFBF8F1.toInt(),0xFFFFFFFF.toInt(),0xFF082427.toInt())[themeIndex]
    private val primaryText get() = intArrayOf(0xFF18181A.toInt(),0xFFEDEDEF.toInt(),0xFF0D2427.toInt(),0xFF2B241D.toInt(),0xFF1C2620.toInt(),0xFFE2F2F2.toInt())[themeIndex]
    private val secondaryText get() = intArrayOf(0xFF77777D.toInt(),0xFF99999F.toInt(),0xFF61777A.toInt(),0xFF827568.toInt(),0xFF69776D.toInt(),0xFF9BBFC0.toInt())[themeIndex]
    private val accentColor get() = intArrayOf(0xFF18181A.toInt(),0xFFEDEDEF.toInt(),0xFF0B7A80.toInt(),0xFF9A4A24.toInt(),0xFF3B6B4C.toInt(),0xFF19C3CD.toInt())[themeIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            workManager = WorkManager.getInstance(applicationContext)
            setContentView(TextView(this).apply {
                text = "正在检查 Android 本地推理引擎…"
                textSize = 15f
                setTextColor(primaryText)
                gravity = Gravity.CENTER
            })
            Thread {
                val probe = runCatching { JSONObject(NativeEngine.probe()) }.getOrNull()
                val ready = probe?.optString("status") == "ready"
                val families = probe?.optJSONArray("families")
                val detected = if (families == null) emptySet() else {
                    (0 until families.length()).map { families.optString(it) }.filter { it.isNotBlank() }.toSet()
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        runtimeReady = ready
                        runtimeFamilies = detected
                        buildPage()
                    }
                }
            }.start()
        } catch (error: Throwable) {
            val fallback = TextView(this).apply {
                text = "模型中心暂时无法打开\n\n${error.localizedMessage ?: error.javaClass.simpleName}\n\n请返回工作室重试。"
                textSize = 15f
                setTextColor(primaryText)
                setPadding(dp(24))
            }
            setContentView(fallback)
        }
    }

    private fun buildPage() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18))
            setBackgroundColor(pageBg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(dp(18) + bars.left, dp(18) + bars.top, dp(18) + bars.right, dp(18) + bars.bottom)
            insets
        }
        root.addView(TextView(this).apply {
            text = "模型中心"
            textSize = 26f
            setTextColor(primaryText)
            gravity = Gravity.CENTER_VERTICAL
        }, params())
        root.addView(TextView(this).apply {
            text = "模型目录来自 Yovoice 上游，共 ${ModelCatalog.load(this@ModelCenterActivity).size} 个模型条目。点击下载后保存到手机本地。"
            textSize = 13f
            setTextColor(secondaryText)
            setPadding(0, dp(5), 0, dp(12))
        }, params())

        sourcePicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ModelCenterActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Hugging Face（默认）", "HF 镜像", "ModelScope")
            )
        }
        root.addView(TextView(this).apply {
            text = "下载源"
            textSize = 13f
            setTextColor(secondaryText)
        }, params())
        root.addView(sourcePicker, params())

        root.addView(TextView(this).apply {
            text = if (runtimeReady) {
                "说明：下载完成会逐文件校验大小和 SHA-256。当前原生引擎实际编入的模型家族：${runtimeFamilies.joinToString().ifBlank { "暂无" }}。目录中的其他模型可以下载保存，但尚未编入本机推理库，不能视为可运行。大模型可能超过手机可用存储或运行内存。"
            } else {
                "说明：当前 Android 原生推理库尚未成功初始化。模型下载仍会校验文件，但在引擎可用前不能进行本地推理。大模型可能超过手机可用存储或运行内存。"
            }
            textSize = 12f
            setTextColor(0xFF8A5B26.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(0xFFFFF2DD.toInt())
        }, params().apply { topMargin = dp(10); bottomMargin = dp(10) })

        root.addView(Button(this).apply {
            text = "返回"
            isAllCaps = false
            setOnClickListener { finish() }
        }, params().apply { bottomMargin = dp(8) })

        val models = ModelCatalog.load(this)
        val search = EditText(this).apply {
            hint = "搜索模型名称或模型家族"
            textSize = 14f
            setSingleLine(true)
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0x33808080)
            }
        }
        root.addView(search, params().apply { topMargin = dp(6); bottomMargin = dp(6) })

        val families = listOf("全部模型") + models.map { it.family }.distinct().sorted()
        val familyPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@ModelCenterActivity, android.R.layout.simple_spinner_dropdown_item, families)
        }
        root.addView(familyPicker, params().apply { bottomMargin = dp(8) })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val empty = TextView(this).apply {
            text = "没有找到匹配的模型"
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
            visibility = View.GONE
        }
        list.addView(empty, params())

        fun refreshModels() {
            val keyword = search.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val family = familyPicker.selectedItem?.toString() ?: "全部模型"
            val filtered = models.filter { model ->
                (family == "全部模型" || model.family == family) &&
                    (keyword.isEmpty() || model.name.lowercase(Locale.ROOT).contains(keyword) ||
                        model.family.lowercase(Locale.ROOT).contains(keyword) ||
                        model.id.lowercase(Locale.ROOT).contains(keyword))
            }
            list.removeAllViews()
            if (filtered.isEmpty()) {
                empty.visibility = View.VISIBLE
                list.addView(empty, params())
            } else {
                empty.visibility = View.GONE
                filtered.forEach { model ->
                    list.addView(createModelCard(model), params().apply {
                        topMargin = dp(5)
                        bottomMargin = dp(5)
                    })
                }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshModels()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        familyPicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = refreshModels()
        }
        refreshModels()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(list, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)
    }

    private fun createModelCard(model: ModelEntry): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12))
            background = GradientDrawable().apply { setColor(surface); cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0x22808080) }
        }
        card.addView(TextView(this).apply {
            text = model.name
            textSize = 17f
            setTextColor(primaryText)
        }, params())
        card.addView(TextView(this).apply {
            text = "${model.family} · ${model.version} · ${model.precision} · ${model.formattedSize()} · ${model.packageFiles().size} 个文件"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(4), 0, dp(4))
        }, params())
        card.addView(TextView(this).apply {
            text = if (model.packageSize() >= 2L * 1024 * 1024 * 1024) {
                "大模型：下载前请确认手机剩余空间；能否推理取决于设备内存与 Android 后端适配。"
            } else {
                "下载后仍需 Android 推理引擎支持，暂未标记为已验证可运行。"
            }
            textSize = 11f
            setTextColor(secondaryText)
        }, params())

        val status = TextView(this).apply {
            val engineSupportsFamily = runtimeReady && model.family in runtimeFamilies
            text = when {
                ModelCatalog.isInstalled(this@ModelCenterActivity, model) && engineSupportsFamily ->
                    "文件校验通过 · 当前引擎包含此模型家族（尚待设备推理实测）"
                ModelCatalog.isInstalled(this@ModelCenterActivity, model) ->
                    "文件校验通过 · 当前 Android 引擎未编入此模型家族"
                engineSupportsFamily ->
                    "尚未下载 · 当前引擎包含此模型家族"
                else -> "尚未下载 · 当前引擎尚未编入此模型家族"
            }
            textSize = 12f
            setTextColor(accentColor)
            setPadding(0, dp(7), 0, dp(3))
        }
        card.addView(status, params())

        val downloadable = model.isDownloadable()
        val action = Button(this).apply {
            isAllCaps = false
            setTextColor(if (themeIndex == 1 || themeIndex == 5) 0xFF06191A.toInt() else 0xFFFFFFFF.toInt())
            backgroundTintList = ColorStateList.valueOf(accentColor)
            textSize = 12f
        }
        card.addView(action, params())
        if (!downloadable) {
            status.text = "目录缺少完整 SHA-256 校验信息，暂不可安全下载"
            action.text = "暂不可下载"
            action.isEnabled = false
            action.alpha = 0.55f
        }
        val uniqueName = "model-download-${model.id}"
        var state: WorkInfo.State? = null
        var installed = ModelCatalog.isInstalled(this, model)

        fun update(info: WorkInfo?) {
            if (!downloadable) {
                status.text = "目录缺少完整 SHA-256 校验信息，暂不可安全下载"
                action.text = "暂不可下载"
                action.isEnabled = false
                return
            }
            state = info?.state
            installed = ModelCatalog.isInstalled(this, model)
            when (info?.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    val done = info.progress.getLong("downloaded", 0L)
                    val total = info.progress.getLong("total", model.packageSize())
                    val percent = if (total > 0L) (done * 100 / total).coerceIn(0, 100) else 0
                    status.text = if (info.state == WorkInfo.State.RUNNING) {
                        "下载中：$percent%（${ModelEntry.formatBytes(done)} / ${ModelEntry.formatBytes(total)}）"
                    } else {
                        "等待下载任务启动…"
                    }
                    action.text = "暂停下载"
                }
                WorkInfo.State.SUCCEEDED -> {
                    status.text = if (installed && runtimeReady && model.family in runtimeFamilies) "文件校验通过 · 当前引擎包含此模型家族（尚待设备推理实测）" else if (installed) "文件校验通过 · 当前 Android 引擎未编入此模型家族" else "安装文件缺失，请重新下载"
                    action.text = if (installed) "已下载" else "重新下载"
                }
                WorkInfo.State.CANCELLED -> {
                    status.text = "下载已暂停；已完成的文件和断点会保留"
                    action.text = "继续下载"
                }
                WorkInfo.State.FAILED -> {
                    status.text = "下载失败：${info.outputData.getString("error") ?: "可点击重试"}"
                    action.text = "重试下载"
                }
                else -> {
                    status.text = if (installed && runtimeReady && model.family in runtimeFamilies) "文件校验通过 · 当前引擎包含此模型家族（尚待设备推理实测）" else if (installed) "文件校验通过 · 当前 Android 引擎未编入此模型家族" else "尚未下载"
                    action.text = if (installed) "已下载" else "下载模型"
                }
            }
        }

        workManager.getWorkInfosForUniqueWorkLiveData(uniqueName).observe(this) { infos ->
            update(infos.firstOrNull())
        }
        action.setOnClickListener {
            if (!downloadable) return@setOnClickListener
            when (state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    workManager.cancelUniqueWork(uniqueName)
                    Toast.makeText(this, "已请求暂停，保留下载断点", Toast.LENGTH_SHORT).show()
                }
                WorkInfo.State.SUCCEEDED -> {
                    if (installed) Toast.makeText(this, if (runtimeReady && model.family in runtimeFamilies) "模型文件已校验；该家族已编入引擎，仍需设备实测" else "模型文件已校验，但当前引擎尚未编入该模型家族", Toast.LENGTH_LONG).show()
                    else enqueue(model, uniqueName)
                }
                else -> enqueue(model, uniqueName)
            }
        }
        return card
    }

    private fun enqueue(model: ModelEntry, uniqueName: String) {
        val source = when (sourcePicker.selectedItemPosition) {
            1 -> "mirror"
            2 -> "modelscope"
            else -> "huggingface"
        }
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(
                ModelDownloadWorker.KEY_MODEL_ID to model.id,
                ModelDownloadWorker.KEY_SOURCE to source
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria( androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        Toast.makeText(this, "已加入下载队列：${model.name}", Toast.LENGTH_SHORT).show()
    }

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
