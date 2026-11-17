# Changelog

各版本的重要更新记录。日常改动记入 `Unreleased`；发布前合并重复项，整理为简短的功能、改进和修复说明。

## [Unreleased]

### 简体中文
- 修复 Android 原型中系统 TTS 初始化与界面状态控件之间的时序风险，并为音频播放和 Activity 生命周期增加异常保护。
- 暂停 Android APK 自动打包；改为手动验证门禁，待 Android 原生推理运行时、模型下载/校验/加载和端到端真机测试完成后再构建交付。
- 明确 Android 模型中心需复用原版模型目录、版本和 SHA-256 校验；模型权重按需下载到手机，不打包进 APK。

### English

- Fix the Android prototype's TTS initialization ordering and add lifecycle and audio playback error handling.
- Gate APK packaging behind manual validation until the Android inference runtime, model download/verification/loading, and end-to-end device tests are complete.
- Define on-device model management using the upstream catalog, version metadata and SHA-256 verification; model weights are downloaded on demand rather than bundled in the APK.

