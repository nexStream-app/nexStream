#include <jni.h>
#include <string>
#include <atomic>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global abort flag — set from Kotlin via abortTranscription() to cancel an in-progress whisper_full()
static std::atomic<bool> g_abort{false};

static bool whisper_abort_callback(void *) {
    return g_abort.load(std::memory_order_relaxed);
}

static inline whisper_context * ptr(jlong p) {
    return reinterpret_cast<whisper_context *>(p);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_nexstream_player_subtitle_WhisperLib_initContext(
        JNIEnv *env, jclass, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // No GPU on TV sticks
    auto *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) { LOGE("whisper_init_from_file_with_params returned null"); return 0L; }
    LOGI("Model loaded OK");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_app_nexstream_player_subtitle_WhisperLib_freeContext(
        JNIEnv *, jclass, jlong contextPtr) {
    if (contextPtr) whisper_free(ptr(contextPtr));
}

JNIEXPORT void JNICALL
Java_app_nexstream_player_subtitle_WhisperLib_abortTranscription(
        JNIEnv *, jclass) {
    g_abort.store(true, std::memory_order_relaxed);
    LOGI("abortTranscription: flag set");
}

JNIEXPORT jstring JNICALL
Java_app_nexstream_player_subtitle_WhisperLib_transcribe(
        JNIEnv *env, jclass,
        jlong   contextPtr,
        jfloatArray samples,
        jstring language,
        jboolean translate,
        jint    numThreads) {

    auto *ctx = ptr(contextPtr);
    if (!ctx) return env->NewStringUTF("");

    const jsize nSamples  = env->GetArrayLength(samples);
    jfloat     *samplePtr = env->GetFloatArrayElements(samples, nullptr);

    whisper_full_params params   = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads             = static_cast<int>(numThreads);
    params.translate             = static_cast<bool>(translate);
    params.no_context            = true;
    params.single_segment        = true;   // one segment per window — faster
    params.print_special         = false;
    params.print_progress        = false;
    params.print_realtime        = false;
    params.print_timestamps      = false;
    params.no_timestamps         = true;   // skip timestamp decoding — faster

    // Limit encoder to the actual audio we have.  Default is 1500 mel frames (30s);
    // 512 frames covers ~10s and cuts encoder time to ~1/3 for our 5s windows.
    params.audio_ctx             = 512;

    // Abort callback: Kotlin calls abortTranscription() to set g_abort and return early
    g_abort.store(false, std::memory_order_relaxed);
    params.abort_callback        = whisper_abort_callback;
    params.abort_callback_user_data = nullptr;

    const char *lang = env->GetStringUTFChars(language, nullptr);
    params.language = (lang && lang[0] != '\0') ? lang : nullptr;

    LOGI("transcribe: %d samples, %d threads, audio_ctx=512", nSamples, numThreads);
    const int ret = whisper_full(ctx, params, samplePtr, static_cast<int>(nSamples));
    env->ReleaseFloatArrayElements(samples, samplePtr, JNI_ABORT);
    env->ReleaseStringUTFChars(language, lang);

    if (ret != 0) {
        LOGE("whisper_full returned %d (aborted=%s)", ret, g_abort.load() ? "yes" : "no");
        return env->NewStringUTF("");
    }

    const int nSeg = whisper_full_n_segments(ctx);
    std::string result;
    result.reserve(256);
    for (int i = 0; i < nSeg; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (text) result += text;
    }

    // Trim leading/trailing whitespace
    const size_t start = result.find_first_not_of(" \t\n\r");
    if (start == std::string::npos) return env->NewStringUTF("");
    const size_t end = result.find_last_not_of(" \t\n\r");
    return env->NewStringUTF(result.substr(start, end - start + 1).c_str());
}

} // extern "C"
