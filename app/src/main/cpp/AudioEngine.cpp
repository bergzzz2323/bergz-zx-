// Bergz ZX 700 — Oboe audio stream + JNI bridge.
// The UI thread only ever posts commands into the synth's lock-free queue or
// reads atomics; the audio callback never waits on the UI.
#include <jni.h>
#include <android/log.h>
#include <oboe/Oboe.h>

#include <memory>
#include <mutex>

#include "Presets.h"
#include "SynthCore.h"

#define LOG_TAG "BergzEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace bergz;

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine() : synth_(48000.f) {}

    bool start() {
        std::lock_guard<std::mutex> lock(mutex_);
        return openAndStart();
    }

    void stop() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stream_) {
            stream_->stop();
            stream_->close();
            stream_.reset();
        }
    }

    SynthCore& synth() { return synth_; }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* audioData,
                                          int32_t numFrames) override {
        synth_.render(static_cast<float*>(audioData), numFrames);
        return oboe::DataCallbackResult::Continue;
    }

    // Headphones unplugged, Bluetooth switched, etc.: reopen on the new device.
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result error) override {
        LOGE("Stream closed: %s, reopening", oboe::convertToText(error));
        std::lock_guard<std::mutex> lock(mutex_);
        stream_.reset();
        openAndStart();
    }

private:
    bool openAndStart() {
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setUsage(oboe::Usage::Media)
            ->setDataCallback(this)
            ->setErrorCallback(this);

        oboe::Result r = b.openStream(stream_);
        if (r != oboe::Result::OK) {
            LOGE("openStream failed: %s", oboe::convertToText(r));
            return false;
        }
        // Stream is not running yet, so it is safe to resize buffers here.
        const int sr = stream_->getSampleRate();
        if (sr != lastSampleRate_) {
            synth_.setSampleRate((float)sr);
            lastSampleRate_ = sr;
        }
        // Two bursts of buffering: low latency with a little safety margin.
        stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);
        LOGI("Stream open: %d Hz, burst %d, buffer %d, exclusive=%d", sr,
             stream_->getFramesPerBurst(), stream_->getBufferSizeInFrames(),
             stream_->getSharingMode() == oboe::SharingMode::Exclusive);

        r = stream_->requestStart();
        if (r != oboe::Result::OK) {
            LOGE("requestStart failed: %s", oboe::convertToText(r));
            return false;
        }
        return true;
    }

    std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    SynthCore synth_;
    int lastSampleRate_ = 48000;
};

static std::unique_ptr<AudioEngine> gEngine;

static void post(int type, int a, int b, float v) {
    if (gEngine) gEngine->synth().post({type, a, b, v});
}

