package com.xiaosi555.yovoice

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Yovoice Android 首阶段宿主。
 * 当前提供系统 TTS 与本地音频导入试听，后续逐步接入离线推理引擎。
 */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private lateinit var speechInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(0xFFF6F7F9.toInt())
        }

        val title = TextView(this).apply {
            text = "Yovoice"
            textSize = 30f
            setTextColor(0xFF20242B.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "本地语音工作室 · Android 适配版"
            textSize = 14f
            setTextColor(0xFF68717D.toInt())
            setPadding(0, dp(5), 0, dp(20))
        }
        root.addView(subtitle, matchWrap())

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val section = TextView(this).apply {
            text = "文字转语音"
            textSize = 18f
            setTextColor(0xFF20242B.toInt())
        }
        panel.addView(section, matchWrap())

        speechInput = EditText(this).apply {
            hint = "输入要朗读的文字"
            textSize = 16f
            minLines = 4
            gravity = Gravity.TOP
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val inputParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
        panel.addView(speechInput, inputParams)

        val speakButton = Button(this).apply {
            text = "使用手机系统音色试听"
            setOnClickListener { speakText() }
        }
        panel.addView(speakButton, matchWrapWithTop(10))
        root.addView(panel, matchWrapWithTop(12))

        val audioPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        audioPanel.addView(TextView(this).apply {
            text = "本地音频"
            textSize = 18f
            setTextColor(0xFF20242B.toInt())
        }, matchWrap())
        audioPanel.addView(Button(this).apply {
            text = "导入音频文件"
            setOnClickListener { chooseAudio() }
        }, matchWrapWithTop(8))
        audioPanel.addView(Button(this).apply {
            text = "播放 / 继续"
            setOnClickListener {
                if (player != null) player?.start()
                else Toast.makeText(this@MainActivity, "请先导入音频文件", Toast.LENGTH_SHORT).show()
            }
        }, matchWrap())
        audioPanel.addView(Button(this).apply {
            text = "暂停播放"
            setOnClickListener { player?.pause() }
        }, matchWrap())
        root.addView(audioPanel, matchWrapWithTop(12))

        status = TextView(this).apply {
            text = "正在初始化手机系统语音引擎…"
            textSize = 13f
            setTextColor(0xFF68717D.toInt())
            setPadding(2, dp(16), 2, 0)
        }
        root.addView(status, matchWrap())
        val footer = TextView(this).apply {
            text = "当前为 Android 移植基础版。离线模型、声音克隆、ASR 与音乐/音轨引擎正在分阶段适配，尚未接入。"
            textSize = 12f
            setTextColor(0xFF818A96.toInt())
            setPadding(2, dp(12), 2, 0)
        }
        root.addView(footer, matchWrap())
        setContentView(root)
    }

    private fun speakText() {
        val text = speechInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "请先输入要朗读的文字", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ttsReady) {
            Toast.makeText(this, "系统语音引擎尚未准备好", Toast.LENGTH_SHORT).show()
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yovoice-preview")
    }

    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, REQUEST_AUDIO)
    }

    @Deprecated("使用系统文件选择器回调")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUDIO && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // 部分文件提供方不支持持久授权，当前会话仍可读取。
            }
            playAudio(uri)
        }
    }

    private fun playAudio(uri: Uri) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@MainActivity, "音频无法播放或格式暂不支持", Toast.LENGTH_LONG).show()
                true
            }
            prepareAsync()
        }
        Toast.makeText(this, "已载入音频，正在准备播放", Toast.LENGTH_SHORT).show()
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) {
            val result = tts?.setLanguage(Locale.getDefault())
            status.text = if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                "系统语音已启动，但当前语言缺少语音数据；请在手机系统设置中安装对应 TTS 语言包。"
            } else {
                "系统 TTS 已就绪，可直接试听。"
            }
        } else {
            status.text = "系统 TTS 初始化失败，请检查手机是否安装语音服务。"
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun matchWrapWithTop(top: Int) = matchWrap().apply { topMargin = dp(top) }

    companion object {
        private const val REQUEST_AUDIO = 4102
    }
}
