package com.xiaosi555.yovoice

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.content.res.Configuration
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.GridLayout
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.SeekBar
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
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mobile studio shell following Yovoice desktop theme tokens and visual hierarchy. */
class MainActivity : AppCompatActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private var destroyed = false
    @Volatile private var nativeBusy = false
    private val nativeExecutor = Executors.newSingleThreadExecutor()
    private val assistantExecutor = Executors.newSingleThreadExecutor()
    private val storageExecutor = Executors.newSingleThreadExecutor()
    private lateinit var body: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var nav: LinearLayout
    private var page = 1
    private var selectedCreateFeature: String? = null
    private var activeWorkspaceFeature: String? = null
    private var themeIndex = 0
    private var accentIndex = 0
    private var appearanceMode = "system"
    private var lastBackPressedAt = 0L
    private var nativeLoadedModelId: String? = null
    private var activeModelStatusView: TextView? = null
    private var ttsSubtitleEnabled = true
    private var ttsSubtitlePreview: TextView? = null
    private var pendingTtsSubtitleSrt = ""
    private var selectedVoiceName: String? = null
    private var availableTtsVoices: List<android.speech.tts.Voice> = emptyList()
    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private lateinit var voicePicker: TextView
    private var multiTtsPackage: MultiTtsPackageImporter.ImportedPackage? = null
    private var pendingMultiTtsReturn = false
    private lateinit var enginePicker: TextView
    private lateinit var speedLabel: TextView
    private lateinit var pitchLabel: TextView
    private lateinit var speedSeekBar: SeekBar
    private lateinit var pitchSeekBar: SeekBar
    private var audioImportStatusView: TextView? = null
    private var asrStatusView: TextView? = null
    private var asrResultView: TextView? = null
    private var pendingTextExport: String = ""
    private var pendingSubtitleSrt: String = ""
    private var pendingSubtitleVtt: String = ""
    private lateinit var cloneTextInput: EditText
    private lateinit var cloneReferenceTextInput: EditText
    private var cloneReferenceUri: Uri? = null
    private var cloneStatusView: TextView? = null
    private var pendingCloneExport: File? = null
    private lateinit var storyScriptInput: EditText
    private lateinit var storyRoleANameInput: EditText
    private lateinit var storyRoleATextInput: EditText
    private lateinit var storyRoleBNameInput: EditText
    private lateinit var storyRoleBTextInput: EditText
    private var storyRoleAUri: Uri? = null
    private var storyRoleBUri: Uri? = null
    private var storyStatusView: TextView? = null
    private var pendingStoryExport: File? = null
    private lateinit var musicPromptInput: EditText
    private lateinit var musicDurationInput: EditText
    private var musicStatusView: TextView? = null
    private var pendingMusicExport: File? = null
    private var stemStatusView: TextView? = null
    private var stemResultsContainer: LinearLayout? = null
    private var midiStatusView: TextView? = null
    private lateinit var midiInstrumentInput: EditText
    private var pendingMidiExport: File? = null
    private var pendingGenericExport: File? = null
    private var stemAudioUri: Uri? = null
    private var midiAudioUri: Uri? = null

    private data class Palette(val name: String, val bg: Int, val surface: Int, val text: Int, val secondary: Int, val accent: Int, val border: Int, val dark: Boolean)
    private val accentNames = listOf("纸橙", "珊瑚", "海湾", "暖沙", "苔绿", "浅青")
    private val accentColors = intArrayOf(0xFFE0651C.toInt(), 0xFFF08A4B.toInt(), 0xFF139AA2.toInt(), 0xFFC2662D.toInt(), 0xFF5E8F45.toInt(), 0xFF78E8EE.toInt())
    private val palettes = listOf(
        Palette("纸白", 0xFFF7F7F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF18181A.toInt(), 0xFF77777D.toInt(), 0xFF18181A.toInt(), 0x1F18181A, false),
        Palette("墨黑", 0xFF0F0F11.toInt(), 0xFF18181B.toInt(), 0xFFEDEDEF.toInt(), 0xFF99999F.toInt(), 0xFFEDEDEF.toInt(), 0x26FFFFFF, true),
        Palette("海湾", 0xFFF1F6F6.toInt(), 0xFFFFFFFF.toInt(), 0xFF0D2427.toInt(), 0xFF61777A.toInt(), 0xFF0B7A80.toInt(), 0x2606272A, false),
        Palette("暖沙", 0xFFF3EEE4.toInt(), 0xFFFBF8F1.toInt(), 0xFF2B241D.toInt(), 0xFF827568.toInt(), 0xFF9A4A24.toInt(), 0x263C2814, false),
        Palette("苔绿", 0xFFEFF3ED.toInt(), 0xFFFFFFFF.toInt(), 0xFF1C2620.toInt(), 0xFF69776D.toInt(), 0xFF3B6B4C.toInt(), 0x261E3223, false),
        Palette("深海", 0xFF04181A.toInt(), 0xFF082427.toInt(), 0xFFE2F2F2.toInt(), 0xFF9BBFC0.toInt(), 0xFF19C3CD.toInt(), 0x26A6F2F5, true)
    )
    private val p: Palette get() {
        val base = palettes[themeIndex]
        val dark = when (appearanceMode) {
            "light" -> false
            "dark" -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val selectedAccent = accentColors[accentIndex.coerceIn(accentColors.indices)]
        val accent = when {
            dark && Color.luminance(selectedAccent) < 0.35f -> mixColor(selectedAccent, Color.WHITE, 0.72f)
            !dark && Color.luminance(selectedAccent) > 0.78f -> mixColor(selectedAccent, Color.BLACK, 0.72f)
            else -> selectedAccent
        }
        if (dark == base.dark) return base.copy(accent = accent)
        return if (dark) Palette(base.name, mixColor(base.bg, Color.BLACK, 0.86f),
            mixColor(base.surface, Color.BLACK, 0.82f), 0xFFF1F3F4.toInt(), 0xFFADB5B8.toInt(),
            accent, 0x33FFFFFF, true)
        else Palette(base.name, mixColor(base.bg, Color.WHITE, 0.88f), 0xFFFFFFFF.toInt(),
            0xFF202124.toInt(), 0xFF6F7478.toInt(), accent, 0x22181818, false)
    }
    private fun mixColor(from: Int, to: Int, amount: Float): Int = Color.rgb(
        (Color.red(from) * (1f - amount) + Color.red(to) * amount).toInt(),
        (Color.green(from) * (1f - amount) + Color.green(to) * amount).toInt(),
        (Color.blue(from) * (1f - amount) + Color.blue(to) * amount).toInt()
    )
    private fun applyPlatformTheme() {
        val accentStyle = when (accentIndex.coerceIn(0, 5)) {
            0 -> R.style.YovoiceAccentPaper
            1 -> R.style.YovoiceAccentCoral
            2 -> R.style.YovoiceAccentLagoon
            3 -> R.style.YovoiceAccentSand
            4 -> R.style.YovoiceAccentSage
            else -> R.style.YovoiceAccentAbyss
        }
        theme.applyStyle(accentStyle, true)
    }

    private fun applySystemBars() {
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bg
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !p.dark
        controller.isAppearanceLightNavigationBars = !p.dark
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val uiPrefs = getSharedPreferences("yovoice-ui", MODE_PRIVATE)
        themeIndex = uiPrefs.getInt("theme", 0).coerceIn(0, palettes.lastIndex)
        accentIndex = uiPrefs.getInt("accent", themeIndex).coerceIn(0, accentColors.lastIndex)
        appearanceMode = uiPrefs.getString("appearance", "system") ?: "system"
        ThemeMode.apply(this, appearanceMode)
        setTheme(R.style.YovoiceTheme)
        applyPlatformTheme()
        super.onCreate(savedInstanceState)
        val ttsPrefs = getSharedPreferences("yovoice-tts", MODE_PRIVATE)
        val savedEngineId = ttsPrefs.getString("selected_tts_engine_id", "system") ?: "system"
        speechRate = ttsPrefs.getFloat("speech_rate:" + savedEngineId,
            ttsPrefs.getFloat("speech_rate", 1.0f)).coerceIn(0.1f, 3.0f)
        speechPitch = ttsPrefs.getFloat("speech_pitch:" + savedEngineId,
            ttsPrefs.getFloat("speech_pitch", 1.0f)).coerceIn(0.1f, 3.0f)
        applyModelLaunchIntent(intent)
        buildShell()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
        initializeSystemTts()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyModelLaunchIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!pendingMultiTtsReturn) return
        pendingMultiTtsReturn = false
        val provider = TtsProviderRegistry.discover(this).firstOrNull {
            it.packageName.equals("org.nobody.multitts", ignoreCase = true) ||
                it.label.contains("MultiTTS", ignoreCase = true)
        }
        if (provider != null) {
            activateTtsProvider(provider)
            if (::status.isInitialized) status.text = "已连接 MultiTTS。正在读取其实际可用音色…"
        } else if (::status.isInitialized) {
            status.text = "尚未发现 MultiTTS 系统语音服务。请确认语音包已导入，并在手机“文字转语音输出”中启用 MultiTTS。"
        }
    }


    private fun applyModelLaunchIntent(source: Intent?) {
        val modelId = source?.getStringExtra("selected_model_id") ?: return
        val model = runCatching { ModelCatalog.load(this).firstOrNull { it.id == modelId } }.getOrNull() ?: return
        val mapped = ModelFeatureGuide.features.filter { model.family in it.modelFamilies }
        val requested = source.getStringExtra("open_feature")
        val target = mapped.firstOrNull { it.id == requested } ?: mapped.firstOrNull() ?: return
        val prefs = getSharedPreferences("yovoice-model-selection", MODE_PRIVATE).edit()
        mapped.forEach { prefs.putString(it.id, model.id) }
        prefs.apply()
        selectedCreateFeature = target.id
        if (target.id == "tts") {
            activeWorkspaceFeature = null
            page = 0
            if (model.family == "audio8_tts") activateNativeTtsEngine()
        } else {
            activeWorkspaceFeature = target.id
            page = 1
        }
    }

    private fun selectedFeatureModel(featureId: String, fallbackId: String): ModelEntry? {
        val selectedId = getSharedPreferences("yovoice-model-selection", MODE_PRIVATE)
            .getString(featureId, null) ?: return null
        val feature = ModelFeatureGuide.features.firstOrNull { it.id == featureId }
        return ModelCatalog.load(this).firstOrNull {
            it.id == selectedId && (feature == null || it.family in feature.modelFamilies)
        }
    }

    private fun buildShell() {
        applySystemBars()
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
        applySystemBars()
        body.animate().cancel()
        body.alpha = 0f
        body.removeAllViews()
        when (page) {
            0 -> renderStudio()
            1 -> renderCreate()
            2 -> renderModels()
            3 -> renderSettings()
            4 -> renderProjects()
            else -> renderSettings()
        }
        renderNav()
        body.animate().alpha(1f).setDuration(180L).start()
    }

    private fun renderHeader(title: String, subtitle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = column()
        brand.addView(label(title, 27, p.text, true))
        row.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val help = FrameLayout(this).apply {
            contentDescription = "查看标题说明"
            isClickable = true
            isFocusable = true
            background = ripple(p.surface, dp(12))
            addView(LineIconView(this@MainActivity, "info", p.secondary),
                FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setMessage(subtitle)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }
        row.addView(help, LinearLayout.LayoutParams(dp(48), dp(48)))
        val settings = LineIconView(this, "settings", p.secondary).apply {
            contentDescription = "设置"
            isClickable = true
            isFocusable = true
            setOnClickListener { page = 3; render() }
        }
        row.addView(settings, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(row)
    }

    private fun renderStudio() {
        renderHeader("文字转语音", "输入文本、选择语音并生成音频")
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
        attachDraft(input, "tts")
        panel.addView(input, marginTop(14))
        enginePicker = TextView(this).apply {
            text = "手机系统语音引擎   ›"
            textSize = 14f; setTextColor(p.text); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0); background = shape(p.bg, dp(13), p.border, dp(1))
            isClickable = true
            isFocusable = true
            contentDescription = "选择语音引擎"
            setOnClickListener { showTtsOptions() }
        }
        panel.addView(enginePicker, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })

        panel.addView(label("音色", 13, p.secondary, true), marginTop(12))
        voicePicker = TextView(this).apply {
            text = "搜索并选择音色"
            textSize = 14f
            setTextColor(p.text)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), 0, dp(13), 0)
            background = shape(p.bg, dp(13), p.border, dp(1))
            isClickable = true
            isFocusable = true
            contentDescription = "搜索并选择中文音色"
            setOnClickListener { showVoiceSearchDialog() }
        }
        panel.addView(voicePicker, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })
        panel.addView(secondaryButton("导入 MultiTTS 音色包（ZIP）") { chooseMultiTtsPackage() }, marginTop(7))
        speedLabel = label("语速 1.0×", 13, p.secondary)
        panel.addView(speedLabel, marginTop(10))
        speedSeekBar = SeekBar(this).apply {
            max = 30
            progress = (speechRate * 10).toInt().coerceIn(1, 30)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    speechRate = (value.coerceAtLeast(1) / 10f).coerceIn(0.1f, 3f)
                    getSharedPreferences("yovoice-tts", MODE_PRIVATE).edit()
                        .putFloat("speech_rate", speechRate)
                        .putFloat("speech_rate:" + activeTtsEngineId, speechRate).apply()
                    if (!activeTtsEngineId.startsWith("native:")) {
                        val result = runCatching { tts?.setSpeechRate(speechRate) ?: TextToSpeech.ERROR }.getOrDefault(TextToSpeech.ERROR)
                        if (fromUser && result == TextToSpeech.ERROR && ::status.isInitialized) {
                            status.text = activeTtsEngineLabel + " 不支持调整语速。"
                        }
                    }
                    speedLabel.text = "语速 %.1f×".format(speechRate)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(speedSeekBar, marginTop(2))
        pitchLabel = label("音调 1.0×", 13, p.secondary)
        panel.addView(pitchLabel, marginTop(8))
        pitchSeekBar = SeekBar(this).apply {
            max = 30
            progress = (speechPitch * 10).toInt().coerceIn(1, 30)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    speechPitch = (value.coerceAtLeast(1) / 10f).coerceIn(0.1f, 3f)
                    getSharedPreferences("yovoice-tts", MODE_PRIVATE).edit()
                        .putFloat("speech_pitch", speechPitch)
                        .putFloat("speech_pitch:" + activeTtsEngineId, speechPitch).apply()
                    if (!activeTtsEngineId.startsWith("native:")) {
                        val result = runCatching { tts?.setPitch(speechPitch) ?: TextToSpeech.ERROR }.getOrDefault(TextToSpeech.ERROR)
                        if (fromUser && result == TextToSpeech.ERROR && ::status.isInitialized) {
                            status.text = activeTtsEngineLabel + " 不支持调整音调。"
                        }
                    }
                    pitchLabel.text = "音调 %.1f×".format(speechPitch)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(pitchSeekBar, marginTop(2))

        panel.addView(primaryButton("合成并试听（当前引擎）") { speak() }, marginTop(10))
        panel.addView(secondaryButton("释放本地模型内存") { unloadLocalModel() }, marginTop(8))
        body.addView(panel, marginTop(18))
        addFeatureModelSelector("tts", body)
        val subtitleToggle = CheckBox(this).apply {
            text = "显示字幕预览"
            textSize = 14f
            setTextColor(p.text)
            isChecked = ttsSubtitleEnabled
            setOnCheckedChangeListener { _, checked ->
                ttsSubtitleEnabled = checked
                ttsSubtitlePreview?.visibility = if (checked) View.VISIBLE else View.GONE
            }
        }
        body.addView(label("字幕", 16, p.text, true), marginTop(14))
        body.addView(subtitleToggle, marginTop(6))
        ttsSubtitlePreview = label(input.text?.toString()?.trim().orEmpty().ifBlank { "输入文字后，这里显示字幕预览。" }, 13, p.text).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = shape(p.surface, dp(10), p.border, dp(1))
            visibility = if (ttsSubtitleEnabled) View.VISIBLE else View.GONE
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ttsSubtitlePreview?.text = s?.toString()?.trim().orEmpty().ifBlank { "输入文字后，这里显示字幕预览。" }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        body.addView(ttsSubtitlePreview, marginTop(6))
        body.addView(secondaryButton("导出本地语音字幕（SRT）") { exportTtsSubtitle() }, marginTop(6))
        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shortcuts.addView(secondaryButton("导入音频") { chooseAudio() }, LinearLayout.LayoutParams(-1, dp(56)))
        body.addView(shortcuts, marginTop(12))
        body.addView(label("运行状态", 17, p.text, true), marginTop(24))
        status = label("正在初始化系统 TTS…", 13, p.secondary)
        body.addView(status, marginTop(8))
        body.addView(label("最近项目", 17, p.text, true), marginTop(24))
        body.addView(infoRow("中文旁白试听", "手机系统语音引擎"), marginTop(8))
        body.addView(infoRow("本地音频导入", "支持通过文件选择器载入音频"), marginTop(8))

        // The TTS provider may finish initialization while the creation home is visible.
        // Rebind the live engine state whenever the TTS workspace is opened.
        if (activeTtsEngineId.isNotBlank()) enginePicker.text = activeTtsEngineLabel + "   ›"
        when {
            activeTtsEngineId.startsWith("native:") -> status.text = "已选择本地语音引擎；请确认所选模型已下载并校验。"
            ttsReady -> {
                status.text = activeTtsEngineLabel + " 已就绪，可以试听。"
                refreshSystemVoices()
            }
            else -> status.text = "正在初始化或等待选择系统语音服务…"
        }
    }

    private fun renderCreate() {
        if (activeWorkspaceFeature == null) renderCreateGrid() else renderCreateWorkspace()
    }

    private fun renderCreateGrid() {
        renderHeader("创作", "选择工具开始制作")
        val columns = if (resources.configuration.screenWidthDp < 360) 2 else 3
        val grid = GridLayout(this).apply { columnCount = columns; useDefaultMargins = false }
        ModelFeatureGuide.features.forEachIndexed { index, feature ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(7), dp(4), dp(6))
                background = shape(p.surface, dp(15), p.border, dp(1))
                isClickable = true
                isFocusable = true
                contentDescription = "${feature.name}，${feature.shortDescription}"
                background = ripple(p.surface, dp(15), p.border)
                setOnClickListener {
                    animatePress(this) {
                        selectedCreateFeature = feature.id
                        if (feature.id == "tts") {
                            page = 0
                            activeWorkspaceFeature = null
                        } else {
                            page = 1
                            activeWorkspaceFeature = feature.id
                        }
                        render()
                    }
                }
                val iconWrap = FrameLayout(this@MainActivity).apply {
                    background = shape(Color.argb(if (p.dark) 38 else 22, Color.red(p.accent), Color.green(p.accent), Color.blue(p.accent)), dp(17))
                    addView(LineIconView(this@MainActivity, featureIcon(feature.id), p.accent), FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER))
                }
                addView(iconWrap, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(label(feature.name, 11, p.text, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(0, dp(6), 0, 0)
                })
            }
            grid.addView(tile, GridLayout.LayoutParams(
                GridLayout.spec(index / columns, 1f), GridLayout.spec(index % columns, 1f)
            ).apply {
                width = 0
                height = dp(94)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        body.addView(grid, marginTop(12))
        body.addView(iconListRow("models", "模型库", "查看模型用途、适配功能与下载状态") {
            page = 2
            render()
        }, marginTop(12))
    }

    private fun featureIcon(id: String): String = when (id) {
        "tts" -> "tts"
        "clone" -> "clone"
        "story" -> "story"
        "asr" -> "asr"
        "music" -> "music"
        "stems" -> "stems"
        "midi" -> "midi"
        "assistant" -> "assistant"
        "audio-import" -> "import"
        else -> "wave"
    }

    private fun renderCreateFeature(featureId: String) {
        val feature = ModelFeatureGuide.features.firstOrNull { it.id == featureId }
        if (feature == null) { selectedCreateFeature = null; renderCreateGrid(); return }
        renderHeader(feature.name, feature.description)
        body.addView(iconListRow("back", "返回创作", "切换其他创作工具") {
            selectedCreateFeature = null
            render()
        }, marginTop(8))
        body.addView(section("功能说明", feature.purpose), marginTop(18))
        val models = ModelCatalog.load(this).filter { it.family in feature.modelFamilies }.sortedBy { it.packageSize() }
        val savedModelId = getSharedPreferences("yovoice-model-selection", MODE_PRIVATE).getString(featureId, null)
        // Never present the first catalog entry as the active model: catalog presence is not installation.
        val selectedModel = models.firstOrNull { it.id == savedModelId }
        body.addView(label(if (feature.modelFamilies.isEmpty()) "模型依赖" else "当前引擎 / 模型", 13, p.secondary, true), marginTop(18))
        val modelStateLabel = label(
            when {
                feature.modelFamilies.isEmpty() -> "此功能不需要单独下载本地模型"
                selectedModel == null -> "尚未选择模型 · 点击查看全部适配模型"
                else -> "正在检查本地安装与校验状态…"
            },
            11, p.secondary
        ).apply { setPadding(0, dp(3), 0, 0) }
        val modelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(9), dp(12), dp(9))
            background = ripple(p.surface, dp(13), p.border)
            isClickable = feature.modelFamilies.isNotEmpty()
            isFocusable = feature.modelFamilies.isNotEmpty()
            contentDescription = if (feature.modelFamilies.isEmpty()) "此功能不需要本地模型" else "管理当前功能模型"
            setOnClickListener {
                if (feature.modelFamilies.isEmpty()) return@setOnClickListener
                val family = selectedModel?.family
                try {
                    startActivity(Intent(this@MainActivity, ModelCenterActivity::class.java)
                        .putExtra("focus_feature", featureId)
                        .putExtra("focus_family", family))
                } catch (error: Throwable) {
                    Toast.makeText(this@MainActivity, "模型库打开失败：${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        val modelText = column()
        modelText.addView(label(
            selectedModel?.name ?: if (feature.modelFamilies.isEmpty()) "无需下载模型" else "尚未选择模型",
            13, p.text, true
        ))
        if (feature.modelFamilies.isNotEmpty()) {
            modelText.addView(label("适配此功能的模型：${models.size} 个 · 按体积从小到大", 11, p.secondary), marginTop(2))
            selectedModel?.let { modelText.addView(label("用途：" + ModelFeatureGuide.modelPurpose(it.family), 11, p.secondary).apply { maxLines = 2 }, marginTop(2)) }
        } else {
            modelText.addView(label("此功能使用在线服务或本地文件，不需要模型下载", 11, p.secondary), marginTop(2))
        }
        modelText.addView(modelStateLabel, marginTop(2))
        modelRow.addView(modelText, LinearLayout.LayoutParams(0, -2, 1f))
        if (feature.modelFamilies.isNotEmpty()) {
            modelRow.addView(LineIconView(this, "chevron", p.secondary), LinearLayout.LayoutParams(dp(20), dp(20)))
        }
        body.addView(modelRow, marginTop(7))
        selectedModel?.let { model ->
            // Hash verification can be expensive for multi-GB models; keep it off the UI thread.
            storageExecutor.execute {
                val verified = runCatching { ModelCatalog.isInstalled(this, model) }.getOrDefault(false)
                runOnUiThread {
                    if (!destroyed && !isFinishing && modelStateLabel.isAttachedToWindow) {
                        modelStateLabel.text = if (verified) {
                            "${model.family} · ${model.formattedSize()} · 文件已安装并通过 SHA-256 校验；运行时仍需任务检测"
                        } else {
                            "${model.family} · ${model.formattedSize()} · 未完整安装或校验失败，请在模型库下载/修复"
                        }
                    }
                }
            }
        }
        body.addView(primaryButton(feature.actionLabel) {
            when (featureId) {
                "tts" -> { page = 0; selectedCreateFeature = null; render() }
                "audio-import" -> chooseAudio()
                else -> { activeWorkspaceFeature = featureId; renderCreateWorkspace() }
            }
        }, marginTop(18))
        body.addView(section("运行适配说明", feature.runtimeNote), marginTop(8))
    }

    private fun renderCreateWorkspace() {
        body.removeAllViews()
        val workspaceFeature = activeWorkspaceFeature
        renderHeader(ModelFeatureGuide.features.firstOrNull { it.id == workspaceFeature }?.name ?: "创作工具",
            ModelFeatureGuide.features.firstOrNull { it.id == workspaceFeature }?.shortDescription ?: "开始创作")
        if (workspaceFeature == "audio-import") {
            val importPanel = card()
            importPanel.addView(section("音频导入与试听", "从手机文件中选择音频，导入后直接试听"))
            audioImportStatusView = label("尚未选择音频文件。", 12, p.secondary)
            importPanel.addView(audioImportStatusView, marginTop(12))
            importPanel.addView(primaryButton("选择音频文件") { chooseAudio() }, marginTop(10))
            body.addView(importPanel, marginTop(10))
        }
        if (workspaceFeature == null || workspaceFeature == "assistant") {
        val assistantPanel = card()
        assistantPanel.addView(section("AI 创作助手", "使用你配置的兼容服务，辅助润色、扩写、分镜和提示词"))
        val assistantInput = EditText(this).apply {
            hint = "输入创作内容或需求，例如：将这段文案改写得更有画面感…"
            minLines = 3; gravity = Gravity.TOP; textSize = 14f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        attachDraft(assistantInput, "assistant.prompt")
        assistantPanel.addView(assistantInput, marginTop(12))
        val assistantOutput = TextView(this).apply {
            text = "AI 回复会显示在这里。请先在设置中配置服务地址、模型和 API Key。"
            textSize = 14f; setTextColor(p.text); setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = shape(p.bg, dp(12), p.border, dp(1))
        }
        assistantPanel.addView(assistantOutput, marginTop(8))
        assistantPanel.addView(primaryButton("开始 AI 辅助创作") {
            val prompt = assistantInput.text?.toString()?.trim().orEmpty()
            if (prompt.isBlank()) Toast.makeText(this, "请先输入创作需求", Toast.LENGTH_SHORT).show()
            else requestAssistant(prompt, assistantOutput)
        }, marginTop(10))
        body.addView(assistantPanel, marginTop(10))
        }
        if (workspaceFeature == null || workspaceFeature == "clone") {
        val clonePanel = card()
        clonePanel.addView(section("声音克隆", "Audio8 零样本克隆 · 需要参考音频及其准确转录文本"))
        cloneTextInput = EditText(this).apply {
            hint = "输入要用克隆声音朗读的内容"
            minLines = 3
            gravity = Gravity.TOP
            textSize = 15f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        attachDraft(cloneTextInput, "clone.text")
        clonePanel.addView(cloneTextInput, marginTop(12))
        cloneReferenceTextInput = EditText(this).apply {
            hint = "填写参考音频中实际说出的文字（尽量逐字准确）"
            minLines = 2
            gravity = Gravity.TOP
            textSize = 14f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        attachDraft(cloneReferenceTextInput, "clone.referenceText")
        clonePanel.addView(cloneReferenceTextInput, marginTop(8))
        clonePanel.addView(secondaryButton("选择参考音频") { chooseCloneReference() }, marginTop(8))
        cloneStatusView = label("尚未选择参考音频。建议使用清晰、无背景音乐的短录音。", 12, p.secondary)
        clonePanel.addView(cloneStatusView, marginTop(8))
        clonePanel.addView(primaryButton("生成克隆语音") { cloneVoice() }, marginTop(10))
        clonePanel.addView(secondaryButton("导出克隆语音 WAV") { exportCloneAudio() }, marginTop(8))
        body.addView(clonePanel, marginTop(10))
        }

        if (workspaceFeature == null || workspaceFeature == "story") {
        val storyPanel = card()
        storyPanel.addView(section("故事配音", "多角色分句克隆 · 每行使用“角色名：台词”格式"))
        storyScriptInput = EditText(this).apply {
            hint = "角色A：故事从这里开始……\n角色B：我来回答你。\n角色A：我们继续前进。"
            minLines = 5
            gravity = Gravity.TOP
            textSize = 14f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        attachDraft(storyScriptInput, "story.script")
        storyPanel.addView(storyScriptInput, marginTop(12))
        storyRoleANameInput = EditText(this).apply {
            hint = "角色 A 名称"
            setText("角色A")
            textSize = 14f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        storyPanel.addView(storyRoleANameInput, marginTop(10))
        storyRoleATextInput = EditText(this).apply {
            hint = "角色 A 参考录音的准确文字"
            minLines = 2
            gravity = Gravity.TOP
            textSize = 13f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        storyPanel.addView(storyRoleATextInput, marginTop(6))
        storyPanel.addView(secondaryButton("选择角色 A 参考音频") { chooseStoryReference(1) }, marginTop(6))
        storyRoleBNameInput = EditText(this).apply {
            hint = "角色 B 名称"
            setText("角色B")
            textSize = 14f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        storyPanel.addView(storyRoleBNameInput, marginTop(12))
        storyRoleBTextInput = EditText(this).apply {
            hint = "角色 B 参考录音的准确文字"
            minLines = 2
            gravity = Gravity.TOP
            textSize = 13f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        storyPanel.addView(storyRoleBTextInput, marginTop(6))
        storyPanel.addView(secondaryButton("选择角色 B 参考音频") { chooseStoryReference(2) }, marginTop(6))
        storyStatusView = label("为每个角色选择清晰参考录音并填写准确转录。", 12, p.secondary)
        storyPanel.addView(storyStatusView, marginTop(8))
        storyPanel.addView(primaryButton("生成多角色故事配音") { generateStoryDubbing() }, marginTop(10))
        storyPanel.addView(secondaryButton("导出故事配音 WAV") { exportStoryDubbing() }, marginTop(8))
        body.addView(storyPanel, marginTop(10))
        }

        if (workspaceFeature == null || workspaceFeature == "asr") {
        body.addView(toolRow("音频 / 视频转文字", "使用本地 Qwen3 ASR 识别音轨，支持常见音频与视频容器") { chooseAsrMedia() }, marginTop(10))
        asrStatusView = label("尚未选择待识别的音频或视频文件。", 12, p.secondary)
        body.addView(asrStatusView, marginTop(8))
        asrResultView = label("识别结果将在此显示。", 14, p.text).apply {
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = shape(p.surface, dp(12), p.border, dp(1))
        }
        body.addView(asrResultView, marginTop(8))
        body.addView(secondaryButton("导出识别文本 TXT") { exportTranscript() }, marginTop(8))
        body.addView(secondaryButton("导出 SRT 字幕") { exportSubtitle(false) }, marginTop(6))
        body.addView(secondaryButton("导出 WebVTT 字幕") { exportSubtitle(true) }, marginTop(6))
        }
        if (workspaceFeature == null || workspaceFeature == "music") {
        val musicPanel = card()
        musicPanel.addView(section("AI 音乐生成", "Stable Audio 3 Small · 根据文字描述生成音乐"))
        musicPromptInput = EditText(this).apply {
            hint = "例如：温暖的钢琴与弦乐，电影感、缓慢渐强、纯音乐"
            minLines = 3
            gravity = Gravity.TOP
            textSize = 15f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        attachDraft(musicPromptInput, "music.prompt")
        musicPanel.addView(musicPromptInput, marginTop(12))
        musicDurationInput = EditText(this).apply {
            hint = "生成时长（秒）"
            setText("20")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 14f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        musicPanel.addView(musicDurationInput, marginTop(8))
        musicStatusView = label("模型约 1.68 GB，首次使用需先下载。", 12, p.secondary)
        musicPanel.addView(musicStatusView, marginTop(8))
        musicPanel.addView(primaryButton("生成音乐") { generateMusic() }, marginTop(10))
        musicPanel.addView(secondaryButton("导出生成音乐 WAV") { exportMusic() }, marginTop(8))
        body.addView(musicPanel, marginTop(10))
        }

        if (workspaceFeature == null || workspaceFeature == "stems") {
        val stemPanel = card()
        stemPanel.addView(section("六轨音轨分离", "HTDemucs · 人声、鼓、贝斯、其他、吉他、钢琴"))
        stemPanel.addView(secondaryButton("选择音乐文件") { chooseStemAudio() }, marginTop(10))
        stemStatusView = label("选择 44.1 kHz 音乐文件后开始分离。", 12, p.secondary)
        stemPanel.addView(stemStatusView, marginTop(8))
        stemResultsContainer = column()
        stemPanel.addView(stemResultsContainer, marginTop(8))
        stemPanel.addView(primaryButton("开始六轨分离") { separateAudio() }, marginTop(10))
        body.addView(stemPanel, marginTop(10))
        }

        if (workspaceFeature == null || workspaceFeature == "midi") {
        val midiPanel = card()
        midiPanel.addView(section("音频转 MIDI", "MuScriptor Small · 从音乐中提取音符与乐器事件"))
        midiPanel.addView(secondaryButton("选择待转换音乐") { chooseMidiAudio() }, marginTop(10))
        midiInstrumentInput = EditText(this).apply {
            hint = "可选乐器限制，例如 drums,electric_bass"
            textSize = 14f
            setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        midiPanel.addView(midiInstrumentInput, marginTop(8))
        midiStatusView = label("模型约 412 MB，支持导出标准 MIDI 文件。", 12, p.secondary)
        midiPanel.addView(midiStatusView, marginTop(8))
        midiPanel.addView(primaryButton("开始转换 MIDI") { convertToMidi() }, marginTop(10))
        midiPanel.addView(secondaryButton("导出 MIDI 文件") { exportMidi() }, marginTop(8))
        body.addView(midiPanel, marginTop(10))
        }
        if (!workspaceFeature.isNullOrBlank() && workspaceFeature != "assistant" && workspaceFeature != "audio-import") {
            addFeatureModelSelector(workspaceFeature, body)
        }
        body.addView(label("未完成 Android 引擎适配的功能会明确标注，不会伪装成可用。", 12, p.secondary), marginTop(18))
    }


    private fun addFeatureModelSelector(featureId: String, target: LinearLayout) {
        val feature = ModelFeatureGuide.features.firstOrNull { it.id == featureId } ?: return
        if (feature.modelFamilies.isEmpty()) return
        val selectedId = getSharedPreferences("yovoice-model-selection", MODE_PRIVATE).getString(featureId, null)
        val selected = ModelCatalog.load(this).firstOrNull { it.id == selectedId && it.family in feature.modelFamilies }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(8), dp(12), dp(8))
            background = ripple(p.surface, dp(13), p.border)
            isClickable = true
            isFocusable = true
            minimumHeight = dp(56)
            contentDescription = "选择或下载" + feature.name + "模型"
        }
        val copy = column()
        copy.addView(label("本地模型", 11, p.secondary, true))
        copy.addView(label(selected?.name ?: "尚未选择 · 点击查看适配模型", 14, p.text, true), marginTop(3))
        val availableCount = ModelCatalog.load(this).count { it.family in feature.modelFamilies }
        copy.addView(label("适配模型：${availableCount} 个 · 轻量优先", 11, p.secondary), marginTop(2))
        selected?.let { copy.addView(label("用途：" + ModelFeatureGuide.modelPurpose(it.family), 11, p.secondary).apply { maxLines = 2 }, marginTop(2)) }
        val isInUse = selected != null && nativeLoadedModelId == selected.id && (featureId != "tts" || activeTtsEngineId.startsWith("native:"))
        val modelState = label(
            if (selected == null) "推荐适配模型 · 可在此下载"
            else if (isInUse) "● 正在使用"
            else selected.family + " · " + selected.formattedSize() + " · 点击更换或下载",
            11, if (isInUse) 0xFF238A59.toInt() else p.secondary
        )
        activeModelStatusView = modelState
        copy.addView(modelState, marginTop(2))
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(LineIconView(this, "chevron", p.secondary), LinearLayout.LayoutParams(dp(20), dp(20)))
        row.setOnClickListener { showModelPicker(featureId) }
        target.addView(row, marginTop(12))
        selected?.let { model ->
            storageExecutor.execute {
                val installed = runCatching { ModelCatalog.isInstalled(this, model) }.getOrDefault(false)
                runOnUiThread {
                    if (!destroyed && modelState.isAttachedToWindow) {
                        modelState.text = when {
                            nativeLoadedModelId == model.id && (featureId != "tts" || activeTtsEngineId.startsWith("native:")) -> "● 正在使用 · 已加载到本地引擎"
                            installed -> model.family + " · 已下载并通过 SHA-256 校验"
                            else -> model.family + " · 未完整安装或校验失败"
                        }
                    }
                }
            }
        }
    }

    private fun showModelPicker(featureId: String) {
        val feature = ModelFeatureGuide.features.firstOrNull { it.id == featureId } ?: return
        if (feature.modelFamilies.isEmpty()) return
        val probe = runCatching { JSONObject(NativeEngine.probe()) }.getOrNull()
        val runtimeReady = probe?.optString("status") == "ready"
        val familyArray = probe?.optJSONArray("families")
        val runtimeFamilies = if (familyArray == null) emptySet() else
            (0 until familyArray.length()).map { familyArray.optString(it) }.toSet()
        val allFeatureModels = ModelCatalog.load(this)
            .filter { it.family in feature.modelFamilies }
            .sortedBy { it.packageSize() }
        val compatibleModels = allFeatureModels.filter { it.family in runtimeFamilies }
        // Show every model mapped to this creation feature. Runtime support is a separate state, never a silent filter.
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        content.addView(label("本功能适配的模型按体积从小到大排列；每项显示用途和本机运行时状态。下载完成还会逐文件校验 SHA-256。", 12, p.secondary))
        if (!runtimeReady || compatibleModels.isEmpty()) {
            content.addView(label("当前设备运行时尚未报告此功能的可用模型家族。下列未适配模型仍可下载保存，但不能作为本机可运行模型。", 12, 0xFF9A6428.toInt()).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = shape(0x1AFFB74D, dp(10))
            }, marginTop(8))
        }
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(content, ViewGroup.LayoutParams(-1, -2)) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择" + feature.name + "模型")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .setPositiveButton("更多模型") { _, _ ->
                startActivity(Intent(this, ModelCenterActivity::class.java).putExtra("focus_feature", featureId))
            }
            .create()
        if (allFeatureModels.isEmpty()) content.addView(label("模型目录中暂时没有关联到此功能的模型。", 13, p.secondary), marginTop(12))
        val manager = WorkManager.getInstance(applicationContext)
        allFeatureModels.forEach { model ->
            val supported = runtimeReady && model.family in runtimeFamilies
            val selectedNow = getSharedPreferences("yovoice-model-selection", MODE_PRIVATE).getString(featureId, null) == model.id
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = shape(p.surface, dp(12), p.border, dp(1))
            }
            val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val titleColumn = column()
            titleColumn.addView(label(model.name, 14, p.text, true))
            titleColumn.addView(label(model.family + " · " + model.formattedSize() + " · " + model.precision, 11, p.secondary), marginTop(3))
            titleColumn.addView(label("用途：" + ModelFeatureGuide.modelPurpose(model.family), 11, p.secondary).apply { maxLines = 3 }, marginTop(4))
            titleColumn.addView(label("适配功能：" + ModelFeatureGuide.featureNames(model.family), 11, p.secondary).apply { maxLines = 2 }, marginTop(2))
            titleRow.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
            if (selectedNow) titleRow.addView(label("已选择", 11, p.accent, true))
            item.addView(titleRow)
            val state = label(if (supported) "尚未校验本地文件" else "当前 Android 运行时未适配", 11,
                if (supported) p.secondary else 0xFF9A6428.toInt())
            item.addView(state, marginTop(6))
            val action = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = shape(p.bg, dp(9), p.border, dp(1))
                minHeight = dp(48)
            }
            item.addView(action, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); gravity = Gravity.END })
            content.addView(item, marginTop(7))
            val uniqueName = "model-download-" + model.id
            var installed = false
            var installedChecked = false
            var currentInfo: WorkInfo? = null
            var verifying = false
            fun updateAction(info: WorkInfo?) {
                currentInfo = info
                val stateNow = info?.state
                val downloading = stateNow == WorkInfo.State.RUNNING || stateNow == WorkInfo.State.ENQUEUED || stateNow == WorkInfo.State.BLOCKED
                if (!model.isDownloadable()) {
                    state.text = "缺少可信文件校验信息，暂不能安全下载"
                    action.text = "不可下载"
                    action.isEnabled = false
                } else if (downloading) {
                    val done = info?.progress?.getLong("downloaded", 0L) ?: 0L
                    val total = info?.progress?.getLong("total", model.packageSize()) ?: model.packageSize()
                    val percent = if (total > 0L) (done * 100L / total).coerceIn(0L, 100L) else 0L
                    state.text = "下载中 " + percent + "% · " + ModelEntry.formatBytes(done) + " / " + ModelEntry.formatBytes(total)
                    action.text = "暂停"
                    action.isEnabled = true
                } else if (!installedChecked) {
                    state.text = "点击检查本地文件；不会在打开弹窗时扫描全部大模型"
                    action.text = "检查本地"
                    action.isEnabled = model.isDownloadable()
                } else if (installed) {
                    state.text = if (supported) "已下载并通过 SHA-256 校验 · 可供此功能选择" else "已下载并校验 · 运行时未适配"
                    action.text = if (supported) "设为当前模型" else "未适配"
                    action.isEnabled = supported
                } else {
                    state.text = when (info?.state) {
                        WorkInfo.State.FAILED -> "下载失败：" + (info?.outputData?.getString("error") ?: "可重试")
                        WorkInfo.State.CANCELLED -> "下载已暂停，可继续"
                        else -> if (supported) "尚未下载 · 可直接在此下载" else "运行时未适配 · 可下载保存，但不能在此设备推理"
                    }
                    action.text = when (info?.state) {
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> "重试下载"
                        else -> "下载"
                    }
                    action.isEnabled = model.isDownloadable()
                }
            }
            action.setOnClickListener {
                val info = currentInfo
                if (info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED) {
                    manager.cancelUniqueWork(uniqueName)
                } else if (!installedChecked) {
                    if (verifying) return@setOnClickListener
                    verifying = true
                    action.text = "校验中…"
                    action.isEnabled = false
                    storageExecutor.execute {
                        val verified = runCatching { ModelCatalog.isInstalled(this, model) }.getOrDefault(false)
                        runOnUiThread {
                            verifying = false
                            installedChecked = true
                            installed = verified
                            if (!destroyed && !isFinishing) updateAction(currentInfo)
                        }
                    }
                    return@setOnClickListener
                } else if (installed) {
                    if (!supported) return@setOnClickListener
                    getSharedPreferences("yovoice-model-selection", MODE_PRIVATE).edit().putString(featureId, model.id).apply()
                    if (featureId == "tts" && model.family == "audio8_tts") activateNativeTtsEngine()
                    dialog.dismiss()
                    render()
                } else {
                    val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                        .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to model.id, ModelDownloadWorker.KEY_SOURCE to "huggingface"))
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build()
                    manager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
                }
            }
            manager.getWorkInfosForUniqueWorkLiveData(uniqueName).observe(this) { infos ->
                val info = infos.firstOrNull()
                updateAction(info)
                if (info?.state == WorkInfo.State.SUCCEEDED && !installedChecked && !verifying) {
                    verifying = true
                    storageExecutor.execute {
                        val verified = runCatching { ModelCatalog.isInstalled(this, model) }.getOrDefault(false)
                        runOnUiThread {
                            verifying = false
                            installed = verified
                            installedChecked = true
                            if (!destroyed && !isFinishing) updateAction(info)
                        }
                    }
                }
            }
        }
        dialog.show()
    }


    private fun markNativeModelInUse(model: ModelEntry) {
        nativeLoadedModelId = model.id
        runOnUiThread {
            activeModelStatusView?.takeIf { it.isAttachedToWindow }?.apply {
                text = "● 正在使用 · 已加载到本地引擎"
                setTextColor(0xFF238A59.toInt())
            }
        }
    }

    private fun markNativeModelIdle() {
        runOnUiThread {
            activeModelStatusView?.takeIf { it.isAttachedToWindow }?.apply {
                text = if (nativeLoadedModelId == null) "模型已释放 · 再次调用时重新加载" else "已加载 · 等待再次调用"
                setTextColor(p.secondary)
            }
        }
    }

    private fun activateNativeTtsEngine() {
        nativeLoadedModelId = null
        activeModelStatusView?.takeIf { it.isAttachedToWindow }?.apply { text = "已选择模型 · 等待本地引擎加载"; setTextColor(p.secondary) }
        ++ttsGeneration
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        activeTtsEngineId = "native:audio8"
        activeTtsEngineLabel = "Audio8 本地语音"
        selectedVoiceName = null
        val prefs = getSharedPreferences("yovoice-tts", MODE_PRIVATE)
        speechRate = prefs.getFloat("speech_rate:native:audio8", speechRate).coerceIn(0.1f, 3.0f)
        speechPitch = prefs.getFloat("speech_pitch:native:audio8", speechPitch).coerceIn(0.1f, 3.0f)
        prefs.edit().putString("selected_tts_engine_id", activeTtsEngineId).apply()
        if (::speedLabel.isInitialized) speedLabel.text = "语速 %.1f×".format(speechRate)
        if (::pitchLabel.isInitialized) pitchLabel.text = "音调 %.1f×".format(speechPitch)
        if (::speedSeekBar.isInitialized) speedSeekBar.progress = (speechRate * 10).toInt().coerceIn(1, 30)
        if (::pitchSeekBar.isInitialized) pitchSeekBar.progress = (speechPitch * 10).toInt().coerceIn(1, 30)
        if (::enginePicker.isInitialized) enginePicker.text = activeTtsEngineLabel + "   ›"
        if (::status.isInitialized) status.text = "已选择 Audio8 本地语音；请在页面底部选择并下载模型。"
    }

    private fun exportTtsSubtitle() {
        if (pendingTtsSubtitleSrt.isBlank()) {
            Toast.makeText(this, "请先使用本地语音模型生成音频，再导出带时长的字幕。", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-subrip"
            putExtra(Intent.EXTRA_TITLE, "yovoice-tts-subtitle.srt")
        }
        startActivityForResult(intent, REQUEST_EXPORT_TTS_SRT)
    }

    private fun buildWholeAudioSubtitle(text: String, wav: File): String {
        val header = ByteArray(44)
        if (!wav.isFile || wav.length() <= 44L) return ""
        val read = wav.inputStream().use { it.read(header) }
        if (read < 44 || String(header, 0, 4, Charsets.US_ASCII) != "RIFF") return ""
        val data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val channels = data.getShort(22).toInt() and 0xffff
        val sampleRate = data.getInt(24)
        val bits = data.getShort(34).toInt() and 0xffff
        val dataBytes = data.getInt(40).toLong() and 0xffffffffL
        val bytesPerSecond = sampleRate.toLong() * channels * bits / 8L
        if (bytesPerSecond <= 0L || dataBytes <= 0L) return ""
        val durationMs = (dataBytes * 1000L / bytesPerSecond).coerceAtLeast(1L)
        val end = formatSubtitleMillis(durationMs)
        return "1\n00:00:00,000 --> " + end + "\n" + text.trim() + "\n"
    }

    private fun formatSubtitleMillis(value: Long): String {
        val millis = value.coerceAtLeast(0L)
        val hours = millis / 3_600_000L
        val minutes = (millis / 60_000L) % 60L
        val seconds = (millis / 1000L) % 60L
        val fraction = millis % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, fraction)
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

    private fun renderProjects() {
        renderHeader("项目", "本地草稿、生成记录与作品导出")
        val records = ProjectStore.list(this)
        if (records.isEmpty()) {
            body.addView(label("这里会保存语音、故事配音、音乐、音轨、MIDI 和转录项目。", 13, p.secondary), marginTop(22))
            return
        }
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        records.forEach { record ->
            val item = card()
            item.addView(label(record.title, 16, p.text, true))
            item.addView(label(record.category + " · " + record.detail, 12, p.secondary).apply {
                setPadding(0, dp(5), 0, 0)
            })
            item.addView(label(formatter.format(java.util.Date(record.createdAt)), 11, p.secondary), marginTop(5))
            val file = File(record.filePath)
            if (file.isFile) {
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                if (record.mimeType.startsWith("audio/") && record.mimeType != "audio/midi") {
                    actions.addView(textButton("试听", 12) { playAudio(Uri.fromFile(file)) }, LinearLayout.LayoutParams(0, dp(42), 1f))
                }
                actions.addView(textButton("导出", 12) {
                    launchFileExport(file, record.mimeType, file.name, REQUEST_EXPORT_GENERIC)
                }, LinearLayout.LayoutParams(0, dp(42), 1f))
                actions.addView(textButton("删除", 12) {
                    ProjectStore.delete(this, record.id)
                    render()
                }, LinearLayout.LayoutParams(0, dp(42), 1f))
                item.addView(actions, marginTop(8))
            } else {
                item.addView(label("文件已不存在，可删除此历史记录。", 12, p.secondary), marginTop(8))
                item.addView(textButton("删除记录", 12) {
                    ProjectStore.delete(this, record.id)
                    render()
                }, marginTop(6))
            }
            body.addView(item, marginTop(10))
        }
    }

    private fun renderSettings() {
        renderHeader("我的", "外观、语音与设备设置")

        val appearanceCard = card()
        appearanceCard.addView(section("外观模式", "跟随手机系统，或固定使用亮色 / 暗色；此设置只影响 Yovoice，不改变手机系统主题。"))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("system" to "跟随系统", "light" to "亮色", "dark" to "暗色").forEach { (mode, title) ->
            val chip = TextView(this).apply {
                text = (if (appearanceMode == mode) "✓  " else "") + title
                gravity = Gravity.CENTER
                textSize = 13f
                minHeight = dp(48)
                setTextColor(if (appearanceMode == mode) p.accent else p.text)
                background = ripple(
                    if (appearanceMode == mode) Color.argb(28, Color.red(p.accent), Color.green(p.accent), Color.blue(p.accent)) else p.surface,
                    dp(12), if (appearanceMode == mode) p.accent else p.border
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (appearanceMode == mode) return@setOnClickListener
                    appearanceMode = mode
                    getSharedPreferences("yovoice-ui", MODE_PRIVATE).edit().putString("appearance", mode).apply()
                    ThemeMode.apply(this@MainActivity, mode)
                }
            }
            modeRow.addView(chip, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                if (mode != "system") leftMargin = dp(7)
            })
        }
        appearanceCard.addView(modeRow, marginTop(8))
        body.addView(appearanceCard, marginTop(12))

        val themeCard = card()
        themeCard.addView(section("界面风格", "六套风格沿用桌面端主题的背景、表面和文字层级；明暗模式独立生效。"))
        val styleGrid = GridLayout(this).apply { columnCount = 2; useDefaultMargins = false }
        palettes.forEachIndexed { index, palette ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(7))
                background = ripple(palette.surface, dp(13), if (themeIndex == index) p.accent else palette.border)

                val preview = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(5), dp(4), dp(5), dp(4))
                    background = shape(palette.bg, dp(8), palette.border, dp(1))
                }
                val rail = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    setPadding(dp(3), dp(5), dp(3), dp(4))
                    background = shape(palette.surface, dp(5))
                    addView(View(this@MainActivity).apply { background = shape(p.accent, dp(4)) },
                        LinearLayout.LayoutParams(dp(8), dp(8)))
                    addView(View(this@MainActivity).apply { background = shape(palette.secondary, dp(2)) },
                        LinearLayout.LayoutParams(dp(14), dp(3)).apply { topMargin = dp(5) })
                    addView(View(this@MainActivity).apply { background = shape(palette.secondary, dp(2)) },
                        LinearLayout.LayoutParams(dp(10), dp(3)).apply { topMargin = dp(4) })
                }
                preview.addView(rail, LinearLayout.LayoutParams(dp(24), dp(42)))

                val sheet = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(6), dp(5), dp(6), dp(5))
                    background = shape(palette.surface, dp(5))
                    addView(View(this@MainActivity).apply { background = shape(palette.text, dp(2)) },
                        LinearLayout.LayoutParams(dp(34), dp(4)))
                    addView(View(this@MainActivity).apply { background = shape(palette.secondary, dp(2)) },
                        LinearLayout.LayoutParams(dp(54), dp(3)).apply { topMargin = dp(5) })
                    val actions = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(View(this@MainActivity).apply { background = shape(p.accent, dp(3)) },
                            LinearLayout.LayoutParams(dp(20), dp(5)))
                        addView(View(this@MainActivity).apply { background = shape(palette.secondary, dp(3)) },
                            LinearLayout.LayoutParams(dp(12), dp(5)).apply { leftMargin = dp(5) })
                    }
                    addView(actions, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6) })
                }
                preview.addView(sheet, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(4) })
                addView(preview, LinearLayout.LayoutParams(-1, dp(50)))
                addView(TextView(this@MainActivity).apply {
                    text = (if (themeIndex == index) "✓  " else "") + palette.name
                    textSize = 12f
                    setTextColor(p.text)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER_VERTICAL
                }, marginTop(5))
                setOnClickListener {
                    themeIndex = index
                    getSharedPreferences("yovoice-ui", MODE_PRIVATE).edit().putInt("theme", index).apply()
                    render()
                }
            }
            styleGrid.addView(item, GridLayout.LayoutParams(
                GridLayout.spec(index / 2, 1f), GridLayout.spec(index % 2, 1f)
            ).apply {
                width = 0
                height = dp(86)
                setMargins(dp(3), dp(4), dp(3), dp(4))
            })
        }
        themeCard.addView(styleGrid, marginTop(8))
        body.addView(themeCard, marginTop(12))

        val accentCard = card()
        accentCard.addView(section("主题颜色", "强调色统一应用于主操作、选中状态、进度和线性图标，不叠加多种强调色。"))
        val accentGrid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        accentNames.forEachIndexed { index, name ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                background = ripple(p.surface, dp(12), if (accentIndex == index) p.accent else p.border)
                addView(View(this@MainActivity).apply {
                    background = shape(accentColors[index], dp(8))
                }, LinearLayout.LayoutParams(dp(16), dp(16)))
                addView(TextView(this@MainActivity).apply {
                    text = (if (accentIndex == index) "✓ " else "") + name
                    textSize = 12f
                    setTextColor(p.text)
                    setPadding(dp(5), 0, 0, 0)
                })
                setOnClickListener {
                    accentIndex = index
                    getSharedPreferences("yovoice-ui", MODE_PRIVATE).edit().putInt("accent", index).apply()
                    applyPlatformTheme()
                    render()
                }
            }
            accentGrid.addView(item, GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1f), GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = dp(48)
                setMargins(dp(3), dp(4), dp(3), dp(4))
            })
        }
        accentCard.addView(accentGrid, marginTop(8))
        body.addView(accentCard, marginTop(12))

        val engineCard = card()
        engineCard.addView(section("语音引擎", "查看手机系统语音服务，选择当前用于朗读和合成的引擎。"))
        engineCard.addView(label("手机系统 TTS · " + if (ttsReady) "已就绪" else "未就绪或正在检测", 13, p.secondary), marginTop(5))
        engineCard.addView(secondaryButton("选择语音引擎 / 打开系统语音设置") { showTtsOptions() }, marginTop(8))
        engineCard.addView(label("Android 本地推理引擎 · Audio8 TTS / Qwen3 ASR 等已编入家族", 12, p.secondary), marginTop(8))
        body.addView(engineCard, marginTop(12))

        val runtimeCard = card()
        runtimeCard.addView(section("本地推理性能", "线程数会影响速度、内存占用和耗电；设置在下一次模型加载时生效。"))
        val runtimePrefs = getSharedPreferences("yovoice-runtime", MODE_PRIVATE)
        val currentThreads = runtimePrefs.getInt("threads", 2).coerceIn(1, 4)
        val threadLabel = label("推理线程：$currentThreads", 13, p.secondary)
        runtimeCard.addView(threadLabel, marginTop(5))
        runtimeCard.addView(SeekBar(this).apply {
            max = 3
            progress = currentThreads - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    val threads = value + 1
                    threadLabel.text = "推理线程：$threads"
                    if (fromUser) runtimePrefs.edit().putInt("threads", threads).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }, marginTop(4))
        body.addView(runtimeCard, marginTop(12))

        val storageCard = card()
        storageCard.addView(section("本地存储", "模型文件、生成作品和应用剩余可用空间。"))
        val modelDir = File(filesDir, "models")
        val storageInfo = label("正在统计手机内的模型与作品文件…", 12, p.secondary)
        storageCard.addView(storageInfo, marginTop(5))
        storageExecutor.execute {
            val modelBytes = directoryBytes(modelDir)
            val projectBytes = generatedStorageBytes()
            val freeBytes = runCatching { android.os.StatFs(filesDir.absolutePath).availableBytes }.getOrDefault(0L)
            runOnUiThread {
                if (!destroyed && !isFinishing && !isDestroyed) {
                    storageInfo.text = "模型：${formatStorage(modelBytes)} · 作品：${formatStorage(projectBytes)}\n应用可用空间：${formatStorage(freeBytes)}\n模型目录：${modelDir.absolutePath}"
                }
            }
        }
        storageCard.addView(secondaryButton("打开系统应用设置") {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
            startActivity(intent)
        }, marginTop(8))
        body.addView(storageCard, marginTop(12))

        val assistantCard = card()
        assistantCard.addView(section("AI 助手服务", "配置兼容的在线服务；密钥使用 Android Keystore 加密保存。"))
        val assistantPrefs = getSharedPreferences("yovoice-assistant", MODE_PRIVATE)
        val endpointInput = EditText(this).apply {
            hint = "API 地址，例如 https://api.openai.com/v1"
            setText(assistantPrefs.getString("endpoint", "https://api.openai.com/v1"))
            textSize = 14f; setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.surface, dp(12), p.border, dp(1)); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        assistantCard.addView(endpointInput, marginTop(8))
        val modelInput = EditText(this).apply {
            hint = "模型名称，例如 gpt-4o-mini"
            setText(assistantPrefs.getString("model", "gpt-4o-mini"))
            textSize = 14f; setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.surface, dp(12), p.border, dp(1)); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        assistantCard.addView(modelInput, marginTop(8))
        val keyInput = EditText(this).apply {
            hint = "API Key（使用 Android Keystore 加密保存）"
            setText(runCatching { ApiKeyStore.get(this@MainActivity) }.getOrDefault(""))
            textSize = 14f; setTextColor(p.text); setHintTextColor(p.secondary)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = shape(p.surface, dp(12), p.border, dp(1)); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        assistantCard.addView(keyInput, marginTop(8))
        assistantCard.addView(secondaryButton("保存 AI 服务配置") {
            val endpoint = endpointInput.text.toString().trim().trimEnd('/')
            val model = modelInput.text.toString().trim()
            val key = keyInput.text.toString().trim()
            if (!endpoint.startsWith("https://") || model.isBlank() || key.isBlank()) {
                Toast.makeText(this, "请填写 HTTPS 地址、模型名称和 API Key", Toast.LENGTH_LONG).show()
            } else {
                runCatching {
                    ApiKeyStore.save(this, key)
                    assistantPrefs.edit().putString("endpoint", endpoint).putString("model", model).apply()
                }.onSuccess {
                    Toast.makeText(this, "AI 服务配置已使用 Android Keystore 加密保存", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "保存失败：" + (it.localizedMessage ?: "加密存储不可用"), Toast.LENGTH_LONG).show()
                }
            }
        }, marginTop(10))
        assistantCard.addView(secondaryButton("清除本机保存的 API Key") {
            runCatching { ApiKeyStore.clear(this) }
                .onSuccess { keyInput.setText(""); Toast.makeText(this, "已清除本机 API Key", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this, "清除失败：" + (it.localizedMessage ?: "未知错误"), Toast.LENGTH_LONG).show() }
        }, marginTop(6))
        body.addView(assistantCard, marginTop(12))
        body.addView(label("隐私提示：使用 AI 助手时，输入的创作内容会发送到你配置的第三方服务商；请勿提交密码、密钥或其他敏感信息。", 12, p.secondary), marginTop(10))
    }

    private fun requestAssistant(prompt: String, output: TextView) {
        val prefs = getSharedPreferences("yovoice-assistant", MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "https://api.openai.com/v1").orEmpty().trim().trimEnd('/')
        val model = prefs.getString("model", "gpt-4o-mini").orEmpty().trim()
        val apiKey = runCatching { ApiKeyStore.get(this) }.getOrDefault("").trim()
        if (!endpoint.startsWith("https://") || model.isBlank() || apiKey.isBlank()) {
            Toast.makeText(this, "请先到设置配置 HTTPS 服务地址、模型和 API Key", Toast.LENGTH_LONG).show()
            page = 3
            render()
            return
        }
        output.text = "正在请求 AI 服务…"
        assistantExecutor.execute {
            val result = runCatching {
                val target = if (endpoint.endsWith("/chat/completions")) endpoint else endpoint + "/chat/completions"
                val connection = (java.net.URL(target).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 120_000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer " + apiKey)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                try {
                    val payload = JSONObject().apply {
                        put("model", model)
                        put("messages", org.json.JSONArray()
                            .put(JSONObject().put("role", "system").put("content", "你是 Yovoice 的中文创作助手。请准确理解用户目标，提供可直接使用的创作结果；如信息不足，简洁说明假设。"))
                            .put(JSONObject().put("role", "user").put("content", prompt)))
                        put("temperature", 0.7)
                    }.toString()
                    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    check(code in 200..299) {
                        val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                        "AI 服务返回 HTTP " + code + if (!detail.isNullOrBlank()) "：" + detail else ""
                    }
                    val json = JSONObject(response)
                    val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.opt("content")
                    val answer = when (content) {
                        is String -> content
                        is org.json.JSONArray -> (0 until content.length()).mapNotNull { index ->
                            content.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() }
                        }.joinToString("\n")
                        else -> ""
                    }
                    check(answer.isNotBlank()) { "服务已响应，但没有返回可显示的文本内容" }
                    answer
                } finally {
                    connection.disconnect()
                }
            }.fold({ it }, { "请求失败：" + (it.message ?: it.javaClass.simpleName) })
            runOnUiThread {
                if (!destroyed && !isFinishing) output.text = result
            }
        }
    }

    private fun nativeThreads(): Int =
        getSharedPreferences("yovoice-runtime", MODE_PRIVATE).getInt("threads", 2).coerceIn(1, 4)

    private fun directoryBytes(directory: File): Long {
        if (!directory.exists()) return 0L
        return runCatching {
            directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    /** Generated media lives in separate app-private folders, not only in /projects. */
    private fun generatedStorageBytes(): Long = listOf(
        "projects", "voice-clones", "story-dubbing", "music", "stems", "midi"
    ).sumOf { directoryBytes(File(filesDir, it)) }

    private fun formatStorage(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return "%.1f %s".format(Locale.US, value, units[unit])
    }

    private fun attachDraft(editText: EditText, key: String) {
        editText.setText(ProjectStore.loadDraft(this, key))
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                ProjectStore.saveDraft(this@MainActivity, key, s?.toString().orEmpty())
            }
        })
    }

    private fun saveProjectRecord(title: String, category: String, detail: String, file: File, mime: String) {
        runCatching { ProjectStore.save(this, title, category, detail, file, mime) }
    }

    private fun handleBackNavigation() {
        when {
            page == 0 -> {
                page = 1
                selectedCreateFeature = null
                activeWorkspaceFeature = null
                render()
            }
            activeWorkspaceFeature != null -> {
                activeWorkspaceFeature = null
                selectedCreateFeature = null
                render()
            }
            page != 1 -> {
                page = 1
                selectedCreateFeature = null
                activeWorkspaceFeature = null
                render()
            }
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressedAt <= 2000L) finish()
                else {
                    lastBackPressedAt = now
                    Toast.makeText(this, "再按一次返回键退出 Yovoice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderNav() {
        nav.removeAllViews()
        // Primary navigation is intentionally creation-first. Model management remains a secondary entry.
        listOf(
            Triple("wave", "创作", 1),
            Triple("folder", "项目", 4),
            Triple("settings", "我的", 3)
        ).forEach { (icon, title, targetPage) ->
            val selected = page == targetPage || (targetPage == 1 && page == 0)
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { page = targetPage; selectedCreateFeature = null; activeWorkspaceFeature = null; render() }
            }
            item.addView(LineIconView(this, icon, if (selected) p.accent else p.secondary), LinearLayout.LayoutParams(dp(24), dp(24)))
            item.addView(label(title, 11, if (selected) p.accent else p.secondary, selected).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
            nav.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun iconListRow(icon: String, title: String, subtitle: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(10), dp(12), dp(10))
        background = shape(p.surface, dp(14), p.border, dp(1))
        isClickable = true
        isFocusable = true
        contentDescription = title
        background = ripple(p.surface, dp(14), p.border)
        setOnClickListener { animatePress(this, action) }
        val iconWrap = FrameLayout(this@MainActivity).apply {
            background = shape(Color.argb(if (p.dark) 38 else 22, Color.red(p.accent), Color.green(p.accent), Color.blue(p.accent)), dp(12))
            addView(LineIconView(this@MainActivity, icon, p.accent), FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
        }
        addView(iconWrap, LinearLayout.LayoutParams(dp(40), dp(40)))
        val labels = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 14, p.text, true))
        }
        addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(11) })
        val help = FrameLayout(this@MainActivity).apply {
            contentDescription = "查看功能说明"
            isClickable = true
            isFocusable = true
            background = ripple(p.surface, dp(10))
            addView(LineIconView(this@MainActivity, "info", p.secondary),
                FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setMessage(subtitle)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }
        addView(help, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(LineIconView(this@MainActivity, "chevron", p.secondary), LinearLayout.LayoutParams(dp(18), dp(18)))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = shape(p.surface, dp(20), p.border, dp(1))
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun section(title: String, sub: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label(title, 18, p.text, true), LinearLayout.LayoutParams(0, -2, 1f))
        val help = FrameLayout(this@MainActivity).apply {
            contentDescription = "查看功能说明"
            isClickable = true
            isFocusable = true
            background = ripple(p.surface, dp(10))
            addView(LineIconView(this@MainActivity, "info", p.secondary),
                FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setMessage(sub)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }
        addView(help, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
    private fun infoRow(title: String, detail: String) = TextView(this).apply {
        text = "$title\n$detail"; textSize = 14f; setTextColor(p.text); setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(dp(14), dp(13), dp(14), dp(13)); background = shape(p.surface, dp(14), p.border, dp(1))
    }
    private fun toolRow(title: String, detail: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(10), dp(10), dp(10))
        background = ripple(p.surface, dp(16), p.border)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        addView(label(title, 16, p.text, true), LinearLayout.LayoutParams(0, -2, 1f))
        val help = FrameLayout(this@MainActivity).apply {
            contentDescription = "查看功能说明"
            isClickable = true
            isFocusable = true
            background = ripple(p.surface, dp(10))
            addView(LineIconView(this@MainActivity, "info", p.secondary),
                FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setMessage(detail)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }
        addView(help, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
    private fun label(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun primaryButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 15f; setTextColor(if (p.dark) 0xFF06191A.toInt() else 0xFFFFFFFF.toInt())
        background = ripple(p.accent, dp(14)); setOnClickListener { animatePress(this, action) }
    }
    private fun secondaryButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 13f; setTextColor(p.text)
        background = ripple(p.surface, dp(14), p.border); setOnClickListener { animatePress(this, action) }
    }
    private fun textButton(value: String, size: Int, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = size.toFloat(); gravity = Gravity.CENTER; setTextColor(p.text)
        background = shape(p.surface, dp(14), p.border, dp(1)); setOnClickListener { action() }
    }
    private fun shape(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT, strokeWidth: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }
    private fun ripple(color: Int, radius: Int, stroke: Int = Color.TRANSPARENT) =
        RippleDrawable(ColorStateList.valueOf(if (p.dark) 0x33FFFFFF else 0x22000000),
            shape(color, radius, stroke, if (stroke == Color.TRANSPARENT) 0 else dp(1)),
            shape(Color.WHITE, radius))
    private fun animatePress(view: View, action: () -> Unit) {
        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(75L).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(105L).withEndAction(action).start()
        }.start()
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
        val model = selectedFeatureModel("tts", "audio8-tts-0.6b-q8")
        if (model == null) {
            Toast.makeText(this, "请先在当前页面的模型栏中选择并下载适配的语音模型。", Toast.LENGTH_LONG).show()
            runOnUiThread { showModelPicker("tts") }
            return
        }
        nativeBusy = true
        if (::status.isInitialized) status.text = "正在检查 Android 原生语音引擎…"
        nativeExecutor.execute {
            try {
                check(ModelCatalog.isInstalled(this, model)) { "请先到模型中心下载 Audio8 TTS 模型（约 1.43 GB）" }
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
                val loaded = JSONObject(NativeEngine.loadModel(modelFile.absolutePath, model.family, nativeThreads()))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "模型加载失败") }
                markNativeModelInUse(model)

                val projectDir = File(filesDir, "projects")
                if (!projectDir.exists() && !projectDir.mkdirs()) error("无法创建项目目录")
                val output = File(projectDir, "audio8-preview-" + System.currentTimeMillis() + ".wav")
                runOnUiThread { if (!destroyed && ::status.isInitialized) status.text = "模型已加载，正在合成中文语音…" }
                val result = JSONObject(NativeEngine.synthesize(text, "zh", output.absolutePath, speechRate, speechPitch))
                check(result.optBoolean("ok")) { result.optString("error", "语音合成失败") }
                check(output.isFile && output.length() > 44L) { "合成结果文件无效" }
                pendingTtsSubtitleSrt = buildWholeAudioSubtitle(text, output)
                saveProjectRecord("Audio8 本地语音", "TTS", text.take(48), output, "audio/wav")

                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        if (::status.isInitialized) status.text = "Audio8 本地合成完成 · ${output.length() / 1024} KB" + if (result.optBoolean("styleControlsApplied")) " · 语速/音调已应用" else " · 此模型不支持音调/语速控制"
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
                markNativeModelIdle()
            }
        }
    }

    private fun unloadLocalModel() {
        if (nativeBusy) {
            Toast.makeText(this, "本地引擎正在处理，完成后再释放模型", Toast.LENGTH_SHORT).show()
            return
        }
        nativeBusy = true
        if (::status.isInitialized) status.text = "正在释放本地模型并回收内存…"
        nativeExecutor.execute {
            runCatching { NativeEngine.unload() }
            nativeLoadedModelId = null
            markNativeModelIdle()
            runOnUiThread {
                if (!destroyed && !isFinishing) {
                    if (::status.isInitialized) status.text = "本地模型已释放；再次使用时会重新加载"
                    Toast.makeText(this, "本地模型已释放", Toast.LENGTH_SHORT).show()
                }
                nativeBusy = false
                markNativeModelIdle()
            }
        }
    }

    private var ttsGeneration = 0
    private var activeTtsEngineId = ""
    private var activeTtsEngineLabel = "正在发现语音引擎…"

    private fun initializeSystemTts() {
        multiTtsPackage = MultiTtsPackageImporter.load(this)
        if (multiTtsPackage == null && MultiTtsPackageImporter.hasSavedPackage(this) && ::status.isInitialized) {
            status.text = "上次导入的 MultiTTS 音色包已失效或文件发生变化，请重新导入。"
        }
        val prefs = getSharedPreferences("yovoice-tts", MODE_PRIVATE)
        if (prefs.getString("selected_tts_engine_id", null) == "native:audio8") {
            activeTtsEngineId = "native:audio8"
            activeTtsEngineLabel = "Audio8 本地引擎"
            if (::enginePicker.isInitialized) enginePicker.text = activeTtsEngineLabel + "   ›"
            if (::status.isInitialized) status.text = "已恢复 Audio8 本地引擎选择；请确认模型已下载并校验。"
            return
        }
        val providers = TtsProviderRegistry.discover(this)
        if (providers.isEmpty()) {
            ttsReady = false
            activeTtsEngineLabel = "未发现 Android TTS 服务"
            if (::status.isInitialized) status.text = "没有发现可用的 Android 文字转语音服务，请在手机设置中安装或启用语音引擎。"
            if (::enginePicker.isInitialized) enginePicker.text = activeTtsEngineLabel + "   ›"
            return
        }
        val saved = prefs.getString("selected_tts_engine_id", null)
        val defaultPackage = runCatching {
            android.provider.Settings.Secure.getString(contentResolver, "tts_default_synth")
        }.getOrNull()
        val provider = providers.firstOrNull { it.packageName == saved }
            ?: providers.firstOrNull { it.packageName == defaultPackage }
            ?: providers.first()
        val missingSaved = !saved.isNullOrBlank() && providers.none { it.packageName == saved }
        activateTtsProvider(provider, saved.isNullOrBlank())
        if (missingSaved && ::status.isInitialized) {
            status.text = "上次选择的语音引擎已不可用；当前临时启用 " + provider.label + "，请重新选择。"
        }
    }

    private fun activateTtsProvider(provider: TtsProviderEntry, persist: Boolean = true) {
        nativeLoadedModelId = null
        activeModelStatusView?.takeIf { it.isAttachedToWindow }?.apply { text = "已选择模型 · 当前使用系统语音引擎"; setTextColor(p.secondary) }
        val token = ++ttsGeneration
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        activeTtsEngineId = provider.packageName
        activeTtsEngineLabel = provider.label
        val enginePrefs = getSharedPreferences("yovoice-tts", MODE_PRIVATE)
        selectedVoiceName = enginePrefs.getString("voice:" + provider.packageName, null)
        speechRate = enginePrefs.getFloat("speech_rate:" + provider.packageName, speechRate).coerceIn(0.1f, 3.0f)
        speechPitch = enginePrefs.getFloat("speech_pitch:" + provider.packageName, speechPitch).coerceIn(0.1f, 3.0f)
        if (::speedLabel.isInitialized) speedLabel.text = "语速 %.1f×".format(speechRate)
        if (::pitchLabel.isInitialized) pitchLabel.text = "音调 %.1f×".format(speechPitch)
        if (::speedSeekBar.isInitialized) speedSeekBar.progress = (speechRate * 10).toInt().coerceIn(1, 30)
        if (::pitchSeekBar.isInitialized) pitchSeekBar.progress = (speechPitch * 10).toInt().coerceIn(1, 30)
        if (persist) {
            getSharedPreferences("yovoice-tts", MODE_PRIVATE).edit()
                .putString("selected_tts_engine_id", provider.packageName).apply()
        }
        if (::enginePicker.isInitialized) enginePicker.text = provider.label + " · 正在连接   ›"
        if (::status.isInitialized) status.text = "正在初始化 " + provider.label + "…"
        tts = try {
            TextToSpeech(applicationContext, { result -> onProviderInit(token, result) }, provider.packageName)
        } catch (error: Throwable) {
            // Constructor failures must leave a visible, recoverable state instead of a dead picker.
            ttsReady = false
            val reason = error.localizedMessage ?: error.javaClass.simpleName
            if (::status.isInitialized) status.text = provider.label + " 启动失败：" + reason
            if (::enginePicker.isInitialized) enginePicker.text = provider.label + " · 启动失败，点击重试   ›"
            null
        }
    }

    private fun showTtsOptions() {
        val providers = TtsProviderRegistry.discover(this)
        val defaultPackage = runCatching {
            android.provider.Settings.Secure.getString(contentResolver, "tts_default_synth")
        }.getOrNull()
        val defaultProvider = providers.firstOrNull { it.packageName == defaultPackage }
        val otherProviders = providers.filterNot { it.packageName == defaultPackage }
        val choices = mutableListOf<String>()
        if (defaultProvider != null) choices += "手机自带 / 系统默认 · ${defaultProvider.label}" +
            if (defaultProvider.packageName == activeTtsEngineId) " ✓" else ""
        otherProviders.forEach { choices += it.label + if (it.packageName == activeTtsEngineId) " ✓" else "" }
        choices += if (activeTtsEngineId == "native:audio8") "Audio8 本地引擎 ✓" else "Audio8 本地引擎"
        choices += "打开手机文字转语音设置"
        choices += "安装语音数据"
        android.app.AlertDialog.Builder(this).setTitle("选择语音引擎")
            .setItems(choices.toTypedArray()) { _, which ->
                val defaultCount = if (defaultProvider != null) 1 else 0
                when {
                    defaultProvider != null && which == 0 -> activateTtsProvider(defaultProvider)
                    which < defaultCount + otherProviders.size -> activateTtsProvider(otherProviders[which - defaultCount])
                    which == defaultCount + otherProviders.size -> {
                        ++ttsGeneration
                        runCatching { tts?.stop() }
                        runCatching { tts?.shutdown() }
                        tts = null
                        ttsReady = false
                        activeTtsEngineId = "native:audio8"
                        activeTtsEngineLabel = "Audio8 本地引擎"
                        val nativePrefs = getSharedPreferences("yovoice-tts", MODE_PRIVATE)
                        speechRate = nativePrefs.getFloat("speech_rate:native:audio8", speechRate).coerceIn(0.1f, 3.0f)
                        speechPitch = nativePrefs.getFloat("speech_pitch:native:audio8", speechPitch).coerceIn(0.1f, 3.0f)
                        if (::speedLabel.isInitialized) speedLabel.text = "语速 %.1f×".format(speechRate)
                        if (::pitchLabel.isInitialized) pitchLabel.text = "音调 %.1f×".format(speechPitch)
                        if (::speedSeekBar.isInitialized) speedSeekBar.progress = (speechRate * 10).toInt().coerceIn(1, 30)
                        if (::pitchSeekBar.isInitialized) pitchSeekBar.progress = (speechPitch * 10).toInt().coerceIn(1, 30)
                        selectedVoiceName = null
                        getSharedPreferences("yovoice-tts", MODE_PRIVATE).edit()
                            .putString("selected_tts_engine_id", activeTtsEngineId).apply()
                        if (::enginePicker.isInitialized) enginePicker.text = activeTtsEngineLabel + "   ›"
                        if (::voicePicker.isInitialized) voicePicker.text = "Audio8 使用模型内置音色"
                        if (::status.isInitialized) status.text = "已选择 Audio8；使用前需要下载并校验对应模型。"
                    }
                    which == defaultCount + otherProviders.size + 1 -> launchTtsSettings()
                    else -> try {
                        startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    } catch (_: ActivityNotFoundException) {
                        launchTtsSettings()
                    }
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
        if (activeTtsEngineId.startsWith("native:")) { speakWithLocalModel(); return }
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) { Toast.makeText(this, "请先输入要合成的文字", Toast.LENGTH_SHORT).show(); return }
        val engine = tts
        if (!ttsReady || engine == null) {
            Toast.makeText(this, "系统 TTS 尚未就绪。请先选择语音引擎或安装中文语音数据。", Toast.LENGTH_LONG).show()
            showTtsOptions()
            return
        }
        val language = runCatching { engine.setLanguage(Locale.SIMPLIFIED_CHINESE) }.getOrDefault(TextToSpeech.ERROR)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED || language == TextToSpeech.ERROR) {
            val fallback = runCatching { engine.setLanguage(Locale.getDefault()) }.getOrDefault(TextToSpeech.ERROR)
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED || fallback == TextToSpeech.ERROR) {
                Toast.makeText(this, "当前引擎没有可用的中文语音数据，请安装语音包或切换引擎。", Toast.LENGTH_LONG).show()
                showTtsOptions(); return
            }
        }
        val selectedVoice = selectedVoiceName?.let { name -> engine.voices?.firstOrNull { it.name == name } }
        if (selectedVoice != null) {
            val voiceResult = runCatching { engine.setVoice(selectedVoice) }.getOrDefault(TextToSpeech.ERROR)
            if (voiceResult != TextToSpeech.SUCCESS) {
                if (::status.isInitialized) status.text = "$activeTtsEngineLabel 无法应用已选音色，已取消本次合成。"
                Toast.makeText(this, "所选音色当前不可用，请重新选择。", Toast.LENGTH_LONG).show()
                return
            }
        }
        val rateResult = runCatching { engine.setSpeechRate(speechRate) }.getOrDefault(TextToSpeech.ERROR)
        val pitchResult = runCatching { engine.setPitch(speechPitch) }.getOrDefault(TextToSpeech.ERROR)
        if ((rateResult != TextToSpeech.SUCCESS || pitchResult != TextToSpeech.SUCCESS) && ::status.isInitialized) {
            status.text = "$activeTtsEngineLabel 不支持当前全部语音参数，将按引擎支持的参数合成。"
        }
        val projectDir = File(filesDir, "projects")
        if (!projectDir.exists() && !projectDir.mkdirs()) {
            Toast.makeText(this, "无法创建语音项目目录", Toast.LENGTH_LONG).show()
            return
        }
        val output = File(projectDir, "system-tts-" + System.currentTimeMillis() + ".wav")
        val utteranceId = "yovoice-system-${System.currentTimeMillis()}"
        val requestGeneration = ttsGeneration
        val requestEngineLabel = activeTtsEngineLabel
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                runOnUiThread {
                    if (!destroyed && requestGeneration == ttsGeneration && ::status.isInitialized) {
                        status.text = "$requestEngineLabel 正在合成…"
                    }
                }
            }
            override fun onDone(id: String?) {
                runOnUiThread {
                    if (destroyed || isFinishing || requestGeneration != ttsGeneration) return@runOnUiThread
                    if (!output.isFile || output.length() <= 44L) {
                        if (::status.isInitialized) status.text = "手机语音引擎未生成有效音频文件。"
                        return@runOnUiThread
                    }
                    pendingTtsSubtitleSrt = buildWholeAudioSubtitle(text, output)
                    saveProjectRecord(requestEngineLabel, "TTS", text.take(48), output, "audio/wav")
                    if (::status.isInitialized) status.text = "语音合成完成 · ${output.length() / 1024} KB · $requestEngineLabel"
                    playAudio(Uri.fromFile(output))
                }
            }
            override fun onError(id: String?) {
                runOnUiThread {
                    if (!destroyed && !isFinishing && requestGeneration == ttsGeneration && ::status.isInitialized) {
                        status.text = "$requestEngineLabel 合成失败，请检查音色或切换引擎。"
                    }
                }
            }
        })
        val result = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                engine.synthesizeToFile(text, Bundle(), output, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                engine.synthesizeToFile(
                    text,
                    hashMapOf(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utteranceId),
                    output.absolutePath
                )
            }
        }.getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.ERROR) {
            output.delete()
            Toast.makeText(this, "当前系统 TTS 不支持文件合成或请求失败。", Toast.LENGTH_LONG).show()
        } else if (::status.isInitialized) {
            status.text = "已提交给 $activeTtsEngineLabel 合成…"
        }
    }
    private fun chooseMultiTtsPackage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_MULTITTS_PACKAGE)
    }

    /**
     * MultiTTS owns the DAT decoder. Hand the original SAF document to its
     * ZIP import activity instead of pretending this app can decode the data.
     */
    private fun handoffPackageToMultiTts(uri: Uri) {
        val packageName = "org.nobody.multitts"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(packageName)
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            pendingMultiTtsReturn = true
            startActivity(viewIntent)
            Toast.makeText(this, "已打开 MultiTTS，请在其中确认导入语音包；完成后返回本应用。", Toast.LENGTH_LONG).show()
            return
        } catch (_: ActivityNotFoundException) {
            pendingMultiTtsReturn = false
        } catch (_: SecurityException) {
            pendingMultiTtsReturn = false
        }

        val launchIntent = runCatching { packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
        if (launchIntent != null) {
            pendingMultiTtsReturn = true
            AlertDialog.Builder(this)
                .setTitle("需要在 MultiTTS 中完成导入")
                .setMessage("当前 MultiTTS 版本没有提供 ZIP 直接打开入口。接下来打开 MultiTTS，请在应用菜单中选择“导入数据”，选取刚才的 ZIP；完成后返回本应用。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("打开 MultiTTS") { _, _ ->
                    runCatching { startActivity(launchIntent) }.onFailure {
                        pendingMultiTtsReturn = false
                        Toast.makeText(this, "无法打开 MultiTTS：" + (it.localizedMessage ?: "未知错误"), Toast.LENGTH_LONG).show()
                    }
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("尚未安装 MultiTTS")
                .setMessage("本应用已识别 ZIP 中的音色配置和 DAT 文件，但不能独立解码 MultiTTS 专用 DAT。请先安装 MultiTTS，再导入该 ZIP 并将 MultiTTS 设为手机的文字转语音引擎。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*" }
        startActivityForResult(intent, REQUEST_AUDIO)
    }
    @Deprecated("System document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            when (requestCode) {
                REQUEST_AUDIO -> {
                    audioImportStatusView?.text = "已导入音频，正在准备播放。"
                    playAudio(uri)
                }
                REQUEST_MULTITTS_PACKAGE -> {
                    if (::status.isInitialized) status.text = "正在读取 MultiTTS 音色包并建立名称映射…"
                    storageExecutor.execute {
                        try {
                            val imported = MultiTtsPackageImporter.importPackage(this, uri)
                            runOnUiThread {
                                if (!destroyed && !isFinishing) {
                                    multiTtsPackage = imported
                                    val localCount = imported.speakers.count { it.hasLocalData }
                                    if (::status.isInitialized) status.text =
                                        "已识别 ${imported.engineName} · ${imported.speakers.size} 个音色 · $localCount 个 DAT 文件；正在交给 MultiTTS 导入"
                                    handoffPackageToMultiTts(uri)
                                }
                            }
                        } catch (error: Throwable) {
                            runOnUiThread {
                                if (!destroyed && !isFinishing) {
                                    if (::status.isInitialized) status.text = "音色包导入失败：${error.localizedMessage ?: error.javaClass.simpleName}"
                                    Toast.makeText(this, "无法读取 MultiTTS 音色包", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                REQUEST_ASR_MEDIA -> transcribeMedia(uri)
                REQUEST_CLONE_REFERENCE -> {
                    cloneReferenceUri = uri
                    cloneStatusView?.text = "参考音频已选择，可以生成克隆语音。"
                }
                REQUEST_STORY_ROLE_A -> {
                    storyRoleAUri = uri
                    storyStatusView?.text = "角色 A 参考音频已选择。"
                }
                REQUEST_STORY_ROLE_B -> {
                    storyRoleBUri = uri
                    storyStatusView?.text = "角色 B 参考音频已选择。"
                }
                REQUEST_EXPORT_STORY -> copyExport(uri, pendingStoryExport, "故事配音已导出")
                REQUEST_STEM_MEDIA -> {
                    stemAudioUri = uri
                    stemStatusView?.text = "已选择音乐文件，可以开始六轨分离。"
                }
                REQUEST_MIDI_MEDIA -> {
                    midiAudioUri = uri
                    midiStatusView?.text = "已选择音乐文件，可以开始转换 MIDI。"
                }
                REQUEST_EXPORT_TXT -> runCatching {
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(pendingTextExport) }
                        ?: error("无法创建导出文件")
                }.onSuccess { Toast.makeText(this, "识别文本已导出", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(this, "导出失败：" + (it.localizedMessage ?: "未知错误"), Toast.LENGTH_LONG).show() }
                REQUEST_EXPORT_SRT -> writeTextExport(uri, pendingSubtitleSrt, "SRT 字幕已导出")
                REQUEST_EXPORT_TTS_SRT -> writeTextExport(uri, pendingTtsSubtitleSrt, "语音字幕已导出")
                REQUEST_EXPORT_VTT -> writeTextExport(uri, pendingSubtitleVtt, "WebVTT 字幕已导出")
                REQUEST_EXPORT_CLONE -> copyExport(uri, pendingCloneExport, "克隆语音已导出")
                REQUEST_EXPORT_MUSIC -> copyExport(uri, pendingMusicExport, "生成音乐已导出")
                REQUEST_EXPORT_MIDI -> copyExport(uri, pendingMidiExport, "MIDI 文件已导出")
                REQUEST_EXPORT_GENERIC -> copyExport(uri, pendingGenericExport, "音轨文件已导出")
            }
        }
    }
    private fun chooseAsrMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_ASR_MEDIA)
    }

    private fun exportTranscript() {
        if (pendingTextExport.isBlank()) {
            Toast.makeText(this, "请先完成一次语音识别", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "yovoice-transcript.txt")
        }
        startActivityForResult(intent, REQUEST_EXPORT_TXT)
    }

    private fun transcribeMedia(uri: Uri) {
        if (nativeBusy) {
            Toast.makeText(this, "本地推理引擎正在处理，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        val model = selectedFeatureModel("asr", "qwen3-asr-0.6b-q8")
        if (model == null) {
            Toast.makeText(this, "请先选择并下载适配的识别模型。", Toast.LENGTH_LONG).show()
            showModelPicker("asr")
            return
        }
        nativeBusy = true
        asrStatusView?.text = "正在解码音轨并转换为 16 kHz 单声道 PCM…"
        asrResultView?.text = "正在准备识别…"
        nativeExecutor.execute {
            try {
                if (!ModelCatalog.isInstalled(this, model)) {
                    runOnUiThread {
                        if (!destroyed && !isFinishing) {
                            asrStatusView?.text = "模型尚未完整安装或校验失败"
                            Toast.makeText(this, "请先在模型中心下载 Qwen3 ASR 0.6B 模型包（约 2.28 GB，含识别模型与对齐器文件）", Toast.LENGTH_LONG).show()
                            page = 2
                            render()
                        }
                    }
                    return@execute
                }
                val pcm = AndroidAudioDecoder.decodeTo16kMono(this, uri)
                runOnUiThread {
                    if (!destroyed && !isFinishing) asrStatusView?.text =
                        "音频解码完成 · " + (pcm.size / 16_000) + " 秒 · 正在加载 Qwen3 ASR…"
                }
                val modelRoot = ModelCatalog.installedDirectory(this, model.id)
                val modelFile = File(modelRoot, model.localRelativePath(model.remotePath))
                check(modelFile.isFile) { "ASR 模型文件不存在，请重新下载模型" }
                val alignerAsset = model.packageFiles().firstOrNull { it.path.contains("ForcedAligner", ignoreCase = true) }
                    ?: error("ASR 模型包缺少 Qwen3 Forced Aligner 文件")
                val alignerFile = File(modelRoot, model.localRelativePath(alignerAsset.path))
                check(alignerFile.isFile) { "强制对齐器文件不存在，请重新下载完整 ASR 模型包" }
                val loaded = JSONObject(NativeEngine.loadTaskModel(
                    modelFile.absolutePath, model.family, "asr", "offline", threads = nativeThreads(), auxiliaryPath = alignerFile.absolutePath
                ))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "ASR 模型加载失败") }
                markNativeModelInUse(model)
                runOnUiThread {
                    if (!destroyed && !isFinishing) asrStatusView?.text = "模型已加载，正在识别音频…"
                }
                val result = JSONObject(NativeEngine.transcribe(pcm, 16_000, 1, ""))
                check(result.optBoolean("ok")) { result.optString("error", "语音识别失败") }
                val transcript = result.optString("text").trim()
                check(transcript.isNotBlank()) { "模型没有返回识别文本" }
                pendingTextExport = transcript
                val transcriptDir = File(filesDir, "projects")
                if (!transcriptDir.exists() && !transcriptDir.mkdirs()) error("无法创建项目目录")
                val transcriptFile = File(transcriptDir, "transcript-" + System.currentTimeMillis() + ".txt")
                transcriptFile.writeText(transcript, Charsets.UTF_8)
                saveProjectRecord("音频转录", "ASR", transcript.take(48), transcriptFile, "text/plain")
                val segments = result.optJSONArray("segments") ?: org.json.JSONArray()
                pendingSubtitleSrt = buildSubtitles(segments, result.optInt("sampleRate", 16_000), false)
                pendingSubtitleVtt = buildSubtitles(segments, result.optInt("sampleRate", 16_000), true)
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        asrResultView?.text = transcript
                        asrStatusView?.text = "本地识别完成 · Qwen3 ASR 0.6B · " + transcript.length + " 字 · " + segments.length() + " 个时间戳片段"
                        Toast.makeText(this, "语音识别完成", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        asrStatusView?.text = "识别失败：" + (error.localizedMessage ?: error.javaClass.simpleName)
                        asrResultView?.text = "请检查文件是否含有可解码音轨、模型是否完整，以及设备剩余内存。"
                        Toast.makeText(this, "本地 ASR 处理失败", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                nativeBusy = false
                markNativeModelIdle()
            }
        }
    }

    private fun chooseCloneReference() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CLONE_REFERENCE)
    }

    private fun exportCloneAudio() {
        val source = pendingCloneExport
        if (source == null || !source.isFile) {
            Toast.makeText(this, "请先生成克隆语音", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "yovoice-cloned-voice.wav")
        }
        startActivityForResult(intent, REQUEST_EXPORT_CLONE)
    }

    private fun cloneVoice() {
        val text = cloneTextInput.text?.toString()?.trim().orEmpty()
        val referenceText = cloneReferenceTextInput.text?.toString()?.trim().orEmpty()
        val referenceUri = cloneReferenceUri
        if (text.isBlank() || referenceText.isBlank() || referenceUri == null) {
            Toast.makeText(this, "请填写目标文本、参考音频转录，并选择参考音频", Toast.LENGTH_LONG).show()
            return
        }
        if (nativeBusy) {
            Toast.makeText(this, "本地推理引擎正在处理，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        val model = selectedFeatureModel("clone", "audio8-tts-0.6b-q8")
        if (model == null) {
            Toast.makeText(this, "请先选择并下载适配的声音克隆模型。", Toast.LENGTH_LONG).show()
            showModelPicker("clone")
            return
        }
        nativeBusy = true
        cloneStatusView?.text = "正在校验 Audio8 TTS 模型…"
        nativeExecutor.execute {
            try {
                check(ModelCatalog.isInstalled(this, model)) {
                    "请先在模型中心下载 Audio8 TTS Realistic 0.6B Q8 模型（约 1.43 GB）"
                }
                runOnUiThread {
                    if (!destroyed && !isFinishing) cloneStatusView?.text = "正在解码参考音频（最多 30 秒）…"
                }
                val referencePcm = AndroidAudioDecoder.decodeTo16kMono(this, referenceUri, 30)
                val modelRoot = ModelCatalog.installedDirectory(this, model.id)
                val modelFile = File(modelRoot, model.localRelativePath(model.remotePath))
                check(modelFile.isFile) { "Audio8 TTS 模型文件不存在，请重新下载" }
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "clon", "offline", threads = nativeThreads()))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "声音克隆模型加载失败") }
                markNativeModelInUse(model)
                runOnUiThread {
                    if (!destroyed && !isFinishing) cloneStatusView?.text = "模型已加载，正在克隆声音并生成语音…"
                }
                val outputDir = File(filesDir, "voice-clones")
                if (!outputDir.exists() && !outputDir.mkdirs()) error("无法创建声音输出目录")
                val output = File(outputDir, "clone-" + System.currentTimeMillis() + ".wav")
                val result = JSONObject(NativeEngine.cloneVoice(
                    text, referenceText, referencePcm, 16_000, 1, output.absolutePath
                ))
                check(result.optBoolean("ok")) { result.optString("error", "声音克隆生成失败") }
                check(output.isFile && output.length() > 44L) { "克隆语音输出文件无效" }
                pendingCloneExport = output
                saveProjectRecord("声音克隆", "克隆语音", text.take(48), output, "audio/wav")
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        cloneStatusView?.text = "声音克隆完成 · " + (output.length() / 1024) + " KB"
                        playAudio(Uri.fromFile(output))
                        Toast.makeText(this, "克隆语音生成完成", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        cloneStatusView?.text = "克隆失败：" + (error.localizedMessage ?: error.javaClass.simpleName)
                        Toast.makeText(this, "声音克隆失败，请检查模型和参考音频", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                nativeBusy = false
                markNativeModelIdle()
            }
        }
    }

    private fun chooseStemAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_STEM_MEDIA)
    }

    private fun chooseMidiAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_MIDI_MEDIA)
    }

    private fun generateMusic() {
        val prompt = musicPromptInput.text?.toString()?.trim().orEmpty()
        val duration = musicDurationInput.text?.toString()?.trim().orEmpty().toFloatOrNull()
        if (prompt.isBlank() || duration == null || duration !in 1f..120f) {
            Toast.makeText(this, "请填写音乐描述，并将时长设置为 1–120 秒", Toast.LENGTH_LONG).show()
            return
        }
        if (nativeBusy) return
        val model = selectedFeatureModel("music", "stable-audio-3-small-music-q8")
        if (model == null) {
            Toast.makeText(this, "请先选择并下载适配的音乐模型。", Toast.LENGTH_LONG).show()
            showModelPicker("music")
            return
        }
        nativeBusy = true
        musicStatusView?.text = "正在校验 Stable Audio 模型…"
        nativeExecutor.execute {
            try {
                check(ModelCatalog.isInstalled(this, model)) { "请先下载 Stable Audio 3 Small Music Q8 模型（约 1.68 GB）" }
                val modelFile = File(ModelCatalog.installedDirectory(this, model.id), model.localRelativePath(model.remotePath))
                check(modelFile.isFile) { "音乐模型文件不存在，请重新下载" }
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "gen", "offline", threads = nativeThreads()))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "音乐模型加载失败") }
                markNativeModelInUse(model)
                runOnUiThread { if (!destroyed && !isFinishing) musicStatusView?.text = "模型已加载，正在生成音乐…" }
                val outputDir = File(filesDir, "music")
                if (!outputDir.exists() && !outputDir.mkdirs()) error("无法创建音乐输出目录")
                val output = File(outputDir, "music-" + System.currentTimeMillis() + ".wav")
                val result = JSONObject(NativeEngine.generateMusic(prompt, output.absolutePath, duration.toString(), "8", "1", ""))
                check(result.optBoolean("ok")) { result.optString("error", "音乐生成失败") }
                check(output.isFile && output.length() > 44L) { "音乐输出文件无效" }
                pendingMusicExport = output
                saveProjectRecord("AI 音乐生成", "音乐", prompt.take(48), output, "audio/wav")
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        musicStatusView?.text = "音乐生成完成 · " + (output.length() / 1024) + " KB"
                        playAudio(Uri.fromFile(output))
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!destroyed && !isFinishing) musicStatusView?.text =
                        "生成失败：" + (error.localizedMessage ?: error.javaClass.simpleName)
                }
            } finally {
                nativeBusy = false
                markNativeModelIdle()
            }
        }
    }

    private fun separateAudio() {
        val uri = stemAudioUri
        if (uri == null) {
            Toast.makeText(this, "请先选择待分离的音乐文件", Toast.LENGTH_SHORT).show()
            return
        }
        if (nativeBusy) return
        val model = selectedFeatureModel("stems", "htdemucs-6stems-q8")
        if (model == null) {
            Toast.makeText(this, "请先选择并下载适配的音轨分离模型。", Toast.LENGTH_LONG).show()
            showModelPicker("stems")
            return
        }
        nativeBusy = true
        stemStatusView?.text = "正在校验 HTDemucs 模型…"
        stemResultsContainer?.removeAllViews()
        nativeExecutor.execute {
            try {
                check(ModelCatalog.isInstalled(this, model)) { "请先下载 HTDemucs 6-stem Q8 模型（约 42 MB）" }
                runOnUiThread { if (!destroyed && !isFinishing) stemStatusView?.text = "正在解码并转换为 44.1 kHz 立体声…" }
                val pcm = AndroidAudioDecoder.decodeToPcm(this, uri, 44_100, 2, 180)
                val modelFile = File(ModelCatalog.installedDirectory(this, model.id), model.localRelativePath(model.remotePath))
                check(modelFile.isFile) { "六轨模型文件不存在，请重新下载" }
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "sep", "offline", threads = nativeThreads()))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "音轨分离模型加载失败") }
                markNativeModelInUse(model)
                runOnUiThread { if (!destroyed && !isFinishing) stemStatusView?.text = "模型已加载，正在分离六条音轨…" }
                val outputDir = File(filesDir, "stems/stems-" + System.currentTimeMillis())
                val result = JSONObject(NativeEngine.separateStems(pcm, 44_100, 2, outputDir.absolutePath))
                check(result.optBoolean("ok")) { result.optString("error", "音轨分离失败") }
                val stems = result.getJSONArray("stems")
                check(stems.length() > 0) { "模型没有返回音轨" }
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        stemResultsContainer?.removeAllViews()
                        for (index in 0 until stems.length()) {
                            val item = stems.getJSONObject(index)
                            val name = item.optString("name", "Stem " + (index + 1))
                            val file = File(item.getString("path"))
                            saveProjectRecord(name + " 分离音轨", "音轨分离", "HTDemucs 六轨", file, "audio/wav")
                            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                            row.addView(label(name, 13, p.text, true), LinearLayout.LayoutParams(0, dp(48), 1f))
                            row.addView(textButton("试听", 12) { playAudio(Uri.fromFile(file)) }, LinearLayout.LayoutParams(dp(60), dp(42)))
                            row.addView(textButton("导出", 12) { launchFileExport(file, "audio/wav", file.name, REQUEST_EXPORT_GENERIC) }, LinearLayout.LayoutParams(dp(60), dp(42)))
                            stemResultsContainer?.addView(row)
                        }
                        stemStatusView?.text = "六轨分离完成 · " + stems.length() + " 条音轨"
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!destroyed && !isFinishing) stemStatusView?.text =
                        "分离失败：" + (error.localizedMessage ?: error.javaClass.simpleName)
                }
            } finally {
                nativeBusy = false
                markNativeModelIdle()
            }
        }
    }

    private fun convertToMidi() {
        val uri = midiAudioUri
        if (uri == null) {
            Toast.makeText(this, "请先选择待转换的音乐文件", Toast.LENGTH_SHORT).show()
            return
        }
        if (nativeBusy) return
        val model = selectedFeatureModel("midi", "muscriptor-small-f32")
        if (model == null) {
            Toast.makeText(this, "请先选择并下载适配的 MIDI 模型。", Toast.LENGTH_LONG).show()
            showModelPicker("midi")
            return
        }
        nativeBusy = true
        midiStatusView?.text = "正在校验 MuScriptor 模型…"
        nativeExecutor.execute {
            try {
                check(ModelCatalog.isInstalled(this, model)) { "请先下载 MuScriptor Small F32 模型（约 412 MB）" }
                runOnUiThread { if (!destroyed && !isFinishing) midiStatusView?.text = "正在解码音乐并转换为 44.1 kHz 立体声…" }
                val pcm = AndroidAudioDecoder.decodeToPcm(this, uri, 44_100, 2, 180)
                val modelFile = File(ModelCatalog.installedDirectory(this, model.id), model.localRelativePath(model.remotePath))
                check(modelFile.isFile) { "MuScriptor 模型文件不存在，请重新下载" }
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "midi", "offline", threads = nativeThreads()))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "MIDI 模型加载失败") }
                markNativeModelInUse(model)
                runOnUiThread { if (!destroyed && !isFinishing) midiStatusView?.text = "模型已加载，正在提取音符事件…" }
                val outputDir = File(filesDir, "midi")
                if (!outputDir.exists() && !outputDir.mkdirs()) error("无法创建 MIDI 输出目录")
                val output = File(outputDir, "transcription-" + System.currentTimeMillis() + ".mid")
                val instruments = midiInstrumentInput.text?.toString()?.trim().orEmpty()
                val result = JSONObject(NativeEngine.transcribeToMidi(pcm, 44_100, 2, output.absolutePath, instruments))
                check(result.optBoolean("ok")) { result.optString("error", "音频转 MIDI 失败") }
                check(output.isFile && output.length() > 20L) { "MIDI 输出文件无效" }
                pendingMidiExport = output
                saveProjectRecord("音频转 MIDI", "MIDI", instruments.ifBlank { "MuScriptor Small" }, output, "audio/midi")
                runOnUiThread { if (!destroyed && !isFinishing) midiStatusView?.text = "MIDI 转换完成 · " + (output.length() / 1024) + " KB" }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!destroyed && !isFinishing) midiStatusView?.text =
                        "转换失败：" + (error.localizedMessage ?: error.javaClass.simpleName)
                }
            } finally {
                nativeBusy = false
                markNativeModelIdle()
            }
        }
    }

    private fun exportMusic() {
        launchFileExport(pendingMusicExport, "audio/wav", "yovoice-generated-music.wav", REQUEST_EXPORT_MUSIC)
    }

    private fun exportMidi() {
        launchFileExport(pendingMidiExport, "audio/midi", "yovoice-transcription.mid", REQUEST_EXPORT_MIDI)
    }

    private fun launchFileExport(file: File?, mime: String, title: String, requestCode: Int) {
        if (file == null || !file.isFile) {
            Toast.makeText(this, "请先完成对应的生成或处理任务", Toast.LENGTH_SHORT).show()
            return
        }
        if (requestCode == REQUEST_EXPORT_GENERIC) pendingGenericExport = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, title)
        }
        startActivityForResult(intent, requestCode)
    }

    private fun copyExport(uri: Uri, source: File?, successMessage: String) {
        runCatching {
            val file = source ?: error("没有可导出的文件")
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("无法创建导出文件")
        }.onSuccess { Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "导出失败：" + (it.localizedMessage ?: "未知错误"), Toast.LENGTH_LONG).show() }
    }

    private fun exportSubtitle(webVtt: Boolean) {
        val content = if (webVtt) pendingSubtitleVtt else pendingSubtitleSrt
        if (content.isBlank()) {
            Toast.makeText(this, "当前识别结果没有可用的时间戳，请确认模型包中的强制对齐器已安装", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (webVtt) "text/vtt" else "application/x-subrip"
            putExtra(Intent.EXTRA_TITLE, if (webVtt) "yovoice-subtitles.vtt" else "yovoice-subtitles.srt")
        }
        startActivityForResult(intent, if (webVtt) REQUEST_EXPORT_VTT else REQUEST_EXPORT_SRT)
    }

    private fun writeTextExport(uri: Uri, content: String, success: String) {
        runCatching {
            check(content.isNotBlank()) { "没有可导出的字幕内容" }
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                ?: error("无法创建字幕文件")
        }.onSuccess { Toast.makeText(this, success, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "字幕导出失败：" + (it.localizedMessage ?: "未知错误"), Toast.LENGTH_LONG).show() }
    }

    private fun buildSubtitles(segments: org.json.JSONArray, sampleRate: Int, webVtt: Boolean): String {
        if (sampleRate <= 0 || segments.length() == 0) return ""
        val groups = mutableListOf<List<org.json.JSONObject>>()
        val current = mutableListOf<org.json.JSONObject>()
        fun flush() {
            if (current.isNotEmpty()) {
                groups.add(current.toList())
                current.clear()
            }
        }
        for (index in 0 until segments.length()) {
            val word = segments.optJSONObject(index) ?: continue
            val text = word.optString("text").trim()
            val start = word.optLong("startSample", -1L)
            val end = word.optLong("endSample", -1L)
            if (text.isEmpty() || start < 0L || end <= start) continue
            if (current.isNotEmpty()) {
                val firstStart = current.first().optLong("startSample")
                val elapsed = (end - firstStart).toDouble() / sampleRate
                if (current.size >= 6 || elapsed >= 3.5) flush()
            }
            current.add(word)
            if (text.matches(Regex(".*[.!?。！？]$"))) flush()
        }
        flush()
        if (groups.isEmpty()) return ""
        val out = StringBuilder()
        if (webVtt) out.append("WEBVTT\n\n")
        groups.forEachIndexed { index, group ->
            val start = group.first().optLong("startSample")
            val end = group.last().optLong("endSample")
            val parts = group.map { it.optString("text").trim() }
            val cjk = parts.any { part -> part.any { it in '\u4e00'..'\u9fff' } }
            val line = parts.joinToString(if (cjk) "" else " ")
            if (webVtt) {
                out.append(formatSubtitleTime(start, sampleRate, true)).append(" --> ")
                    .append(formatSubtitleTime(end, sampleRate, true)).append('\n')
                out.append(line).append("\n\n")
            } else {
                out.append(index + 1).append('\n')
                out.append(formatSubtitleTime(start, sampleRate, false)).append(" --> ")
                    .append(formatSubtitleTime(end, sampleRate, false)).append('\n')
                out.append(line).append("\n\n")
            }
        }
        return out.toString()
    }

    private fun formatSubtitleTime(samples: Long, sampleRate: Int, webVtt: Boolean): String {
        val millis = (samples.coerceAtLeast(0L) * 1000L / sampleRate).coerceAtLeast(0L)
        val hours = millis / 3_600_000L
        val minutes = (millis / 60_000L) % 60L
        val seconds = (millis / 1000L) % 60L
        val fraction = millis % 1000L
        val separator = if (webVtt) "." else ","
        return String.format(java.util.Locale.US, "%02d:%02d:%02d%s%03d", hours, minutes, seconds, separator, fraction)
    }

    private data class StoryLine(val speaker: String, val text: String)

    private fun chooseStoryReference(role: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, if (role == 1) REQUEST_STORY_ROLE_A else REQUEST_STORY_ROLE_B)
    }

    private fun exportStoryDubbing() {
        launchFileExport(pendingStoryExport, "audio/wav", "yovoice-story-dubbing.wav", REQUEST_EXPORT_STORY)
    }

    private fun generateStoryDubbing() {
        val script = storyScriptInput.text?.toString().orEmpty()
        val roleAName = storyRoleANameInput.text?.toString()?.trim().orEmpty()
        val roleBName = storyRoleBNameInput.text?.toString()?.trim().orEmpty()
        val roleAText = storyRoleATextInput.text?.toString()?.trim().orEmpty()
        val roleBText = storyRoleBTextInput.text?.toString()?.trim().orEmpty()
        val roleAUri = storyRoleAUri
        val roleBUri = storyRoleBUri
        if (script.isBlank() || roleAName.isBlank() || roleBName.isBlank() || roleAName == roleBName) {
            Toast.makeText(this, "请填写故事脚本，并为两个角色设置不同名称", Toast.LENGTH_LONG).show()
            return
        }
        val lines = script.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) null else {
                val split = line.indexOfAny(charArrayOf('：', ':'))
                if (split <= 0) StoryLine(roleAName, line)
                else StoryLine(line.substring(0, split).trim(), line.substring(split + 1).trim())
            }
        }.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) {
            Toast.makeText(this, "故事脚本中没有可配音的台词", Toast.LENGTH_SHORT).show()
            return
        }
        if (lines.any { it.speaker != roleAName && it.speaker != roleBName }) {
            Toast.makeText(this, "脚本中的角色名必须与角色 A / B 名称完全一致", Toast.LENGTH_LONG).show()
            return
        }
        if (lines.any { it.speaker == roleAName } && (roleAUri == null || roleAText.isBlank())) {
            Toast.makeText(this, "请为角色 A 选择参考音频并填写参考文本", Toast.LENGTH_LONG).show()
            return
        }
        if (lines.any { it.speaker == roleBName } && (roleBUri == null || roleBText.isBlank())) {
            Toast.makeText(this, "请为角色 B 选择参考音频并填写参考文本", Toast.LENGTH_LONG).show()
            return
        }
        if (nativeBusy) return
        val model = selectedFeatureModel("story", "audio8-tts-0.6b-q8")
        if (model == null) {
            Toast.makeText(this, "请先选择并下载适配的故事配音模型。", Toast.LENGTH_LONG).show()
            showModelPicker("story")
            return
        }
        nativeBusy = true
        storyStatusView?.text = "正在准备多角色配音模型…"
        nativeExecutor.execute {
            try {
                check(ModelCatalog.isInstalled(this, model)) { "请先下载 Audio8 TTS Realistic 0.6B Q8 模型（约 1.43 GB）" }
                val root = ModelCatalog.installedDirectory(this, model.id)
                val modelFile = File(root, model.localRelativePath(model.remotePath))
                check(modelFile.isFile) { "Audio8 TTS 模型文件不存在" }
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "clon", "offline", threads = nativeThreads()))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "故事配音模型加载失败") }
                markNativeModelInUse(model)
                val pcmA = if (lines.any { it.speaker == roleAName }) AndroidAudioDecoder.decodeTo16kMono(this, roleAUri!!, 30) else null
                val pcmB = if (lines.any { it.speaker == roleBName }) AndroidAudioDecoder.decodeTo16kMono(this, roleBUri!!, 30) else null
                val outputDir = File(filesDir, "story-dubbing/story-" + System.currentTimeMillis())
                if (!outputDir.exists() && !outputDir.mkdirs()) error("无法创建故事配音目录")
                val clips = mutableListOf<File>()
                lines.forEachIndexed { index, line ->
                    val referencePcm = if (line.speaker == roleAName) pcmA!! else pcmB!!
                    val referenceText = if (line.speaker == roleAName) roleAText else roleBText
                    val clip = File(outputDir, "line-" + (index + 1).toString().padStart(3, '0') + ".wav")
                    val result = JSONObject(NativeEngine.cloneVoice(
                        line.text, referenceText, referencePcm, 16_000, 1, clip.absolutePath
                    ))
                    check(result.optBoolean("ok")) {
                        "第 " + (index + 1) + " 句生成失败：" + result.optString("error", "未知错误")
                    }
                    check(clip.isFile && clip.length() > 44L) { "第 " + (index + 1) + " 句没有生成有效音频" }
                    clips.add(clip)
                    runOnUiThread {
                        if (!destroyed && !isFinishing) storyStatusView?.text =
                            "正在生成故事配音 · " + (index + 1) + " / " + lines.size + " 句"
                    }
                }
                val output = File(outputDir, "story-dubbing.wav")
                WavJoiner.join(clips, output, 220)
                pendingStoryExport = output
                saveProjectRecord("多角色故事配音", "故事配音", lines.size.toString() + " 句", output, "audio/wav")
                runOnUiThread {
                    if (!destroyed && !isFinishing) {
                        storyStatusView?.text = "多角色故事配音完成 · " + lines.size + " 句"
                        playAudio(Uri.fromFile(output))
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    if (!destroyed && !isFinishing) storyStatusView?.text =
                        "故事配音失败：" + (error.localizedMessage ?: error.javaClass.simpleName)
                }
            } finally {
                nativeBusy = false
                markNativeModelIdle()
            }
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
    private fun onProviderInit(token: Int, result: Int) {
        runOnUiThread {
            if (destroyed || isFinishing || isDestroyed || token != ttsGeneration) return@runOnUiThread
            ttsReady = result == TextToSpeech.SUCCESS && tts != null
            val language = if (ttsReady) runCatching {
                tts?.setLanguage(Locale.SIMPLIFIED_CHINESE) ?: TextToSpeech.ERROR
            }.getOrDefault(TextToSpeech.ERROR) else TextToSpeech.ERROR
            val message = when {
                !ttsReady -> activeTtsEngineLabel + " 初始化失败，请检查该语音服务。"
                language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED || language == TextToSpeech.ERROR ->
                    activeTtsEngineLabel + " 已启动，但没有可用的中文语音数据；请安装语音包或切换引擎。"
                else -> activeTtsEngineLabel + " 已就绪，可以试听。"
            }
            if (::status.isInitialized) status.text = message
            if (::enginePicker.isInitialized) {
                enginePicker.text = activeTtsEngineLabel + if (ttsReady) " · 已连接   ›" else " · 初始化失败   ›"
            }
            if (ttsReady && ::voicePicker.isInitialized) refreshSystemVoices()
        }
    }

    private fun mappedMultiTtsSpeaker(voice: android.speech.tts.Voice): MultiTtsSpeaker? =
        multiTtsPackage?.speakers?.firstOrNull { speaker ->
            voice.name.equals(speaker.id, true) ||
                voice.name.contains(speaker.id, true) ||
                voice.name.equals(speaker.code, true) ||
                voice.name.contains(speaker.code, true)
        }

    private fun voiceDisplayName(voice: android.speech.tts.Voice, index: Int): String {
        // MultiTTS exposes Speaker.id (not Speaker.code) as Android Voice.name.
        val mapped = mappedMultiTtsSpeaker(voice)
        if (mapped != null) return mapped.name
        val language = voice.locale.getDisplayLanguage(Locale.SIMPLIFIED_CHINESE).ifBlank { "未知语言" }
        val country = voice.locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
        val localeLabel = when {
            voice.locale.language.equals("zh", true) && voice.locale.country.equals("CN", true) -> "中文普通话"
            voice.locale.language.equals("zh", true) && country.isNotBlank() -> "中文$country"
            else -> listOf(language, country).filter { it.isNotBlank() }.joinToString("")
        }
        val quality = if (voice.quality >= android.speech.tts.Voice.QUALITY_HIGH) "高品质" else "标准"
        val connection = if (voice.isNetworkConnectionRequired) "网络" else "离线"
        return "$localeLabel · 音色 ${index + 1} · $quality · $connection"
    }

    private fun showVoiceSearchDialog() {
        val voices = availableTtsVoices
        if (voices.isEmpty()) {
            Toast.makeText(this, "当前引擎尚未提供可选音色，请先确认引擎已连接并安装中文语音数据。", Toast.LENGTH_LONG).show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val search = EditText(this).apply {
            hint = "搜索中文名称、语言或音色特征"
            textSize = 14f
            setSingleLine(true)
            setTextColor(p.text)
            setHintTextColor(p.secondary)
            background = shape(p.bg, dp(12), p.border, dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        container.addView(search, LinearLayout.LayoutParams(-1, dp(46)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        container.addView(scroll, LinearLayout.LayoutParams(-1, dp(360)).apply { topMargin = dp(10) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择音色")
            .setView(container)
            .setNegativeButton("关闭", null)
            .create()

        fun renderRows(query: String) {
            list.removeAllViews()
            val filtered = voices.withIndex().filter { (index, voice) ->
                val mapped = mappedMultiTtsSpeaker(voice)
                val haystack = listOf(
                    voiceDisplayName(voice, index), voice.name, voice.locale.toLanguageTag(),
                    mapped?.description.orEmpty(), mapped?.descText.orEmpty()
                ).joinToString(" ").lowercase(Locale.ROOT)
                haystack.contains(query.trim().lowercase(Locale.ROOT))
            }
            filtered.forEach { (index, voice) ->
                val mapped = mappedMultiTtsSpeaker(voice)
                val title = voiceDisplayName(voice, index)
                val detail = mapped?.let { speaker ->
                    listOfNotNull(
                        speaker.description.takeIf { it.isNotBlank() },
                        if (speaker.hasLocalData) "包内存在对应 DAT 文件（引擎加载待验证）" else null,
                        if (voice.isNetworkConnectionRequired) "需要网络" else "离线可用"
                    ).joinToString(" · ")
                } ?: "${voice.locale.toLanguageTag()} · " +
                    if (voice.isNetworkConnectionRequired) "需要网络" else "离线可用"
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = ripple(p.surface, dp(11), p.border)
                    isClickable = true
                    isFocusable = true
                    addView(label(title, 14, p.text, true))
                    addView(label(detail, 11, p.secondary).apply { maxLines = 2 }, marginTop(3))
                    setOnClickListener {
                        val engine = tts
                        val applied = engine != null && ttsReady &&
                            runCatching { engine.setVoice(voice) == TextToSpeech.SUCCESS }.getOrDefault(false)
                        if (applied) {
                            selectedVoiceName = voice.name
                            getSharedPreferences("yovoice-tts", MODE_PRIVATE).edit()
                                .putString("voice:" + activeTtsEngineId, voice.name).apply()
                            val rateResult = runCatching { engine!!.setSpeechRate(speechRate) }.getOrDefault(TextToSpeech.ERROR)
                            val pitchResult = runCatching { engine!!.setPitch(speechPitch) }.getOrDefault(TextToSpeech.ERROR)
                            voicePicker.text = title
                            if (::status.isInitialized) {
                                status.text = if (rateResult == TextToSpeech.SUCCESS && pitchResult == TextToSpeech.SUCCESS)
                                    "已切换音色：$title"
                                else "已切换音色：$title；当前引擎不支持全部语音参数。"
                            }
                            dialog.dismiss()
                        } else {
                            if (::status.isInitialized) status.text = "音色切换失败：引擎未接受该音色，原选择保持不变。"
                            Toast.makeText(this@MainActivity, "当前引擎无法应用该音色，请尝试其他音色。", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
            if (filtered.isEmpty()) list.addView(label("没有找到匹配的音色", 13, p.secondary).apply {
                setPadding(dp(8), dp(18), dp(8), dp(18))
            })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderRows(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderRows("")
        dialog.show()
    }

    private fun refreshSystemVoices() {
        val reportedVoices = runCatching { tts?.voices.orEmpty() }.getOrDefault(emptyList())
        // MultiTTS always publishes a synthetic NOT_SET entry for its automatic/default
        // speaker. It is not a real selectable speaker when imported voices exist.
        val usableVoices = if (activeTtsEngineId.equals("org.nobody.multitts", true)) {
            val actual = reportedVoices.filterNot { it.name.equals("NOT_SET", true) }
            if (actual.isNotEmpty()) actual else emptyList()
        } else reportedVoices
        val voices = usableVoices.sortedWith(
            compareBy({ !it.locale.language.equals("zh", true) }, { it.isNetworkConnectionRequired }, { it.name })
        )
        availableTtsVoices = voices
        if (voices.isNotEmpty()) {
            val currentIndex = voices.indexOfFirst { it.name == selectedVoiceName }.let { if (it >= 0) it else 0 }
            val selected = voices[currentIndex]
            val engine = tts
            val applied = engine != null && ttsReady &&
                runCatching { engine.setVoice(selected) == TextToSpeech.SUCCESS }.getOrDefault(false)
            if (applied) {
                selectedVoiceName = selected.name
                getSharedPreferences("yovoice-tts", MODE_PRIVATE).edit()
                    .putString("voice:" + activeTtsEngineId, selected.name).apply()
                val rateResult = runCatching { engine!!.setSpeechRate(speechRate) }.getOrDefault(TextToSpeech.ERROR)
                val pitchResult = runCatching { engine!!.setPitch(speechPitch) }.getOrDefault(TextToSpeech.ERROR)
                if (::voicePicker.isInitialized) voicePicker.text = voiceDisplayName(selected, currentIndex)
                if ((rateResult != TextToSpeech.SUCCESS || pitchResult != TextToSpeech.SUCCESS) && ::status.isInitialized) {
                    status.text = activeTtsEngineLabel + " 已应用音色，但引擎不支持全部语音参数。"
                }
            } else {
                if (::voicePicker.isInitialized) voicePicker.text = "音色应用失败，请重新选择"
                if (::status.isInitialized) status.text = activeTtsEngineLabel + " 已连接，但引擎未接受默认音色；请手动选择其他音色。"
            }
        } else if (::voicePicker.isInitialized) {
            voicePicker.text = "当前引擎未提供可选音色"
        }
    }
    private fun releasePlayer() { runCatching { player?.stop() }; runCatching { player?.release() }; player = null }
    override fun onDestroy() {
        destroyed = true; ++ttsGeneration; releasePlayer()
        runCatching {
            nativeExecutor.execute { NativeEngine.unload() }
            nativeExecutor.shutdown()
        }
        assistantExecutor.shutdownNow()
        storageExecutor.shutdownNow()
        runCatching { tts?.stop() }; runCatching { tts?.shutdown() }; tts = null
        super.onDestroy()
    }
    companion object {
        private const val REQUEST_AUDIO = 4102
        private const val REQUEST_ASR_MEDIA = 4103
        private const val REQUEST_EXPORT_TXT = 4104
        private const val REQUEST_EXPORT_SRT = 4112
        private const val REQUEST_EXPORT_VTT = 4113
        private const val REQUEST_STORY_ROLE_A = 4114
        private const val REQUEST_STORY_ROLE_B = 4115
        private const val REQUEST_EXPORT_STORY = 4116
        private const val REQUEST_CLONE_REFERENCE = 4105
        private const val REQUEST_EXPORT_CLONE = 4106
        private const val REQUEST_STEM_MEDIA = 4107
        private const val REQUEST_MIDI_MEDIA = 4108
        private const val REQUEST_EXPORT_MUSIC = 4109
        private const val REQUEST_EXPORT_MIDI = 4110
        private const val REQUEST_EXPORT_GENERIC = 4111
        private const val REQUEST_EXPORT_TTS_SRT = 4117
        private const val REQUEST_MULTITTS_PACKAGE = 4118
    }
}
