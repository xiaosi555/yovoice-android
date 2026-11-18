package com.xiaosi555.yovoice

/**
 * Human-readable mapping between Yovoice model families and the Android creation tools.
 * A family being listed here describes its intended task; runtime availability is still
 * determined by NativeEngine.probe() and is shown separately in the model center.
 */
data class CreateFeature(
    val id: String,
    val name: String,
    val symbol: String,
    val shortDescription: String,
    val description: String,
    val purpose: String,
    val modelFamilies: Set<String>,
    val actionLabel: String,
    val runtimeNote: String
)

object ModelFeatureGuide {
    val features = listOf(
        CreateFeature("tts", "文字转语音", "Aa", "将文字合成为语音", "文字转语音与旁白", "输入文字，选择系统 TTS 或已适配的本地语音模型，调整音色、语速和音调并试听、导出音频。", setOf("audio8_tts", "kokoro_tts", "qwen3_tts", "xtts_v2", "omnivoice", "ming3_tts", "breeze_tts", "voxcpm2", "index_tts2", "voxtral_tts", "gradium_phonon", "plapre"), "进入语音合成", "系统 TTS 可独立使用；本地模型需下载、加载，并确认该模型家族已编入 Android 引擎。"),
        CreateFeature("clone", "声音克隆", "◉", "用参考声音生成语音", "参考音频驱动的声音克隆", "导入一段清晰参考录音并填写录音中的准确文本，再输入目标台词。模型会尝试保留参考声音特征；录音质量和模型支持会影响结果。", setOf("audio8_tts", "xtts_v2", "voxcpm2", "index_tts2", "omnivoice"), "进入声音克隆", "需要支持克隆任务的模型、参考音频和转录文本；下载模型不等于克隆能力已在设备验证。"),
        CreateFeature("story", "故事配音", "▤", "多角色分句与配音", "多角色故事配音", "按“角色名：台词”录入脚本，为每个角色提供参考音频和准确转录，逐句生成后合并为完整故事音轨。", setOf("audio8_tts", "xtts_v2", "voxcpm2", "index_tts2", "fish_drama3"), "进入故事配音", "当前 Android 操作流程以 Audio8 克隆任务为基础；其他家族需额外适配后才能用于多角色配音。"),
        CreateFeature("asr", "音频转文字", "≋", "识别音频与视频中的语音", "音频 / 视频语音识别", "选择本地音频或视频文件，提取音轨后进行语音识别；查看转录文本，并导出 TXT、SRT 或 WebVTT 字幕。", setOf("qwen3_asr", "parakeet_tdt", "confucius4_r2t2", "lyric_align"), "进入音频识别", "当前 Android 原生引擎包含 Qwen3 ASR 家族；下载后仍需检查模型文件并在设备上验证。"),
        CreateFeature("music", "AI 音乐生成", "♫", "根据提示词生成音乐", "文本生成音乐与音效", "输入音乐风格、乐器、情绪和时长等描述，生成音乐或音效，再试听并导出 WAV。", setOf("stable_audio", "ace_step", "yue2"), "进入音乐生成", "当前 Android 引擎编入 Stable Audio 家族；ACE-Step、YuE2 等需后续适配，不能仅凭目录存在就视为可运行。"),
        CreateFeature("stems", "音轨分离", "▥", "拆分人声与乐器音轨", "多轨音源分离", "导入混音文件，将歌曲拆分为人声、鼓、贝斯、其他等音轨；可分别试听并导出。", setOf("htdemucs_6stems", "bs_roformer"), "进入音轨分离", "当前 Android 引擎包含 HTDemucs 六轨家族；分离速度和内存占用取决于音频长度及设备资源。"),
        CreateFeature("midi", "音频转 MIDI", "♬", "提取音符并生成 MIDI", "音符识别与 MIDI 制作", "从音乐中识别音符、节奏和乐器事件，生成标准 MIDI；SoundFont 可用于后续音色渲染。", setOf("muscriptor", "sheetsage2", "soundfont"), "进入 MIDI 转换", "当前 Android 引擎包含 MuScriptor 家族；乐器音色库用于 MIDI 渲染，不是音符识别模型。"),
        CreateFeature("assistant", "AI 创作助手", "✦", "润色文案与创作提示词", "AI 文本辅助创作", "通过设置中配置的 OpenAI-compatible 服务，辅助润色、扩写、角色设定、分镜和音乐提示词。", emptySet(), "进入 AI 创作助手", "此功能使用你配置的在线兼容 API，不需要下载本地模型；请妥善保管 API Key。"),
        CreateFeature("audio-import", "音频导入与试听", "♫", "播放手机中的音频文件", "本地音频导入与试听", "从手机文件选择器导入音频，进行本地试听；可作为识别、分离、MIDI 或声音克隆等工具的输入。", emptySet(), "选择本地音频", "这是文件导入和播放工具，不需要单独下载模型。")
    )

