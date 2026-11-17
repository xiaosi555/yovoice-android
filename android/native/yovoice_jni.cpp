#include <jni.h>
#include <sstream>
#include <string>

#include "audiocpp.h"

namespace {
jstring make_string(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_xiaosi555_yovoice_NativeEngine_nativeProbe(JNIEnv* env, jobject) {
    std::ostringstream json;
    json << "{\"abi\":" << audiocpp_abi_version()
         << ",\"version\":\"" << audiocpp_build_version() << "\"";

    audiocpp_registry* registry = nullptr;
    const audiocpp_status status = audiocpp_registry_create(nullptr, &registry);
    if (status != AUDIOCPP_OK) {
        const char* error = audiocpp_last_error();
        json << ",\"status\":\"error\",\"message\":\""
             << (error == nullptr ? audiocpp_status_string(status) : error)
             << "\"}";
        return make_string(env, json.str());
    }

    json << ",\"status\":\"ready\",\"families\":[";
    const size_t family_count = audiocpp_registry_family_count(registry);
    for (size_t i = 0; i < family_count; ++i) {
        const char* family = nullptr;
        if (audiocpp_registry_family(registry, i, &family) == AUDIOCPP_OK && family != nullptr) {
            if (i != 0) json << ",";
            json << "\"" << family << "\"";
        }
    }
    json << "],\"tasks\":[";
    const size_t task_count = audiocpp_task_count();
    for (size_t i = 0; i < task_count; ++i) {
        const char* task = audiocpp_task_name(i);
        if (task != nullptr) {
            if (i != 0) json << ",";
            json << "\"" << task << "\"";
        }
    }
    json << "]}";
    audiocpp_registry_free(registry);
    return make_string(env, json.str());
}