extern "C" {

#define JNI_FN(name) Java_com_bergz_zx700_audio_NativeEngine_##name

JNIEXPORT jboolean JNICALL JNI_FN(nativeStart)(JNIEnv*, jobject) {
    if (!gEngine) gEngine = std::make_unique<AudioEngine>();
    return gEngine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeStop)(JNIEnv*, jobject) {
    if (gEngine) gEngine->stop();
}

JNIEXPORT void JNICALL JNI_FN(nativeSetParam)(JNIEnv*, jobject, jint id, jfloat v) {
    post(CMD_PARAM, id, 0, v);
}

JNIEXPORT void JNICALL JNI_FN(nativeTouch)(JNIEnv*, jobject, jint id, jboolean down) {
    post(CMD_TOUCH, id, down ? 1 : 0, 0.f);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetRecord)(JNIEnv*, jobject, jboolean on) {
    post(CMD_RECORD, 0, 0, on ? 1.f : 0.f);
}

JNIEXPORT void JNICALL JNI_FN(nativeClearMotion)(JNIEnv*, jobject, jint id) {
    post(CMD_CLEAR_MOTION, id, 0, 0.f);
}

JNIEXPORT void JNICALL JNI_FN(nativePlay)(JNIEnv*, jobject) { post(CMD_PLAY, 0, 0, 0.f); }

JNIEXPORT void JNICALL JNI_FN(nativeStopSequencer)(JNIEnv*, jobject) { post(CMD_STOP, 0, 0, 0.f); }

JNIEXPORT void JNICALL JNI_FN(nativeSetStepNote)(JNIEnv*, jobject, jint step, jint slot, jint note) {
    post(CMD_STEP_NOTE, step, note, (float)slot);
}

JNIEXPORT void JNICALL JNI_FN(nativeResetDefaults)(JNIEnv*, jobject) {
    post(CMD_RESET_DEFAULTS, 0, 0, 0.f);
}

JNIEXPORT jfloat JNICALL JNI_FN(nativeDefaultParam)(JNIEnv*, jobject, jint id) {
    return (id >= 0 && id < P_COUNT) ? kDefaultParams[id] : 0.f;
}

JNIEXPORT void JNICALL JNI_FN(nativeClearPattern)(JNIEnv*, jobject) {
    post(CMD_CLEAR_PATTERN, 0, 0, 0.f);
}

// ---- presets: read straight from the constant tables in Presets.h ----
JNIEXPORT jint JNICALL JNI_FN(nativePresetCount)(JNIEnv*, jobject) {
    return (jint)presets().size();
}

JNIEXPORT jstring JNICALL JNI_FN(nativePresetName)(JNIEnv* env, jobject, jint i) {
    if (i < 0 || i >= (jint)presets().size()) return env->NewStringUTF("");
    return env->NewStringUTF(presets()[i].name);
}

JNIEXPORT jfloatArray JNICALL JNI_FN(nativePresetParams)(JNIEnv* env, jobject, jint i) {
    jfloatArray arr = env->NewFloatArray(P_COUNT);
    if (i >= 0 && i < (jint)presets().size())
        env->SetFloatArrayRegion(arr, 0, P_COUNT, presets()[i].params.data());
    return arr;
}

// 16 steps x 4 chord slots, -1 = empty
JNIEXPORT jintArray JNICALL JNI_FN(nativePresetNotes)(JNIEnv* env, jobject, jint i) {
    jintArray arr = env->NewIntArray(kSteps * kNotesPerStep);
    if (i >= 0 && i < (jint)presets().size()) {
        jint tmp[kSteps * kNotesPerStep];
        for (int s = 0; s < kSteps; ++s)
            for (int n = 0; n < kNotesPerStep; ++n) tmp[s * kNotesPerStep + n] = presets()[i].notes[s][n];
        env->SetIntArrayRegion(arr, 0, kSteps * kNotesPerStep, tmp);
    }
    return arr;
}

JNIEXPORT void JNICALL JNI_FN(nativeLoadPreset)(JNIEnv*, jobject, jint i) {
    if (!gEngine || i < 0 || i >= (jint)presets().size()) return;
    loadPreset(presets()[i], [](const Cmd& c) { gEngine->synth().post(c); });
}

JNIEXPORT jint JNICALL JNI_FN(nativePresetCategory)(JNIEnv*, jobject, jint i) {
    if (i < 0 || i >= (jint)presets().size()) return 0;
    return presets()[i].category;
}

JNIEXPORT jint JNICALL JNI_FN(nativeCategoryCount)(JNIEnv*, jobject) { return kCategories; }

JNIEXPORT jstring JNICALL JNI_FN(nativeCategoryName)(JNIEnv* env, jobject, jint i) {
    return env->NewStringUTF(i >= 0 && i < kCategories ? kCategoryNames[i] : "");
}

// ---- live MIDI (called from the MIDI thread; the queue is multi-producer safe) ----
JNIEXPORT void JNICALL JNI_FN(nativeNoteOn)(JNIEnv*, jobject, jint note, jfloat velocity) {
    post(CMD_NOTE_ON, note, 0, velocity);
}
JNIEXPORT void JNICALL JNI_FN(nativeNoteOff)(JNIEnv*, jobject, jint note) { post(CMD_NOTE_OFF, note, 0, 0.f); }
JNIEXPORT void JNICALL JNI_FN(nativeAllNotesOff)(JNIEnv*, jobject) { post(CMD_ALL_NOTES_OFF, 0, 0, 0.f); }
JNIEXPORT void JNICALL JNI_FN(nativePitchBend)(JNIEnv*, jobject, jfloat v) { post(CMD_PITCH_BEND, 0, 0, v); }
JNIEXPORT void JNICALL JNI_FN(nativeMidiClock)(JNIEnv*, jobject) { post(CMD_MIDI_CLOCK, 0, 0, 0.f); }
JNIEXPORT void JNICALL JNI_FN(nativeMidiStart)(JNIEnv*, jobject) { post(CMD_MIDI_START, 0, 0, 0.f); }
JNIEXPORT void JNICALL JNI_FN(nativeMidiContinue)(JNIEnv*, jobject) { post(CMD_MIDI_CONTINUE, 0, 0, 0.f); }
JNIEXPORT void JNICALL JNI_FN(nativeMidiStop)(JNIEnv*, jobject) { post(CMD_MIDI_STOP, 0, 0, 0.f); }

JNIEXPORT jboolean JNICALL JNI_FN(nativeIsPlaying)(JNIEnv*, jobject) {
    return gEngine && gEngine->synth().isPlaying() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL JNI_FN(nativeExternalSync)(JNIEnv*, jobject) {
    return gEngine && gEngine->synth().externalSync() ? JNI_TRUE : JNI_FALSE;
}

// Reopen the audio stream, e.g. when a USB audio device is plugged in, so the
// sound follows the phone's current output (USB-C interface, DAC, headphones).
JNIEXPORT jboolean JNICALL JNI_FN(nativeRestartAudio)(JNIEnv*, jobject) {
    if (!gEngine) return JNI_FALSE;
    gEngine->stop();
    return gEngine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL JNI_FN(nativeSetDrum)(JNIEnv*, jobject, jint lane, jint step, jboolean on) {
    post(CMD_DRUM, lane, step, on ? 1.f : 0.f);
}

JNIEXPORT void JNICALL JNI_FN(nativeSetLock)(JNIEnv*, jobject, jint step, jfloat v) {
    post(CMD_LOCK, step, 0, v);
}

JNIEXPORT jint JNICALL JNI_FN(nativePlayhead)(JNIEnv*, jobject) {
    return gEngine ? gEngine->synth().playhead() : -1;
}

JNIEXPORT jint JNICALL JNI_FN(nativeMotionMask)(JNIEnv*, jobject) {
    return gEngine ? (jint)gEngine->synth().motionMask() : 0;
}

JNIEXPORT jfloat JNICALL JNI_FN(nativeParam)(JNIEnv*, jobject, jint id) {
    return gEngine ? gEngine->synth().param(id) : 0.f;
}

}  // extern "C"