    private val purposes = mapOf(
        "audio8_tts" to "语音合成与 Audio8 参考音频克隆；当前 Android 已编入此模型家族。",
        "kokoro_tts" to "轻量级多语言文字转语音，适合快速语音合成；当前 Android 引擎尚未声明支持。",
        "qwen3_tts" to "文字转语音，目录包含 Base、CustomVoice、VoiceDesign 等不同变体。",
        "xtts_v2" to "多语言语音合成及参考声音克隆类任务；需确认对应 Android 后端支持。",
        "omnivoice" to "通用语音合成模型家族；具体音色控制能力取决于模型变体。",
        "ming3_tts" to "文字转语音模型家族。",
        "breeze_tts" to "文字转语音模型家族。",
        "voxcpm2" to "语音生成模型家族，可用于语音合成相关工作流。",
        "index_tts2" to "可控语音合成与声音表现相关模型家族。",
        "voxtral_tts" to "语音合成模型家族，模型体积较大。",
        "qwen3_asr" to "语音识别，将音频中的语音转换为文本；当前 Android 已编入此模型家族。",
        "parakeet_tdt" to "语音识别模型，适用于音频转录。",
        "confucius4_r2t2" to "语音识别 / 转录相关模型家族，需匹配其专用推理接口。",
        "stable_audio" to "根据文字描述生成音乐或音效；当前 Android 已编入此模型家族。",
        "ace_step" to "歌曲与音乐生成模型家族；当前 Android 尚未编入。",
        "yue2" to "歌曲生成模型家族，适用于带人声或歌词的音乐创作方向。",
        "htdemucs_6stems" to "六轨音源分离：人声、鼓、贝斯、其他、吉他和钢琴；当前 Android 已编入。",
        "bs_roformer" to "基于 RoFormer 的音源分离模型，具体分离目标由模型配置决定。",
        "muscriptor" to "从音频识别音符和乐器事件并生成 MIDI；当前 Android 已编入。",
        "sheetsage2" to "音乐理解 / 乐谱相关模型家族，不能与 MIDI 音源渲染混为一谈。",
        "soundfont" to "MIDI 播放和音色渲染所需的 SoundFont 音色库，不负责识别音符。",
        "plapre" to "Plapre 3 文字转语音模型；目录标记任务为 TTS。",
        "gradium_phonon" to "Gradium Phonon 31M 英语语音合成模型，体积较小但当前标注为英语。",
        "lyric_align" to "强制对齐模型：将已有文本 / 歌词与音频对齐，估算词句时间戳，可辅助字幕时间轴校正。",
        "fish_drama3" to "Drama 3 多角色戏剧语音生成模型家族，适合故事 / 戏剧配音方向，模型体积很大。"
    )

    fun modelPurpose(family: String): String =
        purposes[family] ?: "上游模型目录中的 $family 家族；下载前请核对任务用途和 Android 推理后端支持情况。"

    fun featureNames(family: String): String {
        val names = features.filter { family in it.modelFamilies }.map { it.name }
        return names.joinToString("、").ifBlank { "尚未映射到当前创作功能" }
    }
}
