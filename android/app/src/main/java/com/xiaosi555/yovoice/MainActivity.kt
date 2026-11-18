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

/** Mobile studio shell following Yovoice desktop theme tokens and visual hierarchy. */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private var destroyed = false
    @Volatile private var nativeBusy = false
    private val nativeExecutor = Executors.newSingleThreadExecutor()
    private val assistantExecutor = Executors.newSingleThreadExecutor()
    private lateinit var body: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var nav: LinearLayout
    private var page = 0
    private var themeIndex = 0
    private var selectedVoiceName: String? = null
    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private lateinit var voicePicker: Spinner
    private lateinit var enginePicker: TextView
    private lateinit var speedLabel: TextView
    private lateinit var pitchLabel: TextView
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
            3 -> renderSettings()
            4 -> renderProjects()
            else -> renderSettings()
        }
        renderNav()
    }

    private fun renderHeader(title: String, subtitle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = column()
        brand.addView(label(title, 27, p.text, true))
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
        voicePicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("正在读取系统音色…"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val voices = tts?.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
                        .sortedWith(compareBy({ !it.locale.language.equals("zh", true) }, { it.name }))
                    selectedVoiceName = voices.getOrNull(position)?.name
                    selectedVoiceName?.let { name -> voices.firstOrNull { it.name == name }?.let { voice -> runCatching { tts?.voice = voice } } }
                }
            }
        }
        panel.addView(voicePicker, marginTop(4))
        speedLabel = label("语速 1.0×", 13, p.secondary)
        panel.addView(speedLabel, marginTop(10))
        panel.addView(SeekBar(this).apply {
            max = 30
            progress = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    speechRate = (value.coerceAtLeast(1) / 10f).coerceIn(0.1f, 3f)
                    speedLabel.text = "语速 %.1f×".format(speechRate)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        pitchLabel = label("音调 1.0×", 13, p.secondary)
        panel.addView(pitchLabel, marginTop(8))
        panel.addView(SeekBar(this).apply {
            max = 30
            progress = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    speechPitch = (value.coerceAtLeast(1) / 10f).coerceIn(0.1f, 3f)
                    pitchLabel.text = "音调 %.1f×".format(speechPitch)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })

        panel.addView(primaryButton("试听朗读（手机 TTS）") { speak() }, marginTop(10))
        panel.addView(secondaryButton("本地语音 · Audio8 TTS") { speakWithLocalModel() }, marginTop(9))
        panel.addView(secondaryButton("释放本地模型内存") { unloadLocalModel() }, marginTop(8))
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

        val stemPanel = card()
        stemPanel.addView(section("六轨音轨分离", "HTDemucs · 人声、鼓、贝斯、其他、吉他、钢琴"))
        stemPanel.addView(secondaryButton("选择音乐文件") { chooseStemAudio() }, marginTop(10))
        stemStatusView = label("选择 44.1 kHz 音乐文件后开始分离。", 12, p.secondary)
        stemPanel.addView(stemStatusView, marginTop(8))
        stemResultsContainer = column()
        stemPanel.addView(stemResultsContainer, marginTop(8))
        stemPanel.addView(primaryButton("开始六轨分离") { separateAudio() }, marginTop(10))
        body.addView(stemPanel, marginTop(10))

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
        body.addView(infoRow("Android 本地推理引擎", "Audio8 TTS · Qwen3 ASR 0.6B"), marginTop(8))
        body.addView(label("AI 助手服务", 17, p.text, true), marginTop(24))
        val assistantPrefs = getSharedPreferences("yovoice-assistant", MODE_PRIVATE)
        val endpointInput = EditText(this).apply {
            hint = "API 地址，例如 https://api.openai.com/v1"
            setText(assistantPrefs.getString("endpoint", "https://api.openai.com/v1"))
            textSize = 14f; setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.surface, dp(12), p.border, dp(1)); setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        body.addView(endpointInput, marginTop(8))
        val modelInput = EditText(this).apply {
            hint = "模型名称，例如 gpt-4o-mini"
            setText(assistantPrefs.getString("model", "gpt-4o-mini"))
            textSize = 14f; setTextColor(p.text); setHintTextColor(p.secondary)
            background = shape(p.surface, dp(12), p.border, dp(1)); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        body.addView(modelInput, marginTop(8))
        val keyInput = EditText(this).apply {
            hint = "API Key（仅保存在本机应用私有配置中）"
            setText(assistantPrefs.getString("api_key", ""))
            textSize = 14f; setTextColor(p.text); setHintTextColor(p.secondary)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = shape(p.surface, dp(12), p.border, dp(1)); setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        body.addView(keyInput, marginTop(8))
        body.addView(secondaryButton("保存 AI 服务配置") {
            val endpoint = endpointInput.text.toString().trim().trimEnd('/')
            val model = modelInput.text.toString().trim()
            val key = keyInput.text.toString().trim()
            if (!endpoint.startsWith("https://") || model.isBlank() || key.isBlank()) {
                Toast.makeText(this, "请填写 HTTPS 地址、模型名称和 API Key", Toast.LENGTH_LONG).show()
            } else {
                assistantPrefs.edit().putString("endpoint", endpoint).putString("model", model).putString("api_key", key).apply()
                Toast.makeText(this, "AI 服务配置已保存在本机", Toast.LENGTH_SHORT).show()
            }
        }, marginTop(10))
        body.addView(secondaryButton("清除本机保存的 API Key") {
            assistantPrefs.edit().remove("api_key").apply(); keyInput.setText("")
            Toast.makeText(this, "已清除本机 API Key", Toast.LENGTH_SHORT).show()
        }, marginTop(6))
        body.addView(label("主题名称与颜色沿用 Yovoice 桌面版主题体系；移动端针对单手操作重新排布。", 12, p.secondary), marginTop(18))
    }

    private fun requestAssistant(prompt: String, output: TextView) {
        val prefs = getSharedPreferences("yovoice-assistant", MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", "https://api.openai.com/v1").orEmpty().trim().trimEnd('/')
        val model = prefs.getString("model", "gpt-4o-mini").orEmpty().trim()
        val apiKey = prefs.getString("api_key", "").orEmpty().trim()
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

    private fun renderNav() {
        nav.removeAllViews()
        listOf("⌂" to "工作室", "♫" to "创作", "▦" to "模型", "▤" to "项目", "⚙" to "设置").forEachIndexed { index, pair ->
            val targetPage = if (index == 4) 3 else if (index == 3) 4 else index
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { page = targetPage; render() } }
            item.addView(LineIconView(this, arrayOf("home", "wave", "grid", "folder", "settings")[index], if (page == targetPage) p.accent else p.secondary), LinearLayout.LayoutParams(dp(24), dp(24)))
            item.addView(label(pair.second, 11, if (page == targetPage) p.accent else p.secondary, page == targetPage).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0) })
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
        if (model == null) {
            Toast.makeText(this, "模型目录中没有 Audio8 TTS 条目", Toast.LENGTH_LONG).show()
            page = 2
            render()
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
                val loaded = JSONObject(NativeEngine.loadModel(modelFile.absolutePath, model.family, 2))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "模型加载失败") }

                val projectDir = File(filesDir, "projects")
                if (!projectDir.exists() && !projectDir.mkdirs()) error("无法创建项目目录")
                val output = File(projectDir, "audio8-preview-" + System.currentTimeMillis() + ".wav")
                runOnUiThread { if (!destroyed && ::status.isInitialized) status.text = "模型已加载，正在合成中文语音…" }
                val result = JSONObject(NativeEngine.synthesize(text, "zh", output.absolutePath, speechRate, speechPitch))
                check(result.optBoolean("ok")) { result.optString("error", "语音合成失败") }
                check(output.isFile && output.length() > 44L) { "合成结果文件无效" }
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
            runOnUiThread {
                if (!destroyed && !isFinishing) {
                    if (::status.isInitialized) status.text = "本地模型已释放；再次使用时会重新加载"
                    Toast.makeText(this, "本地模型已释放", Toast.LENGTH_SHORT).show()
                }
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
                        if (tts == null) {
                            Toast.makeText(this, "无法启动所选语音引擎", Toast.LENGTH_LONG).show()
                        } else if (::enginePicker.isInitialized) {
                            enginePicker.text = "${labels[which]}   ›"
                        }
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
        runCatching { engine.setSpeechRate(speechRate); engine.setPitch(speechPitch) }
        selectedVoiceName?.let { name -> engine.voices?.firstOrNull { it.name == name }?.let { voice -> runCatching { engine.voice = voice } } }
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
        if (resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            when (requestCode) {
                REQUEST_AUDIO -> playAudio(uri)
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
        val model = runCatching { ModelCatalog.load(this).firstOrNull { it.id == "qwen3-asr-0.6b-q8" } }.getOrNull()
        if (model == null) {
            Toast.makeText(this, "模型目录中没有 Qwen3 ASR 0.6B 条目", Toast.LENGTH_LONG).show()
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
                    modelFile.absolutePath, model.family, "asr", "offline", auxiliaryPath = alignerFile.absolutePath
                ))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "ASR 模型加载失败") }
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
        val model = runCatching { ModelCatalog.load(this).firstOrNull { it.id == "audio8-tts-0.6b-q8" } }.getOrNull()
        if (model == null) {
            Toast.makeText(this, "模型目录中没有 Audio8 TTS 模型", Toast.LENGTH_LONG).show()
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
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "clon", "offline"))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "声音克隆模型加载失败") }
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
        val model = ModelCatalog.load(this).firstOrNull { it.id == "stable-audio-3-small-music-q8" }
        if (model == null) {
            Toast.makeText(this, "模型目录中没有 Stable Audio 音乐模型", Toast.LENGTH_LONG).show()
            return
        }
        nativeBusy = true
        musicStatusView?.text = "正在校验 Stable Audio 模型…"
        nativeExecutor.execute {
            try {
                check(ModelCatalog.isInstalled(this, model)) { "请先下载 Stable Audio 3 Small Music Q8 模型（约 1.68 GB）" }
                val modelFile = File(ModelCatalog.installedDirectory(this, model.id), model.localRelativePath(model.remotePath))
                check(modelFile.isFile) { "音乐模型文件不存在，请重新下载" }
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "gen", "offline"))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "音乐模型加载失败") }
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
        val model = ModelCatalog.load(this).firstOrNull { it.id == "htdemucs-6stems-q8" }
        if (model == null) {
            Toast.makeText(this, "模型目录中没有 HTDemucs 六轨模型", Toast.LENGTH_LONG).show()
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
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "sep", "offline"))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "音轨分离模型加载失败") }
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
        val model = ModelCatalog.load(this).firstOrNull { it.id == "muscriptor-small-f32" }
        if (model == null) {
            Toast.makeText(this, "模型目录中没有 MuScriptor 模型", Toast.LENGTH_LONG).show()
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
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "midi", "offline"))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "MIDI 模型加载失败") }
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
        val model = ModelCatalog.load(this).firstOrNull { it.id == "audio8-tts-0.6b-q8" }
        if (model == null) {
            Toast.makeText(this, "模型目录中没有 Audio8 TTS 模型", Toast.LENGTH_LONG).show()
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
                val loaded = JSONObject(NativeEngine.loadTaskModel(modelFile.absolutePath, model.family, "clon", "offline"))
                check(loaded.optBoolean("ok")) { loaded.optString("error", "故事配音模型加载失败") }
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
            if (::enginePicker.isInitialized && tts != null) {
                val activeName = tts?.defaultEngine
                val activeLabel = tts?.engines.orEmpty().firstOrNull { it.name == activeName }?.label?.toString()
                enginePicker.text = "${activeLabel?.takeIf(String::isNotBlank) ?: activeName ?: "手机系统语音引擎"}   ›"
            }
            if (ttsReady && ::voicePicker.isInitialized) refreshSystemVoices()
        }
    }
    private fun refreshSystemVoices() {
        val voices = runCatching { tts?.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ !it.locale.language.equals("zh", true) }, { it.name })) }.getOrDefault(emptyList())
        val names = voices.map { voice ->
            val language = voice.locale.getDisplayLanguage(Locale.SIMPLIFIED_CHINESE)
            val country = voice.locale.getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
            val quality = if (voice.quality >= android.speech.tts.Voice.QUALITY_HIGH) "高品质" else "标准"
            "${voice.name} · $language $country · $quality"
        }.ifEmpty { listOf("当前引擎未提供可选音色") }
        val currentIndex = voices.indexOfFirst { it.name == selectedVoiceName }.coerceAtLeast(0)
        voicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        if (voices.isNotEmpty()) {
            selectedVoiceName = voices[currentIndex].name
            voicePicker.setSelection(currentIndex)
        }
    }
    private fun releasePlayer() { runCatching { player?.stop() }; runCatching { player?.release() }; player = null }
    override fun onDestroy() {
        destroyed = true; releasePlayer()
        runCatching {
            nativeExecutor.execute { NativeEngine.unload() }
            nativeExecutor.shutdown()
        }
        assistantExecutor.shutdownNow()
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
    }
}
