#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <limits>
#include <cmath>
#include <mutex>
#include <sstream>
#include <string>

#include "audiocpp.h"

namespace {
class UtfChars {
public:
    UtfChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        if (value_ != nullptr) chars_ = env_->GetStringUTFChars(value_, nullptr);
    }
    ~UtfChars() {
        if (chars_ != nullptr) env_->ReleaseStringUTFChars(value_, chars_);
    }
    const char* get() const { return chars_ == nullptr ? "" : chars_; }
private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_ = nullptr;
};

struct RuntimeState {
    audiocpp_registry* registry = nullptr;
    audiocpp_model* model = nullptr;
    audiocpp_session* session = nullptr;
    std::string model_path;
    std::string family;
    std::string task;
    std::string mode;
};

std::mutex g_mutex;
RuntimeState g_state;

std::string json_escape(const std::string& input) {
    std::ostringstream out;
    for (const unsigned char c : input) {
        switch (c) {
            case '\\': out << "\\\\"; break;
            case '"': out << "\\\""; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (c < 0x20) out << '?';
                else out << static_cast<char>(c);
        }
    }
    return out.str();
}

jstring make_string(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string status_message(audiocpp_status status) {
    const char* detail = audiocpp_last_error();
    if (detail != nullptr && detail[0] != '\0') return detail;
    const char* label = audiocpp_status_string(status);
    return label == nullptr ? "audio.cpp operation failed" : label;
}

void release_state() {
    if (g_state.session != nullptr) audiocpp_session_free(g_state.session);
    if (g_state.model != nullptr) audiocpp_model_free(g_state.model);
    if (g_state.registry != nullptr) audiocpp_registry_free(g_state.registry);
    g_state = RuntimeState{};
}

std::string failure_json(const std::string& message) {
    return "{\"ok\":false,\"error\":\"" + json_escape(message) + "\"}";
}

void write_u16(std::ofstream& stream, uint16_t value) {
    const char bytes[2] = {
        static_cast<char>(value & 0xff),
        static_cast<char>((value >> 8) & 0xff)
    };
    stream.write(bytes, sizeof(bytes));
}

void write_u32(std::ofstream& stream, uint32_t value) {
    const char bytes[4] = {
        static_cast<char>(value & 0xff),
        static_cast<char>((value >> 8) & 0xff),
        static_cast<char>((value >> 16) & 0xff),
        static_cast<char>((value >> 24) & 0xff)
    };
    stream.write(bytes, sizeof(bytes));
}

bool write_wav(
    const std::string& path,
    const float* samples,
    size_t frames,
    int sample_rate,
    int channels,
    std::string* error
) {
    if (samples == nullptr || frames == 0 || sample_rate <= 0 || channels <= 0) {
        *error = "模型没有返回有效音频";
        return false;
    }
    const uint64_t sample_count = static_cast<uint64_t>(frames) * static_cast<uint64_t>(channels);
    const uint64_t data_bytes = sample_count * sizeof(int16_t);
    if (data_bytes > std::numeric_limits<uint32_t>::max() - 36U) {
        *error = "生成的音频文件过大";
        return false;
    }

    std::ofstream stream(path, std::ios::binary | std::ios::trunc);
    if (!stream) {
        *error = "无法创建 WAV 输出文件";
        return false;
    }
    const uint32_t data_size = static_cast<uint32_t>(data_bytes);
    stream.write("RIFF", 4);
    write_u32(stream, 36U + data_size);
    stream.write("WAVE", 4);
    stream.write("fmt ", 4);
    write_u32(stream, 16U);
    write_u16(stream, 1U);
    write_u16(stream, static_cast<uint16_t>(channels));
    write_u32(stream, static_cast<uint32_t>(sample_rate));
    write_u32(stream, static_cast<uint32_t>(sample_rate * channels * 2));
    write_u16(stream, static_cast<uint16_t>(channels * 2));
    write_u16(stream, 16U);
    stream.write("data", 4);
    write_u32(stream, data_size);

    for (uint64_t i = 0; i < sample_count; ++i) {
        const float bounded = std::max(-1.0f, std::min(1.0f, samples[i]));
        const auto value = static_cast<int16_t>(std::lrint(bounded * 32767.0f));
        write_u16(stream, static_cast<uint16_t>(value));
    }
    stream.flush();
    if (!stream.good()) {
        *error = "写入 WAV 文件失败";
        return false;
    }
    return true;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_xiaosi555_yovoice_NativeEngine_nativeProbe(JNIEnv* env, jobject) {
    std::ostringstream json;
    json << "{\"abi\":" << audiocpp_abi_version()
         << ",\"version\":\"" << json_escape(audiocpp_build_version() == nullptr ? "" : audiocpp_build_version()) << "\"";

    audiocpp_registry* registry = nullptr;
    const audiocpp_status status = audiocpp_registry_create(nullptr, &registry);
    if (status != AUDIOCPP_OK) {
        json << ",\"status\":\"error\",\"message\":\""
             << json_escape(status_message(status)) << "\"}";
        return make_string(env, json.str());
    }

    json << ",\"status\":\"ready\",\"families\":[";
    const size_t family_count = audiocpp_registry_family_count(registry);
    bool first = true;
    for (size_t i = 0; i < family_count; ++i) {
        const char* family = nullptr;
        if (audiocpp_registry_family(registry, i, &family) == AUDIOCPP_OK && family != nullptr) {
            if (!first) json << ",";
            json << "\"" << json_escape(family) << "\"";
            first = false;
        }
    }
    json << "],\"tasks\":[";
    const size_t task_count = audiocpp_task_count();
    first = true;
    for (size_t i = 0; i < task_count; ++i) {
        const char* task = audiocpp_task_name(i);
        if (task != nullptr) {
            if (!first) json << ",";
            json << "\"" << json_escape(task) << "\"";
            first = false;
        }
    }
    json << "]}";
    audiocpp_registry_free(registry);
    return make_string(env, json.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xiaosi555_yovoice_NativeEngine_nativeLoadModel(
    JNIEnv* env, jobject, jstring model_path, jstring family, jstring task, jstring mode, jint threads
) {
    UtfChars path(env, model_path);
    UtfChars family_name(env, family);
    UtfChars task_name(env, task);
    UtfChars mode_name(env, mode);
    const std::string task_value = task_name.get()[0] == '\0' ? "tts" : task_name.get();
    const std::string mode_value = mode_name.get()[0] == '\0' ? "offline" : mode_name.get();
    if (path.get()[0] == '\0' || family_name.get()[0] == '\0') {
        return make_string(env, failure_json("模型路径或模型家族为空"));
    }
    if ((task_value != "tts" && task_value != "asr") || mode_value != "offline") {
        return make_string(env, failure_json("当前 Android 原生运行时仅开放离线 TTS 与 ASR"));
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_state.session != nullptr &&
        g_state.model_path == path.get() &&
        g_state.family == family_name.get() &&
        g_state.task == task_value && g_state.mode == mode_value) {
        return make_string(env, "{\"ok\":true,\"alreadyLoaded\":true}");
    }
    release_state();

    audiocpp_status status = audiocpp_registry_create(nullptr, &g_state.registry);
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        release_state();
        return make_string(env, failure_json(message));
    }

    const audiocpp_model_config model_config{
        family_name.get(), nullptr, nullptr, nullptr
    };
    status = audiocpp_model_load(
        g_state.registry, path.get(), &model_config, nullptr, &g_state.model
    );
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        release_state();
        return make_string(env, failure_json(message));
    }

    if (!audiocpp_model_supports(g_state.model, task_value.c_str(), mode_value.c_str())) {
        release_state();
        return make_string(env, failure_json("该模型不支持所选任务或运行模式"));
    }

    audiocpp_backend_config backend{
        "cpu", 0, std::max(1, std::min(threads, 8))
    };
    audiocpp_options* options = audiocpp_options_create();
    if (options != nullptr) {
        audiocpp_options_set(options, "mem_saver", "true");
    }
    status = audiocpp_session_create(
        g_state.model, task_value.c_str(), mode_value.c_str(), &backend, options, &g_state.session
    );
    if (options != nullptr) audiocpp_options_free(options);
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        release_state();
        return make_string(env, failure_json(message));
    }

    g_state.model_path = path.get();
    g_state.family = family_name.get();
    g_state.task = task_value;
    g_state.mode = mode_value;
    return make_string(env, "{\"ok\":true,\"alreadyLoaded\":false}");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xiaosi555_yovoice_NativeEngine_nativeSynthesize(
    JNIEnv* env, jobject, jstring text, jstring language, jstring output_path,
    jfloat speaking_rate, jfloat pitch_multiplier
) {
    UtfChars input_text(env, text);
    UtfChars language_name(env, language);
    UtfChars output(env, output_path);
    if (input_text.get()[0] == '\0' || output.get()[0] == '\0') {
        return make_string(env, failure_json("朗读文字或输出路径为空"));
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_state.session == nullptr || g_state.task != "tts") {
        return make_string(env, failure_json("尚未加载 TTS 语音模型"));
    }

    if (!std::isfinite(speaking_rate) || speaking_rate <= 0.0f ||
        !std::isfinite(pitch_multiplier) || pitch_multiplier <= 0.0f) {
        return make_string(env, failure_json("语速或音调参数无效"));
    }
    audiocpp_request* request = audiocpp_request_create();
    if (request == nullptr) return make_string(env, failure_json("无法创建推理请求"));
    const char* language_value = language_name.get()[0] == '\0' ? nullptr : language_name.get();
    audiocpp_status status = audiocpp_request_set_text(request, input_text.get(), language_value);
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        audiocpp_request_free(request);
        return make_string(env, failure_json(message));
    }
    const bool style_controls_applied = audiocpp_model_supports_style_condition(g_state.model) != 0;
    if (style_controls_applied) {
        status = audiocpp_request_set_speaking_rate(request, speaking_rate);
        if (status == AUDIOCPP_OK) {
            const float pitch_semitones = 12.0f * std::log2(pitch_multiplier);
            status = audiocpp_request_set_pitch_shift(request, pitch_semitones);
        }
        if (status != AUDIOCPP_OK) {
            const std::string message = status_message(status);
            audiocpp_request_free(request);
            return make_string(env, failure_json(message));
        }
    }
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        audiocpp_request_free(request);
        return make_string(env, failure_json(message));
    }

    audiocpp_result* result = nullptr;
    status = audiocpp_session_run(g_state.session, request, &result);
    audiocpp_request_free(request);
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        if (result != nullptr) audiocpp_result_free(result);
        return make_string(env, failure_json(message));
    }

    const float* samples = nullptr;
    size_t frames = 0;
    int sample_rate = 0;
    int channels = 0;
    status = audiocpp_result_audio(result, &samples, &frames, &sample_rate, &channels);
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        audiocpp_result_free(result);
        return make_string(env, failure_json(message));
    }

    std::string write_error;
    const bool written = write_wav(output.get(), samples, frames, sample_rate, channels, &write_error);
    audiocpp_result_free(result);
    if (!written) return make_string(env, failure_json(write_error));

    std::ostringstream json;
    json << "{\"ok\":true,\"path\":\"" << json_escape(output.get())
         << "\",\"frames\":" << frames
         << ",\"sampleRate\":" << sample_rate
         << ",\"channels\":" << channels
         << ",\"styleControlsApplied\":" << (style_controls_applied ? "true" : "false") << "}";
    return make_string(env, json.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xiaosi555_yovoice_NativeEngine_nativeTranscribe(
    JNIEnv* env, jobject, jfloatArray pcm_array, jint sample_rate, jint channels, jstring language
) {
    UtfChars language_name(env, language);
    if (pcm_array == nullptr || sample_rate <= 0 || channels <= 0) {
        return make_string(env, failure_json("音频数据或采样参数无效"));
    }
    const jsize sample_count = env->GetArrayLength(pcm_array);
    if (sample_count <= 0 || sample_count > 120000000 || sample_count % channels != 0) {
        return make_string(env, failure_json("音频长度无效或超过 Android 单次推理上限"));
    }
    jfloat* samples = env->GetFloatArrayElements(pcm_array, nullptr);
    if (samples == nullptr) return make_string(env, failure_json("无法读取音频 PCM 数据"));
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_state.session == nullptr || g_state.task != "asr") {
        env->ReleaseFloatArrayElements(pcm_array, samples, JNI_ABORT);
        return make_string(env, failure_json("请先加载 ASR 模型"));
    }
    audiocpp_request* request = audiocpp_request_create();
    if (request == nullptr) {
        env->ReleaseFloatArrayElements(pcm_array, samples, JNI_ABORT);
        return make_string(env, failure_json("无法创建 ASR 请求"));
    }
    const size_t frames = static_cast<size_t>(sample_count) / static_cast<size_t>(channels);
    audiocpp_status status = audiocpp_request_set_audio(request, samples, frames, sample_rate, channels);
    env->ReleaseFloatArrayElements(pcm_array, samples, JNI_ABORT);
    if (status == AUDIOCPP_OK && language_name.get()[0] != '\0') {
        status = audiocpp_request_set_option(request, "language", language_name.get());
    }
    if (status != AUDIOCPP_OK) {
        const std::string message = status_message(status);
        audiocpp_request_free(request);
        return make_string(env, failure_json(message));
    }
    audiocpp_result* result = nullptr;
    status = audiocpp_session_run(g_state.session, request, &result);
    audiocpp_request_free(request);
    if (status != AUDIOCPP_OK || result == nullptr) {
        const std::string message = status_message(status);
        if (result != nullptr) audiocpp_result_free(result);
        return make_string(env, failure_json(message));
    }
    const char* transcript = nullptr;
    const char* result_language = nullptr;
    status = audiocpp_result_text(result, &transcript, &result_language);
    if (status != AUDIOCPP_OK || transcript == nullptr) {
        const std::string message = status_message(status);
        audiocpp_result_free(result);
        return make_string(env, failure_json(message));
    }
    std::ostringstream json;
    json << "{\"ok\":true,\"text\":\"" << json_escape(transcript)
         << "\",\"language\":\"" << json_escape(result_language == nullptr ? "" : result_language)
         << "\",\"sampleRate\":" << sample_rate << ",\"segments\":[";
    bool first = true;
    const size_t count = audiocpp_result_segment_count(result);
    for (size_t i = 0; i < count; ++i) {
        int64_t start = 0, end = 0;
        float confidence = 0.0f;
        const char* segment_text = nullptr;
        if (audiocpp_result_segment(result, i, &start, &end, &confidence, &segment_text) != AUDIOCPP_OK) continue;
        if (!first) json << ",";
        json << "{\"startSample\":" << start << ",\"endSample\":" << end
             << ",\"confidence\":" << confidence << ",\"text\":\""
             << json_escape(segment_text == nullptr ? "" : segment_text) << "\"}";
        first = false;
    }
    json << "]}";
    audiocpp_result_free(result);
    return make_string(env, json.str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_xiaosi555_yovoice_NativeEngine_nativeUnload(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_state();
}
