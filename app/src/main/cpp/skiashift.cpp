#include <jni.h>
#include <string.h>
#include <string>
#include <android/log.h>
#include <sys/system_properties.h>
#include <bytehook.h>
#include <shadowhook.h>

#define LOG_TAG "SkiaShiftNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static char OVERRIDE_VALUE[32] = "skiagl";

static const char* get_override_value(const char* key) {
    if (!key) return nullptr;
    bool is_vk = (strcmp(OVERRIDE_VALUE, "skiavk") == 0);
    
    if (strcmp(key, "ro.hwui.use_vulkan") == 0) return is_vk ? "true" : "false";
    if (strcmp(key, "debug.hwui.renderer") == 0) return is_vk ? "skiavk" : "skiagl";
    if (strcmp(key, "debug.renderengine.backend") == 0) return is_vk ? "skiavkthreaded" : "skiaglthreaded";
    if (strcmp(key, "renderthread.skia.reduceopstasksplitting") == 0) return is_vk ? "true" : nullptr;
    if (strcmp(key, "debug.hwui.use_buffer_age") == 0) return is_vk ? "true" : "false";
    if (strcmp(key, "debug.hwui.skia_use_perf_hint") == 0) return "true";
    
    return nullptr;
}

// android::base::GetProperty hook
typedef std::string (*GetProperty_fn)(const std::string& key, const std::string& default_value);
static GetProperty_fn orig_GetProperty = nullptr;

static std::string my_GetProperty(const std::string& key, const std::string& default_value) {
    const char* override_val = get_override_value(key.c_str());
    if (override_val) {
        LOGI("Intercepted android::base::GetProperty for %s -> %s", key.c_str(), override_val);
        return std::string(override_val);
    }
    auto prev_func = (decltype(&my_GetProperty))bytehook_get_prev_func(reinterpret_cast<void*>(my_GetProperty));
    return prev_func ? prev_func(key, default_value) : default_value;
}

typedef int (*__system_property_get_fn)(const char *name, char *value);
static __system_property_get_fn orig_system_property_get = nullptr;

static int my_system_property_get(const char *name, char *value) {
    const char* override_val = get_override_value(name);
    if (override_val) {
        LOGI("Intercepted __system_property_get for %s -> %s", name, override_val);
        strcpy(value, override_val);
        return strlen(override_val);
    }
    auto prev_func = (decltype(&my_system_property_get))bytehook_get_prev_func(reinterpret_cast<void*>(my_system_property_get));
    if (prev_func) return prev_func(name, value);
    if (value) value[0] = '\0';
    return 0;
}

typedef int (*property_get_fn)(const char *key, char *value, const char *default_value);
static property_get_fn orig_property_get = nullptr;

static int my_property_get(const char *key, char *value, const char *default_value) {
    const char* override_val = get_override_value(key);
    if (override_val) {
        LOGI("Intercepted property_get for %s -> %s", key, override_val);
        strcpy(value, override_val);
        return strlen(override_val);
    }
    auto prev_func = (decltype(&my_property_get))bytehook_get_prev_func(reinterpret_cast<void*>(my_property_get));
    if (prev_func) return prev_func(key, value, default_value);
    if (value && default_value) {
        strcpy(value, default_value);
        return strlen(default_value);
    }
    if (value) value[0] = '\0';
    return 0;
}

// property_get_bool hook
typedef int8_t (*property_get_bool_fn)(const char *key, int8_t default_value);
static property_get_bool_fn orig_property_get_bool = nullptr;

static int8_t my_property_get_bool(const char *key, int8_t default_value) {
    const char* override_val = get_override_value(key);
    if (override_val) {
        LOGI("Intercepted property_get_bool for %s -> %s", key, override_val);
        return (strcmp(override_val, "true") == 0 || strcmp(override_val, "1") == 0) ? 1 : 0;
    }
    auto prev_func = (decltype(&my_property_get_bool))bytehook_get_prev_func(reinterpret_cast<void*>(my_property_get_bool));
    return prev_func ? prev_func(key, default_value) : default_value;
}

// android::base::GetBoolProperty hook
typedef bool (*GetBoolProperty_fn)(const std::string& key, bool default_value);
static GetBoolProperty_fn orig_GetBoolProperty = nullptr;

static bool my_GetBoolProperty(const std::string& key, bool default_value) {
    const char* override_val = get_override_value(key.c_str());
    if (override_val) {
        LOGI("Intercepted android::base::GetBoolProperty for %s -> %s", key.c_str(), override_val);
        return (strcmp(override_val, "true") == 0 || strcmp(override_val, "1") == 0);
    }
    auto prev_func = (decltype(&my_GetBoolProperty))bytehook_get_prev_func(reinterpret_cast<void*>(my_GetBoolProperty));
    return prev_func ? prev_func(key, default_value) : default_value;
}

