package com.xiaosi555.yovoice

import android.os.Bundle
import android.app.AlertDialog
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
import android.widget.HorizontalScrollView
import android.widget.Spinner
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

class ModelCenterActivity : AppCompatActivity() {
    private lateinit var workManager: WorkManager
    private lateinit var sourcePicker: Spinner
    private val downloadStates = mutableMapOf<String, WorkInfo.State?>()
    private val modelCheckExecutor = Executors.newFixedThreadPool(2)
    private val sourceProbeExecutor = Executors.newFixedThreadPool(3)
    @Volatile private var runtimeFamilies: Set<String> = emptySet()
    @Volatile private var runtimeReady: Boolean = false
    private val themeIndex by lazy { getSharedPreferences("yovoice-ui", MODE_PRIVATE).getInt("theme", 0).coerceIn(0, 5) }
    private val accentIndex by lazy { getSharedPreferences("yovoice-ui", MODE_PRIVATE).getInt("accent", themeIndex).coerceIn(0, 5) }
    private val appearanceMode by lazy { getSharedPreferences("yovoice-ui", MODE_PRIVATE).getString("appearance", "system") ?: "system" }
    private val paletteDark: Boolean get() = when (appearanceMode) {
        "light" -> false
        "dark" -> true
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private val baseBg get() = intArrayOf(0xFFF7F7F7.toInt(),0xFF0F0F11.toInt(),0xFFF1F6F6.toInt(),0xFFF3EEE4.toInt(),0xFFEFF3ED.toInt(),0xFF04181A.toInt())[themeIndex]
    private val baseSurface get() = intArrayOf(0xFFFFFFFF.toInt(),0xFF18181B.toInt(),0xFFFFFFFF.toInt(),0xFFFBF8F1.toInt(),0xFFFFFFFF.toInt(),0xFF082427.toInt())[themeIndex]
    private val baseThemeDark get() = themeIndex == 1 || themeIndex == 5
    private val basePrimaryText get() = intArrayOf(0xFF18181A.toInt(),0xFFEDEDEF.toInt(),0xFF0D2427.toInt(),0xFF2B241D.toInt(),0xFF1C2620.toInt(),0xFFE2F2F2.toInt())[themeIndex]
    private val baseSecondaryText get() = intArrayOf(0xFF77777D.toInt(),0xFF99999F.toInt(),0xFF61777A.toInt(),0xFF827568.toInt(),0xFF69776D.toInt(),0xFF9BBFC0.toInt())[themeIndex]
    private val pageBg get() = if (paletteDark) {
        if (baseThemeDark) baseBg else mixColor(baseBg, android.graphics.Color.BLACK, 0.86f)
    } else {
        if (baseThemeDark) mixColor(baseBg, android.graphics.Color.WHITE, 0.88f) else baseBg
    }
    private val surface get() = if (paletteDark) {
        if (baseThemeDark) baseSurface else mixColor(baseSurface, android.graphics.Color.BLACK, 0.82f)
    } else {
        if (baseThemeDark) mixColor(baseSurface, android.graphics.Color.WHITE, 0.88f) else baseSurface
    }
    private val primaryText get() = if (paletteDark == baseThemeDark) basePrimaryText else if (paletteDark) 0xFFF1F3F4.toInt() else 0xFF202124.toInt()
    private val secondaryText get() = if (paletteDark == baseThemeDark) baseSecondaryText else if (paletteDark) 0xFFADB5B8.toInt() else 0xFF6F7478.toInt()
    private val baseAccent get() = intArrayOf(0xFFE0651C.toInt(),0xFFF08A4B.toInt(),0xFF139AA2.toInt(),0xFFC2662D.toInt(),0xFF5E8F45.toInt(),0xFF78E8EE.toInt())[accentIndex]
    private val accentColor get() = when {
        paletteDark && android.graphics.Color.luminance(baseAccent) < 0.35f -> mixColor(baseAccent, android.graphics.Color.WHITE, 0.72f)
        !paletteDark && android.graphics.Color.luminance(baseAccent) > 0.75f -> mixColor(baseAccent, android.graphics.Color.BLACK, 0.72f)
        else -> baseAccent
    }
    private fun mixColor(from: Int, to: Int, amount: Float): Int = android.graphics.Color.rgb(
        (android.graphics.Color.red(from) * (1f - amount) + android.graphics.Color.red(to) * amount).toInt(),
        (android.graphics.Color.green(from) * (1f - amount) + android.graphics.Color.green(to) * amount).toInt(),
        (android.graphics.Color.blue(from) * (1f - amount) + android.graphics.Color.blue(to) * amount).toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val appearance = getSharedPreferences("yovoice-ui", MODE_PRIVATE).getString("appearance", "system") ?: "system"
        ThemeMode.apply(this, appearance)
        setTheme(R.style.YovoiceTheme)
        val accentStyle = when (accentIndex) {
            0 -> R.style.YovoiceAccentPaper
            1 -> R.style.YovoiceAccentCoral
            2 -> R.style.YovoiceAccentLagoon
            3 -> R.style.YovoiceAccentSand
            4 -> R.style.YovoiceAccentSage
            else -> R.style.YovoiceAccentAbyss
        }
        theme.applyStyle(accentStyle, true)
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
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ModelCenterActivity).apply {
                text = "模型中心"
                textSize = 26f
                setTextColor(primaryText)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(infoAction("模型中心说明",
                "模型下载只负责获取文件并校验 SHA-256。下载完成不代表本机推理已验证；只有当前 Android 原生引擎支持的模型家族，才会开放对应功能入口。"), LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        root.addView(header, params())
        root.addView(TextView(this).apply {
            text = "${ModelCatalog.load(this@ModelCenterActivity).size} 个模型 · 默认按体积从小到大 · 下载后校验"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(2), 0, dp(8))
        }, params())

        sourcePicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ModelCenterActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("自动测速并选择最快源", "Hugging Face", "HF 镜像", "ModelScope")
            )
        }
        root.addView(TextView(this).apply {
            text = "下载源"
            textSize = 13f
            setTextColor(secondaryText)
        }, params())
        root.addView(sourcePicker, params())

