# Yovoice Android TTS 引擎接入与恢复技术方案

状态：P0 功能恢复基线  
目标：恢复“发现引擎 → 选择引擎 → 初始化 → 读取音色 → 调节参数 → 试听/合成 → 失败回退”闭环。  
原则：系统 TTS、第三方 TTS 服务和 Audio8 本地推理是不同引擎类型，统一入口但不能混为一个实现。

## 1. 当前代码审查结论

当前 MainActivity 的语音引擎流程有缺口：
- initializeSystemTts() 只创建 Android 默认 TextToSpeech 实例。
- showTtsOptions() 依赖 tts?.engines 获取引擎列表，缺少独立设备服务扫描和错误状态。
- 切换引擎时直接 shutdown 当前实例并创建新实例，缺少可靠状态机、选择持久化和音色刷新时序。
- TTS 页面把系统 TTS 与 Audio8 分成两个按钮，用户无法在统一入口选择实际引擎。
- MultiTTS、CloneTTS Engine 没有显式适配层。若设备实现注册为 Android TTS Service，理论上可通过系统服务发现，但当前代码没有专门验证其服务和初始化结果。
- Audio8 是独立 JNI 工作流：检查模型安装、读取 yovoice-model.json、加载 native model、调用 synthesize、生成 WAV、播放并保存项目。它不是 Android TextToSpeech provider，不能直接出现在系统 engines 列表中。

因此当前 APK 不能宣称已恢复此前的多引擎本地 TTS 体验。

## 2. 引擎类型与能力模型

统一引擎选择器，内部使用不同 adapter。

### A. Android 系统 TTS Provider
包括手机自带引擎，以及设备上安装并注册为 Android TextToSpeech.Engine 服务的第三方引擎。
- 通过 PackageManager 查询 android.intent.action.TTS_SERVICE。
- 使用真实 package/engine name 初始化 TextToSpeech(context, listener, engineName)。
- 初始化成功后读取 voices、availableLanguages、defaultEngine 等能力。
- 服务可用性、中文音色、联网需求均以设备返回数据为准。

### B. MultiTTS
- 若 MultiTTS 注册为标准 Android TTS provider：走系统 provider adapter，显示为独立引擎选项。
- 若音色包/模型文件需要用户导入：走独立 MultiTTS package adapter，不得把文件路径假装成 TTS engine package。
- 导入使用 Storage Access Framework 并持久化 URI 权限；记录 URI、文件名、大小、修改时间或可验证摘要。
- 首次导入或资源变化时重新扫描/索引；同一资源未变化时复用索引。
- 资源失效或权限丢失时显示需要重新选择/授权，不得静默切换其他引擎。

### C. CloneTTS Engine
- 若设备安装并注册为 Android TTS provider，则使用 provider adapter 初始化。
- 若需独立服务协议或专属音色包，建立单独 adapter，检测服务包、版本、接口能力和模型资源。
- 不得仅凭应用名称推断可用；以服务发现和真实初始化结果为准。

### D. Audio8 本地 TTS
- 作为应用内 Native adapter，不进入 Android TextToSpeech.getEngines() 列表。
- 依赖已下载并校验的 Audio8 模型、已编译的 audio8_tts native family 和设备可用内存。
- 完成 load → synthesize → WAV validation → playback → project save 闭环。
- 加载、合成、释放在单一推理执行器内串行化。
- 避免每次页面重绘重复加载；记录 loaded model id/path 与 native state。
- 下载成功不代表加载成功；加载成功不代表合成任务成功。

## 3. 统一引擎注册与选择状态机

每个引擎记录：
- id：稳定标识，例如 system:<package>、multitts:<package>、clone:<package>、native:audio8。
- kind：android_provider / imported_package / native_runtime。
- displayName、packageName、version。
- availability：available / not_installed / missing_permission / unsupported / initializing / failed。
- capabilities：voice list、language list、network requirement、rate、pitch、sample rate、offline flag。
- lastError：最近一次真实错误。

状态流：
DISCOVERING → READY_LIST → INITIALIZING(selected) → ENGINE_READY → VOICES_READY → SPEAKING。

错误状态：
DISCOVERY_FAILED、INIT_FAILED、VOICE_LOAD_FAILED、SPEAK_FAILED、RESOURCE_MISSING。

