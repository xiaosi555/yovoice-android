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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Yovoice Android 迁移宿主。
 *
 * 注意：先创建界面，再初始化异步 TTS 服务，避免 onInit 回调访问尚未初始化的 View。
 * 当前仅提供系统 TTS 和本地音频试听；AI 模型运行时尚未接入。
 */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private lateinit var speechInput: EditText
    private lateinit var status: TextView
    private var activityDestroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 必须先完成所有 View 初始化，之后才能启动可能异步回调的 TTS。
        buildScreen()
        status.text = "正在初始化手机系统语音引擎…"
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (error: Exception) {
            status.text = "系统 TTS 启动失败：${error.localizedMessage ?: "未知错误"}"
        }
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(0xFFF6F7F9.toInt())
        }

        content.addView(TextView(this).apply {
            text = "Yovoice"
            textSize = 30f
            setTextColor(0xFF20242B.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }, matchWrap())

        content.addView(TextView(this).apply {
            text = "本地语音工作室 · Android 适配版"
            textSize = 14f
            setTextColor(0xFF68717D.toInt())
            setPadding(0, dp(5), 0, dp(20))
        }, matchWrap())

        val speechPanel = panel()
        speechPanel.addView(sectionTitle("文字转语音"), matchWrap())
        speechInput = EditText(this).apply {
            hint = "输入要朗读的文字"
            textSize = 16f
            minLines = 4
            gravity = Gravity.TOP
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        speechPanel.addView(speechInput, matchWrapWithTop(12))
        speechPanel.addView(Button(this).apply {
            text = "使用手机系统音色试听"
            setOnClickListener { speakText() }
        }, matchWrapWithTop(10))
        content.addView(speechPanel, matchWrapWithTop(12))

        val audioPanel = panel()
        audioPanel.addView(sectionTitle("本地音频"), matchWrap())
        audioPanel.addView(Button(this).apply {
            text = "导入音频文件"
            setOnClickListener { chooseAudio() }
        }, matchWrapWithTop(8))
        audioPanel.addView(Button(this).apply {
            text = "播放 / 继续"
            setOnClickListener {
                val current = player
                if (current != null) {
                    runCatching { current.start() }.onFailure {
                        Toast.makeText(this@MainActivity, "音频无法继续播放", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "请先导入音频文件", Toast.LENGTH_SHORT).show()
                }
            }
        }, matchWrap())
        audioPanel.addView(Button(this).apply {
            text = "暂停播放"
            setOnClickListener { runCatching { player?.pause() } }
        }, matchWrap())
        content.addView(audioPanel, matchWrapWithTop(12))

        status = TextView(this).apply {
            text = "正在准备语音服务…"
            textSize = 13f
            setTextColor(0xFF68717D.toInt())
            setPadding(2, dp(16), 2, 0)
        }
        content.addView(status, matchWrap())

        content.addView(TextView(this).apply {
            text = "当前为 Android 迁移开发版。离线模型、声音克隆、ASR、音乐与音轨引擎尚未接入，不代表完整桌面功能。"
            textSize = 12f
            setTextColor(0xFF818A96.toInt())
            setPadding(2, dp(12), 2, 0)
        }, matchWrap())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(scroll)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(0xFFFFFFFF.toInt())
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        setTextColor(0xFF20242B.toInt())
    }

    private fun speakText() {
        val text = speechInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "请先输入要朗读的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val engine = tts
        if (!ttsReady || engine == null) {
            Toast.makeText(this, "系统语音引擎尚未准备好", Toast.LENGTH_SHORT).show()
            return
        }
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yovoice-preview")
        }.getOrElse {
            Toast.makeText(this, "朗读失败：${it.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
            return
        }
        if (result == TextToSpeech.ERROR) {
            Toast.makeText(this, "系统语音服务拒绝了朗读请求", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        runCatching { startActivityForResult(intent, REQUEST_AUDIO) }
            .onFailure {
                Toast.makeText(this, "无法打开系统文件选择器", Toast.LENGTH_LONG).show()
            }
    }

    @Deprecated("使用系统文件选择器回调")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_AUDIO || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // 不支持持久授权的文档提供方仍可在当前会话中读取。
        }
        playAudio(uri)
    }

    private fun playAudio(uri: Uri) {
        releasePlayer()
        try {
            player = MediaPlayer().apply {
                setOnPreparedListener { media ->
                    if (!activityDestroyed) media.start() else media.release()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@MainActivity, "音频无法播放或格式暂不支持", Toast.LENGTH_LONG).show()
                    true
                }
                setDataSource(this@MainActivity, uri)
                prepareAsync()
            }
            Toast.makeText(this, "已载入音频，正在准备播放", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            releasePlayer()
            Toast.makeText(
                this,
                "打开音频失败：${error.localizedMessage ?: "文件不可读取"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onInit(statusCode: Int) {
        runOnUiThread {
            if (activityDestroyed || isFinishing || isDestroyed) return@runOnUiThread
            ttsReady = statusCode == TextToSpeech.SUCCESS
            if (!ttsReady) {
                status.text = "系统 TTS 初始化失败，请检查手机是否安装并启用了语音服务。"
                return@runOnUiThread
            }
            val languageResult = runCatching {
                tts?.setLanguage(Locale.getDefault()) ?: TextToSpeech.ERROR
            }.getOrDefault(TextToSpeech.ERROR)
            status.text = when (languageResult) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED ->
                    "系统语音已启动，但当前语言缺少语音数据；请在手机系统设置中安装对应 TTS 语言包。"
                TextToSpeech.ERROR ->
                    "系统语音已启动，但语言初始化失败；可在系统设置中更换 TTS 引擎或语言。"
                else -> "系统 TTS 已就绪，可直接试听。"
            }
        }
    }

    private fun releasePlayer() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    override fun onDestroy() {
        activityDestroyed = true
        ttsReady = false
        releasePlayer()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
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
