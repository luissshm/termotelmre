#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "daas.hpp"

#define LOG_TAG "DaaS-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static DaasAPI* g_daas = nullptr;
static jclass g_daas_manager = nullptr;

static const typeset_t SIMPLE_TYPESET = 1;

/* ---------------- Events ---------------- */

class DaasEvents : public IDaasApiEvent {
public:
    void dinAccepted(din_t din) override {
        LOGD("[DaaS] dinAccepted %lu", (din >> 44));

        JNIEnv* env;
        g_vm->AttachCurrentThread(&env, nullptr);

        jmethodID mid = env->GetStaticMethodID(g_daas_manager, "onDinAccepted", "(J)V");

        if (mid) {
            env->CallStaticVoidMethod(g_daas_manager, mid, (jlong)(din >> 44));
        }
    }

    void ddoReceived(int payload_size, typeset_t typeset, din_t origin) override {
        LOGD("[DaaS] ddoReceived from %lu size=%d typeset=%d", (origin >> 44), payload_size, typeset);

        DDO* ddo = nullptr;
        if (g_daas->pull(origin, &ddo) != ERROR_NONE || !ddo)
            return;

        // Attach JNI
        JNIEnv* env;
        g_vm->AttachCurrentThread(&env, nullptr);

        din_t realDin = origin >> 44;

        // EVENT DDO (typeset 3400, 6 bytes: 4+1+1)
        if (typeset == 3400 && payload_size >= 6) {
            uint8_t buffer[6];
            ddo->getPayloadAsBinary(buffer, 0, 6);

            uint32_t timestamp =
                    ((uint32_t)buffer[0]) |
                    ((uint32_t)buffer[1] << 8) |
                    ((uint32_t)buffer[2] << 16) |
                    ((uint32_t)buffer[3] << 24);

            uint8_t logId = buffer[4];
            uint8_t logValue = buffer[5];

            jmethodID mid = env->GetStaticMethodID(
                    g_daas_manager,
                    "onLogDDOReceived",
                    "(JIII)V"
            );

            if (mid) {
                env->CallStaticVoidMethod(
                        g_daas_manager,
                        mid,
                        (jlong)realDin,
                        (jint)timestamp,
                        (jint)logId,
                        (jint)logValue
                );
            }

            delete ddo;
            return;
        }

        if (typeset == 3110 && payload_size >= 16) {
            std::vector<uint8_t> buffer(payload_size);
            ddo->getPayloadAsBinary(buffer.data(), 0, payload_size);

            jbyteArray payloadArray = env->NewByteArray(payload_size);
            env->SetByteArrayRegion(
                    payloadArray,
                    0,
                    payload_size,
                    reinterpret_cast<jbyte*>(buffer.data())
            );

            jmethodID mid = env->GetStaticMethodID(
                    g_daas_manager,
                    "onStatusReportReceived",
                    "(JI[B)V"
            );

            if (mid) {
                env->CallStaticVoidMethod(
                        g_daas_manager,
                        mid,
                        (jlong)realDin,
                        (jint)typeset,
                        payloadArray
                );
            }

            env->DeleteLocalRef(payloadArray);
            delete ddo;
            return;
        }

        // Normal DDOs
        uint32_t value = 0;
        ddo->getPayloadAsBinary((uint8_t*)&value, 0, sizeof(uint32_t));

        jmethodID mid = env->GetStaticMethodID(g_daas_manager, "onDDOReceivedExtended", "(JII)V");
        if (mid) {
            env->CallStaticVoidMethod(g_daas_manager, mid, (jlong)realDin, (jint)typeset, (jint)value);
        }

        delete ddo;
    }

    void nodeDiscovered(din_t din, link_t link) override {
        LOGD("[DaaS] Node Discovered DIN=%lu LINK=%d", din, link);

        if (!g_vm) return;

        JNIEnv* env = nullptr;
        g_vm->AttachCurrentThread(&env, nullptr);


        jmethodID mid = env->GetStaticMethodID(g_daas_manager,
                                               "onNodeDiscovered",
                                               "(J)V");

        if (!mid) {
            LOGD("[JNI] Failed to find onNodeDiscovered method");
            return;
        }

        env->CallStaticVoidMethod(g_daas_manager, mid, (jlong)din);
    }

    void nodeConnectedToNetwork(din_t sid, din_t din) override {
        LOGD("[DaaS] nodeConnectedToNetwork sid=%lu din=%lu", sid, (din >> 44));
        JNIEnv* env;
        g_vm->AttachCurrentThread(&env, nullptr);

        jmethodID mid = env->GetStaticMethodID(g_daas_manager, "onNetworkConnected", "(J)V");

        if (mid) {
            env->CallStaticVoidMethod(g_daas_manager, mid, (jlong)(din >> 44));
        }
    }

