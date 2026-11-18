package com.xiaosi555.yovoice

import android.os.Bundle
import android.content.Intent
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
import android.widget.FrameLayout
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
import java.util.concurrent.Executors
import org.json.JSONObject

class ModelCenterActivity : ComponentActivity() {
    private lateinit var workManager: WorkManager
    private lateinit var sourcePicker: Spinner
    private val downloadStates = mutableMapOf<String, WorkInfo.State?>()
    private val modelCheckExecutor = Executors.newFixedThreadPool(2)
    @Volatile private var runtimeFamilies: Set<String> = emptySet()
    @Volatile private var runtimeReady: Boolean = false
    private val themeIndex by lazy { getSharedPreferences("yovoice-ui", MODE_PRIVATE).getInt("theme", 0).coerceIn(0, 5) }
    private val appearanceMode by lazy { getSharedPreferences("yovoice-ui", MODE_PRIVATE).getString("appearance", "system") ?: "system" }
    private val paletteDark: Boolean get() = when (appearanceMode) {
        "light" -> false
        "dark" -> true
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private val baseBg get() = intArrayOf(0xFFF7F7F7.toInt(),0xFF0F0F11.toInt(),0xFFF1F6F6.toInt(),0xFFF3EEE4.toInt(),0xFFEFF3ED.toInt(),0xFF04181A.toInt())[themeIndex]
    private val baseSurface get() = intArrayOf(0xFFFFFFFF.toInt(),0xFF18181B.toInt(),0xFFFFFFFF.toInt(),0xFFFBF8F1.toInt(),0xFFFFFFFF.toInt(),0xFF082427.toInt())[themeIndex]
    private val baseThemeDark get() = themeIndex == 1 || themeIndex == 5
    private val pageBg get() = if (paletteDark) {
        if (baseThemeDark) baseBg else mixColor(baseBg, android.graphics.Color.BLACK, 0.86f)
    } else {
        if (baseThemeDark) mixColor(baseBg, android.graphics.Color.WHITE, 0.94f) else baseBg
    }
    private val surface get() = if (paletteDark) {
        if (baseThemeDark) baseSurface else mixColor(baseSurface, android.graphics.Color.BLACK, 0.82f)
    } else android.graphics.Color.WHITE
    private val primaryText get() = if (paletteDark) 0xFFF1F3F4.toInt() else 0xFF202124.toInt()
    private val secondaryText get() = if (paletteDark) 0xFFADB5B8.toInt() else 0xFF6F7478.toInt()
    private val accentColor get() = intArrayOf(0xFF18181A.toInt(),0xFFEDEDEF.toInt(),0xFF0B7A80.toInt(),0xFF9A4A24.toInt(),0xFF3B6B4C.toInt(),0xFF19C3CD.toInt())[themeIndex].let {
        if (paletteDark && themeIndex == 0) 0xFFE8EAED.toInt()
        else if (!paletteDark && themeIndex == 1) 0xFF242529.toInt()
        else it
    }
    private fun mixColor(from: Int, to: Int, amount: Float): Int = android.graphics.Color.rgb(
        (android.graphics.Color.red(from) * (1f - amount) + android.graphics.Color.red(to) * amount).toInt(),
        (android.graphics.Color.green(from) * (1f - amount) + android.graphics.Color.green(to) * amount).toInt(),
        (android.graphics.Color.blue(from) * (1f - amount) + android.graphics.Color.blue(to) * amount).toInt()
    )

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
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg
        val systemBars = WindowCompat.getInsetsController(window, window.decorView)
        systemBars.isAppearanceLightStatusBars = !paletteDark
        systemBars.isAppearanceLightNavigationBars = !paletteDark
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
            val requestedFamily = intent.getStringExtra("focus_family")
            if (!requestedFamily.isNullOrBlank()) {
                val requestedIndex = families.indexOf(requestedFamily)
                if (requestedIndex > 0) setSelection(requestedIndex)
            }
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
            }.sortedBy { it.packageSize() }
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
            text = "主要用途：${ModelFeatureGuide.modelPurpose(model.family)}"
            textSize = 12f
            setTextColor(primaryText)
            setPadding(0, dp(5), 0, dp(3))
        }, params())
        card.addView(TextView(this).apply {
            text = "对应功能：${ModelFeatureGuide.featureNames(model.family)}"
            textSize = 11f
            setTextColor(secondaryText)
            setPadding(0, dp(2), 0, dp(4))
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
            text = "正在检查本地模型文件及 SHA-256…"
            textSize = 12f
            setTextColor(accentColor)
            setPadding(0, dp(7), 0, dp(3))
        }
        card.addView(status, params())

        val downloadable = model.isDownloadable()
        val uniqueName = "model-download-${model.id}"
        var state: WorkInfo.State? = null
        var installed = false
        var latestInfo: WorkInfo? = null
        var verifiedForSucceeded = false
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        val downloadAction = iconActionButton("download", "下载模型")
        val useAction = iconActionButton("play", "在对应功能中使用")
        actionRow.addView(downloadAction, LinearLayout.LayoutParams(dp(48), dp(48)))
        actionRow.addView(useAction, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        card.addView(actionRow, params().apply { topMargin = dp(8) })

        fun updateActions() {
            val downloading = state == WorkInfo.State.RUNNING ||
                state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
            val downloadIcon = when {
                !downloadable -> "close"
                downloading -> "pause"
                installed -> "check"
                state == WorkInfo.State.FAILED -> "refresh"
                else -> "download"
            }
            val downloadDescription = when {
                !downloadable -> "缺少校验信息，不能下载"
                downloading -> "暂停下载"
                installed -> "模型已下载并校验"
                state == WorkInfo.State.FAILED -> "重试下载"
                state == WorkInfo.State.CANCELLED -> "继续下载"
                else -> "下载模型"
            }
            updateIconAction(downloadAction, downloadIcon, downloadDescription, downloadable && !installed)
            val canUse = installed && runtimeReady && model.family in runtimeFamilies &&
                ModelFeatureGuide.features.any { model.family in it.modelFamilies }
            updateIconAction(useAction, "play",
                if (canUse) "进入对应功能并使用此模型" else "当前模型尚不能在此设备运行",
                canUse)
        }

        fun update(info: WorkInfo?) {
            latestInfo = info
            state = info?.state
            if (!downloadable) {
                status.text = "目录缺少完整 SHA-256 校验信息，暂不可安全下载"
            } else when (info?.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    val done = info.progress.getLong("downloaded", 0L)
                    val total = info.progress.getLong("total", model.packageSize())
                    val percent = if (total > 0L) (done * 100 / total).coerceIn(0, 100) else 0
                    status.text = if (info.state == WorkInfo.State.RUNNING) {
                        "下载中：$percent%（${ModelEntry.formatBytes(done)} / ${ModelEntry.formatBytes(total)}）"
                    } else "等待下载任务启动…"
                }
                WorkInfo.State.SUCCEEDED -> {
                    status.text = if (installed && runtimeReady && model.family in runtimeFamilies) {
                        "已下载并通过 SHA-256 校验 · 本机引擎支持此模型家族，仍需实际任务验证"
                    } else if (installed) {
                        "已下载并通过校验 · 当前 Android 引擎未编入此模型家族，暂不能本机推理"
                    } else "安装文件缺失，请重新下载"
                }
                WorkInfo.State.CANCELLED -> status.text = "下载已暂停；已完成的文件和断点会保留"
                WorkInfo.State.FAILED -> status.text = "下载失败：${info.outputData.getString("error") ?: "可点击重试"}"
                else -> status.text = if (installed && runtimeReady && model.family in runtimeFamilies) {
                    "已下载并通过 SHA-256 校验 · 本机引擎支持此模型家族，仍需实际任务验证"
                } else if (installed) {
                    "已下载并通过校验 · 当前 Android 引擎未编入此模型家族，暂不能本机推理"
                } else "尚未下载"
            }
            updateActions()
        }

        fun refreshInstalled() {
            modelCheckExecutor.execute {
                val verified = runCatching { ModelCatalog.isInstalled(this@ModelCenterActivity, model) }.getOrDefault(false)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        installed = verified
                        update(latestInfo)
                    }
                }
            }
        }

        workManager.getWorkInfosForUniqueWorkLiveData(uniqueName).observe(this) { infos ->
            val info = infos.firstOrNull()
            update(info)
            if (info?.state == WorkInfo.State.SUCCEEDED && !verifiedForSucceeded) {
                verifiedForSucceeded = true
                refreshInstalled()
            } else if (info?.state != WorkInfo.State.SUCCEEDED) {
                verifiedForSucceeded = false
            }
        }

        downloadAction.setOnClickListener {
            if (!downloadable || installed) return@setOnClickListener
            when (state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    workManager.cancelUniqueWork(uniqueName)
                    Toast.makeText(this, "已请求暂停，保留下载断点", Toast.LENGTH_SHORT).show()
                }
                else -> enqueue(model, uniqueName)
            }
        }
        useAction.setOnClickListener {
            if (!installed || !runtimeReady || model.family !in runtimeFamilies) {
                Toast.makeText(this, "此模型尚未满足本机运行条件", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val mapped = ModelFeatureGuide.features.filter { model.family in it.modelFamilies }
            val requested = intent.getStringExtra("focus_feature")
            val target = mapped.firstOrNull { it.id == requested } ?: mapped.firstOrNull()
            if (target == null) {
                Toast.makeText(this, "尚未关联可用的创作功能", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val prefs = getSharedPreferences("yovoice-model-selection", MODE_PRIVATE).edit()
            mapped.forEach { prefs.putString(it.id, model.id) }
            prefs.apply()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_feature", target.id)
                putExtra("selected_model_id", model.id)
            })
            finish()
        }

        refreshInstalled()
        updateActions()
        return card
    }

    private fun iconActionButton(icon: String, description: String): FrameLayout = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x33808080)
        }
        isClickable = true
        isFocusable = true
        contentDescription = description
        addView(LineIconView(this@ModelCenterActivity, icon, accentColor),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(70L).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
            false
        }
    }

    private fun updateIconAction(view: FrameLayout, icon: String, description: String, enabled: Boolean) {
        view.removeAllViews()
        view.addView(LineIconView(this, icon, if (enabled) accentColor else secondaryText),
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        view.contentDescription = description
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.42f
        view.background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), if (enabled) 0x55808080 else 0x22808080)
        }
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

    override fun onDestroy() {
        modelCheckExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