        val runtimeExplanation = if (runtimeReady) {
            "当前原生引擎支持的模型家族：${runtimeFamilies.joinToString().ifBlank { "暂无" }}。其他模型可以下载保存，但尚不能在本机推理。下载前请确认存储空间；模型能否运行还取决于设备内存和实际任务测试。"
        } else {
            "当前 Android 原生推理引擎尚未成功初始化。模型下载仍会执行文件校验，但引擎可用前不能进行本地推理。"
        }
        val compatibilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ModelCenterActivity).apply {
                text = "设备兼容与空间要求"
                textSize = 12f
                setTextColor(secondaryText)
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(infoAction("设备兼容与空间要求", runtimeExplanation), LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        root.addView(compatibilityRow, params().apply { topMargin = dp(4); bottomMargin = dp(8) })

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
                }
        }
        root.addView(search, params().apply { topMargin = dp(6); bottomMargin = dp(6) })

        val featureCategories = ModelFeatureGuide.features.filter { it.modelFamilies.isNotEmpty() }
        val mappedFamilies = featureCategories.flatMap { it.modelFamilies }.toSet()
        val categories = mutableListOf("all" to "全部模型 ${models.size}")
        featureCategories.forEach { feature ->
            val count = models.count { it.family in feature.modelFamilies }
            categories.add(feature.id to "${feature.name} $count")
        }
        val otherCount = models.count { it.family !in mappedFamilies }
        if (otherCount > 0) categories.add("other" to "其他模型 $otherCount")
        var selectedCategory = intent.getStringExtra("focus_feature")
            ?.takeIf { requested -> categories.any { it.first == requested } } ?: "all"
        var refreshModels: () -> Unit = {}
        val categoryViews = linkedMapOf<String, TextView>()
        val categoryStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun updateCategoryAppearance() {
            categories.forEach { (id, _) ->
                val chip = categoryViews[id] ?: return@forEach
                val selected = selectedCategory == id
                val fill = if (selected) android.graphics.Color.argb(
                    30, android.graphics.Color.red(accentColor),
                    android.graphics.Color.green(accentColor), android.graphics.Color.blue(accentColor)
                ) else surface
                chip.setTextColor(if (selected) accentColor else primaryText)
                chip.background = android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(if (paletteDark) 0x33FFFFFF else 0x22000000),
                    GradientDrawable().apply {
                        setColor(fill)
                        cornerRadius = dp(18).toFloat()
                        setStroke(dp(1), if (selected) accentColor else 0x22808080)
                    }, null
                )
            }
        }
        categories.forEach { (id, title) ->
            val chip = TextView(this).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(14), 0, dp(14), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedCategory = id
                    updateCategoryAppearance()
                    refreshModels()
                }
            }
            categoryViews[id] = chip
            categoryStrip.addView(chip, LinearLayout.LayoutParams(-2, dp(48)).apply {
                rightMargin = dp(7)
            })
        }
        updateCategoryAppearance()
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(categoryStrip, ViewGroup.LayoutParams(-2, dp(52)))
        }, params().apply { topMargin = dp(4); bottomMargin = dp(8) })

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

        refreshModels = {
            val keyword = search.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val selectedFeature = featureCategories.firstOrNull { it.id == selectedCategory }
            val filtered = models.filter { model ->
                val categoryMatches = when (selectedCategory) {
                    "all" -> true
                    "other" -> model.family !in mappedFamilies
                    else -> selectedFeature?.let { model.family in it.modelFamilies } == true
                }
                val searchable = listOf(
                    model.name, model.family, model.id,
                    ModelFeatureGuide.modelPurpose(model.family),
                    ModelFeatureGuide.featureNames(model.family)
                ).joinToString(" ").lowercase(Locale.ROOT)
                categoryMatches && (keyword.isEmpty() || keyword.split(Regex("\\s+")).all { searchable.contains(it) })
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
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@ModelCenterActivity).apply {
                text = model.name
                textSize = 16f
                setTextColor(primaryText)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            val modelHelp = "主要用途：${ModelFeatureGuide.modelPurpose(model.family)}\n对应功能：${ModelFeatureGuide.featureNames(model.family)}\n\n" +
                if (model.packageSize() >= 2L * 1024 * 1024 * 1024) "这是大型模型。下载前确认剩余存储空间；实际运行还受设备可用内存和运行时支持影响。" else "下载后仍需通过本机原生运行时和实际任务验证。"
            addView(infoAction("模型说明", modelHelp), LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        card.addView(titleRow, params())
        card.addView(TextView(this).apply {
            text = "${model.family} · ${model.version} · ${model.precision} · ${model.formattedSize()} · ${model.packageFiles().size} 个文件"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(0, dp(2), 0, dp(4))
        }, params())
        card.addView(TextView(this).apply {
            text = "主要用途：${ModelFeatureGuide.modelPurpose(model.family)}"
            textSize = 12f
            setTextColor(secondaryText)
            setLineSpacing(dp(2).toFloat(), 1f)
            maxLines = 3
        }, params().apply { topMargin = dp(2) })
        card.addView(TextView(this).apply {
            text = "适配功能：${ModelFeatureGuide.featureNames(model.family)}"
            textSize = 11f
            setTextColor(secondaryText)
            maxLines = 2
        }, params().apply { topMargin = dp(2) })

        val status = TextView(this).apply {
            val engineSupportsFamily = runtimeReady && model.family in runtimeFamilies
            text = "正在检查本地模型文件及 SHA-256…"
            textSize = 12f
            setTextColor(accentColor)
            setPadding(0, dp(7), 0, dp(3))
        }
        card.addView(status, params())

        val runtimeSupported = runtimeReady && model.family in runtimeFamilies
        val downloadable = model.isDownloadable() && runtimeSupported
        val uniqueName = "model-download-${model.id}"
        var state: WorkInfo.State? = null
        var installed = false
        var latestInfo: WorkInfo? = null
        var verifiedForSucceeded = false
        var probingSource = false
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
                downloading -> "pause"
                installed -> "check"
                !downloadable -> "close"
                state == WorkInfo.State.FAILED -> "refresh"
                else -> "download"
            }
            val downloadDescription = when {
                !model.isDownloadable() -> "缺少 SHA-256 校验信息，不能下载"
                !runtimeReady -> "原生引擎尚未就绪，暂不能确认兼容性"
                !runtimeSupported -> "当前 APK 未编入此模型家族，下载后也无法使用"
                downloading -> "暂停下载"
                installed -> "模型已下载并校验"
                state == WorkInfo.State.FAILED -> "重试下载"
                state == WorkInfo.State.CANCELLED -> "继续下载"
                else -> "下载模型"
            }
            val canCancel = downloading
            updateIconAction(downloadAction, downloadIcon, if (probingSource) "正在测速下载源…" else downloadDescription,
                !installed && !probingSource && (downloadable || canCancel))
            val canUse = installed && runtimeReady && model.family in runtimeFamilies &&
                ModelFeatureGuide.features.any { model.family in it.modelFamilies }
            updateIconAction(useAction, "play",
                if (canUse) "进入对应功能并使用此模型" else "当前模型尚不能在此设备运行",
                canUse)
        }

        fun update(info: WorkInfo?) {
            latestInfo = info
            state = info?.state
            if (!model.isDownloadable()) {
                status.text = "模型目录缺少完整 SHA-256 校验信息，暂不可安全下载"
            } else if (!runtimeReady) {
                status.text = if (installed) "模型文件已下载并校验，但 Android 原生引擎未就绪；请先检查运行库" else "Android 原生引擎未就绪，暂不能确认模型兼容性"
            } else if (!runtimeSupported) {
                status.text = if (installed) {
                    "文件已下载并通过 SHA-256 校验，但当前 APK 未编入「${model.family}」推理模块，因此不能加载或使用；无需重复下载"
                } else {
                    "当前 APK 未编入「${model.family}」推理模块。下载后也无法使用，请选择已适配的模型家族"
                }
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
            if (installed || probingSource) return@setOnClickListener
            when (state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    workManager.cancelUniqueWork(uniqueName)
                    Toast.makeText(this, "已请求暂停，保留下载断点", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    if (!downloadable) {
                        Toast.makeText(this, "此模型家族尚未编入当前 APK，不能下载后使用。", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val selectedSource = when (sourcePicker.selectedItemPosition) {
                        1 -> "huggingface"
                        2 -> "mirror"
                        3 -> "modelscope"
                        else -> null
                    }
                    if (selectedSource != null) {
                        enqueue(model, uniqueName, selectedSource)
                    } else {
                        probingSource = true
                        status.text = "正在对模型文件进行小流量测速（每个源最多约 192 KB）…"
                        updateActions()
                        sourceProbeExecutor.execute {
                            val fastest = runCatching {
                                ModelDownloadSourceProbe.select(model, ModelCatalog.revision(this@ModelCenterActivity))
                            }.getOrNull()
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                probingSource = false
                                if (fastest == null) {
                                    status.text = "候选下载源均未能完成测速，请检查网络后重试。"
                                    Toast.makeText(this, "没有可用的模型下载源", Toast.LENGTH_LONG).show()
                                    updateActions()
                                } else {
                                    status.text = "已选择最快下载源：${fastest.label} · ${fastest.formattedSpeed()}，开始下载…"
                                    enqueue(model, uniqueName, fastest.source)
                                    updateActions()
                                }
                            }
                        }
                    }
                }
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

    private fun infoRow(title: String, message: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@ModelCenterActivity).apply {
            text = title
            textSize = 12f
            setTextColor(secondaryText)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        addView(infoAction(title, message), LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun infoAction(title: String, message: String): FrameLayout = FrameLayout(this).apply {
        contentDescription = "查看$title"
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = dp(12).toFloat()
        }
        addView(LineIconView(this@ModelCenterActivity, "info", secondaryText),
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        setOnClickListener {
            AlertDialog.Builder(this@ModelCenterActivity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun iconActionButton(icon: String, description: String): FrameLayout = FrameLayout(this).apply {
        background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
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
        }
    }

    private fun enqueue(model: ModelEntry, uniqueName: String, source: String) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(
                ModelDownloadWorker.KEY_MODEL_ID to model.id,
                ModelDownloadWorker.KEY_SOURCE to source
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria( androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        val sourceLabel = when (source) {
            "mirror" -> "HF 镜像"
            "modelscope" -> "ModelScope"
            else -> "Hugging Face"
        }
        Toast.makeText(this, "已选择 $sourceLabel · 已加入下载队列：${model.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        modelCheckExecutor.shutdownNow()
        sourceProbeExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun params() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