    void frisbeeReceived(din_t) override {}
    void nodeStateReceived(din_t) override {}
    void atsSyncCompleted(din_t) override {}
    void frisbeeDperfCompleted(din_t, uint32_t, uint32_t) override {}
    void streamInfoReceived(din_t din, stream_type pkt_type, uint32_t stream_id) override {}

};

static DaasEvents g_events;

/* ---------------- JNI lifecycle ---------------- */

extern void set_jvm(JavaVM* jvm);
extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    set_jvm(vm);
    g_vm = vm;

    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass localClass = env->FindClass("com/sebyone/daas/DaasManager");
    if (localClass != nullptr) {
        g_daas_manager = (jclass) env->NewGlobalRef(localClass);
        env->DeleteLocalRef(localClass);
    }
    return JNI_VERSION_1_6;
}

/* ---------------- API wrappers ---------------- */

extern "C"
JNIEXPORT void JNICALL
Java_com_sebyone_daas_DaasManager_nativeCreate(JNIEnv*, jclass) {
    LOGD("[DaaS] Creating DaasAPI instance...");
    g_daas = new DaasAPI(&g_events);
    LOGD("[DaaS] Library version: %s", g_daas->getVersion());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeInit(
        JNIEnv* env, jclass, din_t sid, din_t din) {

    LOGD("[DaaS] Initializing with SID=%lu DIN=%lu", sid, din);
    auto err = g_daas->doInit(sid, din);
    LOGD("[DaaS] doInit() -> %d", err);
    return err;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sebyone_daas_DaasManager_nativeListDrivers(JNIEnv* env, jclass) {
    return env->NewStringUTF(g_daas->listAvailableDrivers());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeEnableDriver(
        JNIEnv* env, jclass, jstring uri, jbyte driver) {

    const char* c_uri = env->GetStringUTFChars(uri, nullptr);
    LOGD("[DaaS] Enabling driver %d with URI %s", driver, c_uri);

    auto err = g_daas->enableDriver(static_cast<link_t>(driver), c_uri);

    LOGD("[DaaS] enableDriver(%d) -> %d", driver, err);

    g_daas->setATSMaxError(1000);

    env->ReleaseStringUTFChars(uri, c_uri);
    return err;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeMap(JNIEnv* env, jclass, din_t din, jstring uri, jbyte driver) {
    const char* c_uri = env->GetStringUTFChars(uri, nullptr);

    din_t rawDin = din << 44; // <-- ADD THIS SHIFT
    LOGD("[DaaS] Mapping DIN %lu (Logical: %lu) to %s", rawDin, din, c_uri);

    auto err = g_daas->map(rawDin, static_cast<link_t>(driver), c_uri); // Use rawDin here

    env->ReleaseStringUTFChars(uri, c_uri);
    return err;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativePerform(
        JNIEnv*, jclass) {
    return g_daas->doPerform(PERFORM_CORE_THREAD);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeSendDDO(JNIEnv*, jclass, din_t remoteDin, jlong value, jbyte tset) {
    DDO ddo(tset);

    ddo.setPayload(&value, 8);

    din_t rawDin = remoteDin; // <-- ADD THIS SHIFT
    auto err = g_daas->push(rawDin, &ddo); // Use rawDin here

    return err;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeDiscovery(
        JNIEnv*, jclass, jbyte driver, jlong sid) {

    LOGD("[DaaS] Initializing discovery BLE");

    g_daas->unbindNetwork();
    auto err = g_daas->discovery(sid);

    LOGD("[DaaS] discovery(%d) -> %d", driver, err);
    return err;
}
#include <unistd.h>
static void* stdout_to_logcat(void*) {
    int pipes[2];
    pipe(pipes);
    dup2(pipes[1], STDOUT_FILENO);
    dup2(pipes[1], STDERR_FILENO);
    close(pipes[1]);

    char buf[512];
    int n;
    while ((n = read(pipes[0], buf, sizeof(buf) - 1)) > 0) {
        buf[n] = '\0';
        LOGD("%s", buf);
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sebyone_daas_DaasManager_redirectLogs(JNIEnv*, jobject) {
    // Activate only for debug
    return;
    /*__android_log_print(ANDROID_LOG_DEBUG, "DAAS_NATIVE", "redirectLogs called"); // ← confirm JNI works
    pthread_t t;
    pthread_create(&t, nullptr, stdout_to_logcat, nullptr);
    pthread_detach(t);

    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);*/
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeLocate(
        JNIEnv*, jclass, jlong din, jint timeout_ms) {

    if (!g_daas) return -1;

    LOGD("[DaaS] Locating DIN=%lu with timeout=%dms", din, timeout_ms);
    auto err = g_daas->locate((din_t)din, timeout_ms);
    LOGD("[DaaS] locate() -> %d", err);
    return err;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sebyone_daas_DaasManager_nativeSetDiscoveryStateFull(
        JNIEnv*, jclass) {

    discovery_state_t dis = discovery_sender_only;
    LOGD("[DaaS] Setting discovery state to -> discovery_sender_only");
    g_daas->setDiscoveryState(dis);

}

extern "C"
JNIEXPORT jlongArray JNICALL
Java_com_sebyone_daas_DaasManager_nativeListNodes(
        JNIEnv* env, jclass) {

    if (!g_daas) {
        LOGD("[DaaS] List Nodes: g_daas is null");
        return nullptr;
    }

    // Get the local node list
    dinlist_t nodes = g_daas->listNodes();
    jsize count = static_cast<jsize>(nodes.size());

    LOGD("[DaaS] List Nodes -> %d nodes", count);

    // Create a Java long array to return
    jlongArray jnodes = env->NewLongArray(count);
    if (!jnodes) {
        LOGD("[DaaS] ListNodes: failed to allocate jlongArray");
        return nullptr;
    }

    // Copy values into Java array
    std::vector<jlong> tmp(count);
    for (jsize i = 0; i < count; ++i) {
        tmp[i] = static_cast<jlong>(nodes[i]);
    }
    env->SetLongArrayRegion(jnodes, 0, count, tmp.data());

    return jnodes;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_sebyone_daas_DaasManager_nativeAutoPull(
        JNIEnv* env, jclass, jlong remoteDin) {

    if (!g_daas) return;

    DDO* inbound = nullptr;
    auto err = g_daas->pull((din_t)remoteDin, &inbound);

    if (err != ERROR_NONE || inbound == nullptr) {
        return; // no data available
    }

//    inbound->getPayloadAsBinary((uint8_t*)&value, 0, sizeof(value));
    uint8_t  * payload = inbound->getPayloadPtr();
    int value = payload[0];


    din_t origin = inbound->getOrigin();
    stime_t ts = inbound->getTimestamp();

    LOGD("[AUTO-PULL] origin=%lu value=%d ts=%lu", origin, payload[0], ts);

    // notify Java UI
    JNIEnv* jni = nullptr;
    g_vm->AttachCurrentThread(&jni, nullptr);

    jmethodID mid = jni->GetStaticMethodID(g_daas_manager, "onAutoPull", "(JI)V");

    if (mid)
        jni->CallStaticVoidMethod(g_daas_manager, mid, (jlong)origin, (jint)value);

    delete inbound;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeSendDDOBytes(
        JNIEnv* env, jclass, jlong remoteDin, jbyteArray payload, jint tset) {

    if (!g_daas) return -1;
    if (!payload) return -2;

    jsize len = env->GetArrayLength(payload);
    if (len <= 0) return -3;

    std::vector<jbyte> buffer(len);
    env->GetByteArrayRegion(payload, 0, len, buffer.data());

    DDO ddo((typeset_t)tset);
    ddo.setPayload(reinterpret_cast<uint8_t*>(buffer.data()), len);

    din_t rawDin = (din_t)remoteDin << 44; // <-- ADD THIS SHIFT
    LOGD("[DaaS] push(bytes) -> Logical DIN=%lu, Raw DIN=%lu", (unsigned long)remoteDin, rawDin);

    auto err = g_daas->push(rawDin, &ddo); // Use rawDin here

    return err;
}



extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeSimpleDiscovery(JNIEnv *env, jobject thiz, jlong sid) {
    LOGD("[DaaS] Discovering trought SID: %ld ", sid);
    auto err = g_daas->discovery(sid);
    return err;

}
extern "C"
JNIEXPORT jint JNICALL
Java_com_sebyone_daas_DaasManager_nativeUnbindNetwork(JNIEnv *env, jobject thiz) {
    LOGD("[DaaS] UnbindingNetwork");
    auto err = g_daas->unbindNetwork();
    return err;
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_sebyone_daas_DaasManager_nativeGetSystemStatistics(
        JNIEnv *env,
        jobject thiz,
        jint syscode
) {
    LOGD("[DaaS] Obtaining System Statistics for ID= %d", syscode);

    if (g_daas == nullptr) {
        LOGD("[DaaS] ERROR: g_daas is null");
        return -1;
    }

    auto code = static_cast<syscode_t>(syscode);
    auto result = g_daas->getSystemStatistics(code);

    LOGD("[DaaS] System Statistics result = %ld", static_cast<long>(result));

    return static_cast<jlong>(result);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_sebyone_daas_DaasManager_nativeSetDiscoveryState(JNIEnv *env, jobject thiz, jint state) {
    LOGD("[DaaS] Setting discovery state");

    auto discstate = (discovery_state_t) state;
    g_daas->setDiscoveryState(discstate);

}
extern "C"
JNIEXPORT void JNICALL
Java_com_sebyone_daas_DaasManager_nativeRemove(JNIEnv *env, jobject thiz, jlong din) {
    LOGD("[DaaS] Removing node from map...");
    auto err = g_daas->remove(din);
    LOGD("[DaaS] RemoveNode: Error code: %u", err);

}