# Android 原生推理运行时构建记录

## 固定上游

- 项目：`https://github.com/0xShug0/audio.cpp`
- 固定提交：`a6e32214e762aca6126eb7206a5497f11d064303`
- 接口：上游提供的进程内 C ABI（`include/audiocpp.h`），通过 JNI 供 Kotlin 调用。
- 首轮目标：Android API 26+、`arm64-v8a`、CPU 后端。

## 为什么不直接使用桌面二进制

上游当前公开预编译包清单包括 Windows x64、Ubuntu x64、macOS ARM64/x64，没有 Android ARM64 官方预编译包。桌面 `audiocpp_server` 也不能当作 Android 原生库直接运行。Android 必须用 NDK 从源码交叉编译，并通过 C ABI/JNI 嵌入应用。

## 首轮构建配置

- 使用 Android NDK 27.2.12479018 和 CMake 3.31.6。
- 构建 `libaudiocpp.so`，开启 deployment model specs。
- 关闭 CUDA、HIP、Vulkan、Metal、WebGPU、SYCL、RKNN、OpenMP 和宿主机 native CPU ISA。
- 使用 ARM64 通用 CPU 路径；设备专用 NEON/硬件加速须后续实测再启用。
- 当前阶段只验证原生库能否在 Android 工具链下构建，不代表所有模型家族均已完成手机端推理验证。

## 模型目录

模型清单唯一来源仍为上游 `internal/catalog/catalog.json`。当前清单包含 68 个条目、25 个模型家族，包含单文件和多文件包、版本历史、精度、文件大小、SHA-256、下载仓库及运行时最低版本。Android 端必须读取同一份清单，不能手工维护第二套易过期目录。

下载源 URL 规则应复用上游 `internal/catalog/catalog.go` 的 `FileURL` / `PackageFileURL` 语义：
- 默认模型仓库：audio-cpp/audio.cpp-gguf。
- 自定义仓库模型：按模型条目中的 `repo` / `revision`。
- 支持 Hugging Face、hf-mirror、ModelScope。
- 多文件包必须逐文件下载、逐文件校验；全部成功后原子登记。

## 发布状态

本目录工作流只构建并检查 ARM64 原生推理库，不生成 APK。只有 native build 成功后，才把 JNI 桥接和该库接入 Android app；再进行模拟器/真机加载与真实模型推理测试。