typedef const void* (*__system_property_find_fn)(const char* name);
static __system_property_find_fn orig_system_property_find = nullptr;

static const void* my_system_property_find(const char* name) {
    const char* override_val = get_override_value(name);
    if (override_val) {
        LOGI("Intercepted __system_property_find for %s", name);
    }
    return orig_system_property_find ? orig_system_property_find(name) : nullptr;
}

// Hooking __system_property_read_callback is tricky because we need to wrap the callback
struct CallbackCookie {
    void (*orig_callback)(void *cookie, const char *name, const char *value, uint32_t serial);
    void *orig_cookie;
};

static void my_callback(void *cookie, const char *name, const char *value, uint32_t serial) {
    CallbackCookie *c = static_cast<CallbackCookie *>(cookie);
    const char* override_val = get_override_value(name);
    if (override_val) {
        LOGI("Intercepted __system_property_read_callback for %s -> %s", name, override_val);
        c->orig_callback(c->orig_cookie, name, override_val, serial);
    } else {
        c->orig_callback(c->orig_cookie, name, value, serial);
    }
    delete c;
}

typedef void (*__system_property_read_callback_fn)(const prop_info *pi,
                                                   void (*callback)(void *cookie, const char *name, const char *value, uint32_t serial),
                                                   void *cookie);
static __system_property_read_callback_fn orig_system_property_read_callback = nullptr;

static void my_system_property_read_callback(const prop_info *pi,
                                             void (*callback)(void *cookie, const char *name, const char *value, uint32_t serial),
                                             void *cookie) {
    if (callback == nullptr) {
        if (orig_system_property_read_callback) {
            orig_system_property_read_callback(pi, callback, cookie);
        }
        return;
    }
    CallbackCookie *c = new CallbackCookie{callback, cookie};
    if (orig_system_property_read_callback) {
        orig_system_property_read_callback(pi, my_callback, c);
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_jefino_skiashift_SkiaShiftModule_setRendererNative(JNIEnv* env, jobject /* this */, jstring renderer) {
    const char* renderer_str = env->GetStringUTFChars(renderer, nullptr);
    if (renderer_str) {
        strncpy(OVERRIDE_VALUE, renderer_str, sizeof(OVERRIDE_VALUE) - 1);
        OVERRIDE_VALUE[sizeof(OVERRIDE_VALUE) - 1] = '\0';
        env->ReleaseStringUTFChars(renderer, renderer_str);
        LOGI("Renderer globally set to %s via JNI", OVERRIDE_VALUE);
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    LOGI("Initializing ByteHook and ShadowHook for SkiaShift...");
    bytehook_init(BYTEHOOK_MODE_AUTOMATIC, false);
    
    // Initialize ShadowHook for inline hooking
    shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false);

    // Hook property_get using ByteHook (it's safe for PLT)
    bytehook_hook_all(
            nullptr,
            "property_get",
            reinterpret_cast<void *>(my_property_get),
            nullptr,
            reinterpret_cast<void **>(&orig_property_get)
    );

    // Hook property_get_bool using ByteHook
    bytehook_hook_all(
            nullptr,
            "property_get_bool",
            reinterpret_cast<void *>(my_property_get_bool),
            nullptr,
            reinterpret_cast<void **>(&orig_property_get_bool)
    );

    // Hook android::base::GetBoolProperty using ByteHook
    bytehook_hook_all(
            nullptr,
            "_ZN7android4base15GetBoolPropertyERKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEb",
            reinterpret_cast<void *>(my_GetBoolProperty),
            nullptr,
            reinterpret_cast<void **>(&orig_GetBoolProperty)
    );

    // Hook __system_property_get using ByteHook
    bytehook_hook_all(
            nullptr,
            "__system_property_get",
            reinterpret_cast<void *>(my_system_property_get),
            nullptr,
            reinterpret_cast<void **>(&orig_system_property_get)
    );

    // Hook __system_property_read_callback using ShadowHook to catch internal Bionic calls!
    shadowhook_hook_sym_name(
            "libc.so",
            "__system_property_read_callback",
            reinterpret_cast<void *>(my_system_property_read_callback),
            reinterpret_cast<void **>(&orig_system_property_read_callback)
    );

    // Hook __system_property_find using ShadowHook
    shadowhook_hook_sym_name(
            "libc.so",
            "__system_property_find",
            reinterpret_cast<void *>(my_system_property_find),
            reinterpret_cast<void **>(&orig_system_property_find)
    );

    // Hook android::base::GetProperty using ByteHook
    bytehook_hook_all(
            nullptr,
            "_ZN7android4base11GetPropertyERKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEES9_",
            reinterpret_cast<void *>(my_GetProperty),
            nullptr,
            reinterpret_cast<void **>(&orig_GetProperty)
    );

    LOGI("SkiaShift Native Hook Installed.");
    return JNI_VERSION_1_6;
}
