package com.xiaosi555.yovoice

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.GridLayout
import android.content.ActivityNotFoundException
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.io.File
import org.json.JSONObject
import java.util.concurrent.Executors

/** Mobile studio shell following Yovoice desktop theme tokens and visual hierarchy. */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private var destroyed = false
    @Volatile private var nativeBusy = false
    private val nativeExecutor = Executors.newSingleThreadExecutor()
    private lateinit var body: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var nav: LinearLayout
    private var page = 0
    private var themeIndex = 0

    private data class Palette(val name: String, val bg: Int, val surface: Int, val text: Int, val secondary: Int, val accent: Int, val border: Int, val dark: Boolean)
    private val palettes = listOf(
        Palette("Paper", 0xFFF7F7F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF18181A.toInt(), 0xFF77777D.toInt(), 0xFF18181A.toInt(), 0x1F18181A, false),
        Palette("Ink", 0xFF0F0F11.toInt(), 0xFF18181B.toInt(), 0xFFEDEDEF.toInt(), 0xFF99999F.toInt(), 0xFFEDEDEF.toInt(), 0x26FFFFFF, true),
        Palette("Lagoon", 0xFFF1F6F6.toInt(), 0xFFFFFFFF.toInt(), 0xFF0D2427.toInt(), 0xFF61777A.toInt(), 0xFF0B7A80.toInt(), 0x2606272A, false),
        Palette("Sand", 0xFFF3EEE4.toInt(), 0xFFFBF8F1.toInt(), 0xFF2B241D.toInt(), 0xFF827568.toInt(), 0xFF9A4A24.toInt(), 0x263C2814, false),
        Palette("Sage", 0xFFEFF3ED.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C2620.toInt(), 0xFF69776D.toInt(), 0xFF3B6B4C.toInt(), 0x261E3223, false),
        Palette("Abyss", 0xFF04181A.toInt(), 0xFF082427.toInt(), 0xFFE2F2F2.toInt(), 0xFF9BBFC0.toInt(), 0xFF19C3CD.toInt(), 0x26A6F2F5, true)
    )
    private val p: Palette get() = palettes[themeIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeIndex = getSharedPreferences("yovoice-ui", MODE_PRIVATE).getInt("theme", 0).coerceIn(0, palettes.lastIndex)
        buildShell()
        initializeSystemTts()
    }

    private fun buildShell() {
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bg
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.bg) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(24)) }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = shape(p.surface, 0, p.border, dp(1))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(68)))
        setContentView(root)
        render()
    }

    private fun render() {
        body.removeAllViews()
        when (page) {
            0 -> renderStudio()
            1 -> renderCreate()
            2 -> renderModels()
            else -> renderSettings()
        }
        renderNav()
    }

    private fun renderHeader(title: String, subtitle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = column()
        brand.addView(label("Yovoice", 15, p.text, true))
        brand.addView(label(title, 29, p.text, true).apply { setPadding(0, dp(12), 0, 0) })
        brand.addView(label(subtitle, 13, p.secondary).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = textButton("设置", 14) { page = 3; render() }
        row.addView(settings, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(row)
    }

    private fun renderStudio() {
        renderHeader("语音工作室", "本地 AI 音频创作")
        val ready = label("●  系统语音服务准备中", 12, 0xFF238A59.toInt(), true)
        ready.setPadding(dp(12), dp(9), dp(12), dp(9))
        ready.background = shape(if (p.dark) 0x33238A59 else 0x1A238A59, dp(30))
        body.addView(ready, marginTop(16))
        val panel = card()
        panel.addView(section("文字转语音", "输入文字并选择可用的语音引擎"))
        input = EditText(this).apply {
            hint = "输入文字，开始创作…"
            textSize = 16f
            minLines = 5
            gravity = Gravity.TOP
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(14), p.border, dp(1))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        panel.addView(input, marginTop(14))
        val engine = TextView(this).apply {
            text = "手机系统语音引擎   ›"
            textSize = 14f; setTextColor(p.text); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0); background = shape(p.bg, dp(13), p.border, dp(1))
        }
        panel.addView(engine, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        panel.addView(primaryButton("试听朗读（手机 TTS）") { speak() }, marginTop(10))
        panel.addView(secondaryButton("本地语音 · Audio8 TTS") { speakWithLocalModel() }, marginTop(9))
        body.addView(panel, marginTop(18))
        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        shortcuts.addView(secondaryButton("导入音频") { chooseAudio() }, LinearLayout.LayoutParams(0, dp(64), 1f))
        shortcuts.addView(secondaryButton("模型中心") { page = 2; render() }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { leftMargin = dp(10) })
        body.addView(shortcuts, marginTop(12))
        body.addView(label("运行状态", 17, p.text, true), marginTop(24))
        status = label("正在初始化系统 TTS…", 13, p.secondary)
        body.addView(status, marginTop(8))
        body.addView(label("最近项目", 17, p.text, true), marginTop(24))
        body.addView(infoRow("中文旁白试听", "手机系统语音引擎"), marginTop(8))
        body.addView(infoRow("本地音频导入", "支持通过文件选择器载入音频"), marginTop(8))
    }

    private fun renderCreate() {
        renderHeader("创作工具", "从文字、音频开始你的创作")
        body.addView(toolRow("文字转语音", "选择手机系统 TTS 或本地 Audio8 引擎") { page = 0; render() }, marginTop(18))
        body.addView(toolRow("导入本地音频", "选择手机中的音频文件并播放") { chooseAudio() }, marginTop(10))
        body.addView(toolRow("模型与音色", "下载模型、查看本地安装状态") { page = 2; render() }, marginTop(10))
        body.addView(toolRow("声音克隆", "需要接入对应的 Android 声音克隆推理后端") { showFeaturePending("声音克隆") }, marginTop(10))
        body.addView(toolRow("字幕识别 / 转录", "导入音视频并生成字幕的 Android 后端待适配") { showFeaturePending("字幕识别 / 转录") }, marginTop(10))
        body.addView(toolRow("音轨分离 / 音乐 / MIDI", "桌面版模块纳入移植清单，尚未完成 Android 后端") { showFeaturePending("音轨分离 / 音乐 / MIDI") }, marginTop(10))
        body.addView(label("未完成 Android 引擎适配的功能会明确标注，不会伪装成可用。", 12, p.secondary), marginTop(18))
    }

    private fun renderModels() {
        renderHeader("模型中心", "下载、校验并管理本地模型")
        body.addView(label("模型目录包含上游模型清单。下载完成后还需匹配的 Android 推理后端才能运行。", 13, p.secondary), marginTop(12))
        body.addView(toolRow("打开模型管理", "查看 68 个模型条目、下载源与下载进度") {
            try { startActivity(Intent(this, ModelCenterActivity::class.java)) } catch (error: Throwable) {
                Toast.makeText(this, "模型中心打开失败：${error.localizedMessage ?: error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }, marginTop(18))
        body.addView(label("建议先从轻量模型开始。大型模型下载前请确认存储空间；实际推理能力取决于模型家族适配和设备资源。", 12, p.secondary), marginTop(14))
    }

    private fun renderSettings() {
        renderHeader("设置", "外观与设备语音服务")
        body.addView(label("界面主题", 17, p.text, true), marginTop(22))
        val themeGrid = GridLayout(this).apply { columnCount = 2; useDefaultMargins = false }
        palettes.forEachIndexed { index, palette ->
            val item = TextView(this).apply {
                text = (if (themeIndex == index) "✓  " else "") + palette.name
                textSize = 15f; setTextColor(palette.text)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = shape(palette.surface, dp(13), palette.border, dp(1))
                setOnClickListener {
                    themeIndex = index
                    getSharedPreferences("yovoice-ui", MODE_PRIVATE).edit().putInt("theme", index).apply()
                    render()
                }
            }
            themeGrid.addView(item, GridLayout.LayoutParams(GridLayout.spec(index / 2, 1f), GridLayout.spec(index % 2, 1f)).apply { width = 0; height = dp(54); setMargins(dp(4), dp(5), dp(4), dp(5)) })
        }
        body.addView(themeGrid, marginTop(10))
        body.addView(label("语音引擎", 17, p.text, true), marginTop(24))
        body.addView(infoRow("手机系统 TTS", if (ttsReady) "已就绪" else "未就绪或正在检测"), marginTop(8))
        body.addView(secondaryButton("选择语音引擎 / 打开系统语音设置") { showTtsOptions() }, marginTop(8))
        body.addView(infoRow("Android 本地推理引擎", "Audio8 TTS 首个适配目标"), marginTop(8))
        body.addView(label("主题名称与颜色沿用 Yovoice 桌面版主题体系；移动端针对单手操作重新排布。", 12, p.secondary), marginTop(18))
    }

    private fun renderNav() {
        nav.removeAllViews()
        listOf("⌂" to "工作室", "♫" to "创作", "▦" to "模型", "⚙" to "设置").forEachIndexed { index, pair ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { page = index; render() } }
            item.addView(LineIconView(this, arrayOf("home", "wave", "grid", "settings")[index], if (page == index) p.accent else p.secondary), LinearLayout.LayoutParams(dp(24), dp(24)))
            item.addView(label(pair.second, 11, if (page == index) p.accent else p.secondary, page == index).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
            nav.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = shape(p.surface, dp(20), p.border, dp(1))
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun section(title: String, sub: String) = column().apply { addView(label(title, 18, p.text, true)); addView(label(sub, 12, p.secondary).apply { setPadding(0, dp(4), 0, 0) }) }
    private fun infoRow(title: String, detail: String) = TextView(this).apply {
        text = "$title\n$detail"; textSize = 14f; setTextColor(p.text); setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(dp(14), dp(13), dp(14), dp(13)); background = shape(p.surface, dp(14), p.border, dp(1))
    }
    private fun toolRow(title: String, detail: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = shape(p.surface, dp(16), p.border, dp(1)); setOnClickListener { action() }
        addView(label(title, 16, p.text, true)); addView(label(detail, 12, p.secondary).apply { setPadding(0, dp(5), 0, 0) })
    }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun primaryButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 15f; setTextColor(if (p.dark) 0xFF06191A.toInt() else 0xFFFFFFFF.toInt())
        background = shape(p.accent, dp(14)); setOnClickListener { action() }
    }
    private fun secondaryButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 13f; setTextColor(p.text)
        background = shape(p.surface, dp(14), p.border, dp(1)); setOnClickListener { action() }
    }
    private fun textButton(value: String, size: Int, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = size.toFloat(); gravity = Gravity.CENTER; setTextColor(p.text)
        background = shape(p.surface, dp(14), p.border, dp(1)); setOnClickListener { action() }
    }
    private fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT, strokeWidth: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }
    private fun marginTop(value: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun speakWithLocalModel() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "请先输入要合成的文字", Toast.LENGTH_SHORT).show()
            return
        }
        if (nativeBusy) {
            Toast.makeText(this, "本地语音引擎正在处理，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        val model = ModelCatalog.load(this).firstOrNull { it.id == "audio8-tts-0.6b-q8" }
        if (model == null || !ModelCatalog.isInstalled(this, model)) {
            Toast.makeText(this, "请先到模型中心下载 Audio8 TTS 模型（约 1.43 GB）", Toast.LENGTH_LONG).show()
            page = 2
            render()
            return
        }
        nativeBusy = true
        if (::status.isInitialized) status.text = "正在检查 Android 原生语音引擎…"
        nativeExecutor.execute {
            try {
                val probe = JSONObject(NativeEngine.probe())
                check(probe.optString("status") == "ready") {
                    probe.optString("message", "Android 原生引擎不可用")
                }
                val families = probe.optJSONArray("families")
                check(families != null && (0 until families.length()).any { families.optString(it) == "audio8_tts" }) {
                    "当前原生库未包含 Audio8 TTS 模型支持"
                }
                val modelDir = ModelCatalog.installedDirectory(this, model.id)
                val manifest = JSONObject(File(modelDir, "yovoice-model.json").readText())
                val modelFile = File(modelDir, manifest.getString("mainFile"))
                check(modelFile.isFile && modelFile.length() > 0L) { "模型主文件不存在或为空" }

                runOnUiThread { if (!destroyed && ::status.isInitialized) status.text = "正在加载本地 Audio8 模型（首次加载可能较久）…" }
                val loaded = JSONObject(NativeEngine.loadModel(modelFile.absolutePath, model.family, 2))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "模型加载失败") }

                val output = File(cacheDir, "audio8-preview-${System.currentTimeMillis()}.wav")
                runOnUiThread { if (!destroyed && ::status.isInitialized) status.text = "模型已加载，正在合成中文语音…" }
                val result = JSONObject(NativeEngine.synthesize(text, "zh", output.absolutePath))
                check(result.optBoolean("ok")) { result.optString("error", "语音合成失败") }
                check(output.isFile && output.length() > 44L) { "合成结果文件无效" }

                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        if (::status.isInitialized) status.text = "Audio8 本地合成完成 · ${output.length() / 1024} KB"
                        playAudio(Uri.fromFile(output))
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        if (::status.isInitialized) status.text = "本地 AI 语音失败：${error.localizedMessage ?: error.javaClass.simpleName}"
                        Toast.makeText(this, "本地语音处理失败，请查看运行状态", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                nativeBusy = false
            }
        }
    }

    private fun initializeSystemTts() {
        ttsReady = false
        tts = try {
            TextToSpeech(applicationContext) { result -> onInit(result) }
        } catch (error: Throwable) {
            if (::status.isInitialized) status.text = "无法创建系统 TTS：${error.localizedMessage ?: error.javaClass.simpleName}"
            null
        }
    }

    private fun showTtsOptions() {
        val engines = runCatching { tts?.engines.orEmpty() }.getOrDefault(emptyList())
        val labels = engines.map { it.label?.toString()?.takeIf(String::isNotBlank) ?: it.name }
        val choices = labels + listOf("打开手机文字转语音设置", "安装语音数据")
        android.app.AlertDialog.Builder(this).setTitle("语音引擎")
            .setItems(choices.toTypedArray()) { _, which ->
                when {
                    which < engines.size -> {
                        val selected = engines[which]
                        runCatching { tts?.shutdown() }
                        ttsReady = false
                        tts = try { TextToSpeech(applicationContext, this, selected.name) } catch (e: Throwable) { null }
                        if (tts == null) Toast.makeText(this, "无法启动所选语音引擎", Toast.LENGTH_LONG).show()
                    }
                    which == engines.size -> launchTtsSettings()
                    else -> try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } catch (_: ActivityNotFoundException) { launchTtsSettings() }
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun launchTtsSettings() {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        try { startActivity(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "请在系统设置中搜索“文字转语音输出”", Toast.LENGTH_LONG).show()
        }
    }

    private fun showFeaturePending(name: String) {
        android.app.AlertDialog.Builder(this).setTitle(name)
            .setMessage("此模块属于电脑版功能范围，Android 版尚未完成对应推理引擎、文件流程和真机验证。当前不会显示为已可用。")
            .setPositiveButton("知道了", null).show()
    }

    private fun speak() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) { Toast.makeText(this, "请先输入要朗读的文字", Toast.LENGTH_SHORT).show(); return }
        val engine = tts
        if (!ttsReady || engine == null) {
            Toast.makeText(this, "系统 TTS 尚未就绪。请先选择语音引擎或安装中文语音数据。", Toast.LENGTH_LONG).show()
            showTtsOptions()
            return
        }
        val language = runCatching { engine.setLanguage(Locale.SIMPLIFIED_CHINESE) }.getOrDefault(TextToSpeech.ERROR)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = runCatching { engine.setLanguage(Locale.getDefault()) }.getOrDefault(TextToSpeech.ERROR)
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED || fallback == TextToSpeech.ERROR) {
                Toast.makeText(this, "当前引擎没有可用的中文语音数据，请安装语音包或切换引擎。", Toast.LENGTH_LONG).show()
                showTtsOptions(); return
            }
        }
        val result = runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yovoice-preview") }.getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.ERROR) Toast.makeText(this, "朗读请求失败，请切换系统 TTS 引擎。", Toast.LENGTH_LONG).show()
    }
    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*" }
        startActivityForResult(intent, REQUEST_AUDIO)
    }
    @Deprecated("System document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUDIO && resultCode == RESULT_OK) data?.data?.let { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            playAudio(uri)
        }
    }
    private fun playAudio(uri: Uri) {
        releasePlayer()
        try {
            player = MediaPlayer().apply {
                setOnPreparedListener { if (!destroyed) it.start() else it.release() }
                setOnErrorListener { _, _, _ -> Toast.makeText(this@MainActivity, "音频格式暂不支持", Toast.LENGTH_SHORT).show(); true }
                setDataSource(this@MainActivity, uri); prepareAsync()
            }
        } catch (e: Exception) { releasePlayer(); Toast.makeText(this, "无法打开音频：${e.localizedMessage}", Toast.LENGTH_LONG).show() }
    }
    override fun onInit(result: Int) {
        runOnUiThread {
            if (destroyed || isFinishing || isDestroyed) return@runOnUiThread
            ttsReady = result == TextToSpeech.SUCCESS
            val language = if (ttsReady) runCatching { tts?.setLanguage(Locale.SIMPLIFIED_CHINESE) ?: TextToSpeech.ERROR }.getOrDefault(TextToSpeech.ERROR) else TextToSpeech.ERROR
            val message = when {
                !ttsReady -> "系统 TTS 初始化失败，请检查手机语音服务。"
                language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED -> "语音服务已启动，但缺少中文数据；可切换引擎或安装语音包。"
                else -> "系统 TTS 已就绪，可以试听。"
            }
            if (::status.isInitialized) status.text = message
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            }
        }
    }
    private fun releasePlayer() { runCatching { player?.stop() }; runCatching { player?.release() }; player = null }
    override fun onDestroy() {
        destroyed = true; releasePlayer()
        runCatching {
            nativeExecutor.execute { NativeEngine.unload() }
            nativeExecutor.shutdown()
        }
        runCatching { tts?.stop() }; runCatching { tts?.shutdown() }; tts = null
        super.onDestroy()
    }
    companion object { private const val REQUEST_AUDIO = 4102 }
}