每次切换引擎：
1. 停止旧引擎当前朗读。
2. 安全释放旧 provider/native 资源；不能在任务执行中卸载。
3. 初始化用户明确选择的新引擎。
4. 等待 onInit 成功回调。
5. 读取当前引擎音色和语言能力。
6. 恢复按引擎分别保存的音色/语速/音调，仅应用引擎支持的能力。
7. 更新 UI；失败时保留用户选择并显示原因，允许用户主动选择其他引擎。

## 4. 用户操作流程

1. 打开文字转语音创作页。
2. 引擎行显示当前引擎和真实状态。
3. 点击引擎行，弹出统一引擎列表：系统默认引擎、已安装 Android TTS provider（包括检测到的 MultiTTS/CloneTTS）、Audio8 本地引擎。
4. 只有真实检测到且可初始化的 provider 才能作为可选项。Audio8 显示模型下载、校验和 runtime 状态。
5. 选择后等待初始化；不能在点击时立即显示切换成功。
6. 初始化完成后展示当前引擎实际支持的音色。
7. 用户选音色、语速、音调，点击试听。
8. 成功后可生成、保存、播放和导出。
9. 失败时展示真实错误和重试入口；不得无提示自动切换。

## 5. 设置与持久化

独立保存：
- selected_tts_engine_id
- selected_voice_id（按 engine id 分开保存）
- speech_rate、speech_pitch
- MultiTTS 导入资源的持久 URI、元信息、索引版本
- Audio8 最近成功加载的模型 id

重启后按保存的 engine id 重新发现并初始化。若引擎已卸载或 URI 无效，提示重新选择；不要悄悄回退并覆盖用户偏好。

## 6. 音色、语速、音调与语言

- 音色列表必须来自当前已初始化引擎，不得使用假数据或固定写死列表。
- 切换引擎后清空旧音色并加载新列表，防止 voice id 串用。
- 网络音色、离线音色、语言和地区标记必须来自真实能力。
- 语速和音调按引擎能力启用；Audio8 若返回 styleControlsApplied=false，明确说明模型未应用参数。
- 中文能力先检查 zh-CN / zh，再按引擎返回能力选择回退语言。
- 试听前检查文本非空、引擎 ready、目标语言可用。

## 7. 生命周期与资源管理

- TTS provider 初始化/关闭不能跟随 render() 或页面切换重复执行。
- TextToSpeech 回调校验当前 engine generation/token，忽略过期回调。
- 页面重建保留引擎实例和草稿；Activity 销毁时安全停止并释放。
- Audio8 模型只加载一次，连续任务复用；显式释放或低内存时卸载。
- 本地推理和 TTS provider 不共用一个“已就绪”布尔值。
- 文件 URI 读取处理持久权限、文件不存在和读取失败。

## 8. 测试矩阵

自动化/模拟：
- 仅系统 TTS；多个 provider 并存。
- MultiTTS provider 存在、缺失、初始化失败。
- CloneTTS provider 存在、缺失、初始化失败。
- 选择引擎后进程重启，恢复选择。
- 快速连续切换时旧回调不覆盖新引擎。
- 音色为空、中文缺失、网络音色不可用。
- Audio8 模型未下载、未校验、runtime 不支持、load 失败、合成失败和成功输出。
- MultiTTS URI 权限撤销、资源变化、重复打开。

真机：
- 逐个发现设备实际安装的系统 TTS / MultiTTS / CloneTTS。
- 每个引擎真实初始化成功并完成中文试听。
- 语速/音调实际生效或明确禁用。
- Audio8 下载、校验、加载、合成、播放、保存闭环。
- 切换页面、锁屏、返回后引擎状态一致。

## 9. 实施顺序

P0：恢复引擎发现、选择、初始化、音色刷新、持久化和中文试听。  
P1：把 Audio8 纳入统一引擎选择器，保留 native 专属加载和合成路径。  
P2：完成 MultiTTS / CloneTTS 设备发现与 provider adapter；若资源协议不是标准 Android TTS，按真实 SDK/接口接入，不猜测私有接口。  
P3：将引擎选择器嵌入新 TTS 创作页，并统一 UI 状态与弹窗。  
P4：自动化与真机验收后再发布 APK。

## 10. 不可接受的回归

- 只剩系统 TTS，原本支持的本地/第三方引擎入口消失。
- 只显示 Audio8 下载按钮，却不能选择、加载和合成。
- 只显示模型已下载就宣称可使用。
- 切换引擎后音色仍显示上一个引擎列表。
- 页面切换导致 TTS 重置、草稿丢失或模型重复加载。
- 失败时静默回退到系统引擎。
