package com.jefino.skiashift

import android.annotation.SuppressLint
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam

class SkiaShiftModule : XposedModule() {

    companion object {
        const val TAG = "SkiaShift"
    }

    @SuppressLint("DiscouragedPrivateApi")
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)

        Log.i(TAG, "SkiaShift module loaded for ${param.processName}.")

        try {
            System.loadLibrary("skiashift")

            try {
                val content = java.io.File("/data/local/tmp/skiashift_config.json").readText()
                val json = org.json.JSONObject(content)
                var renderer = if (json.has(param.processName)) json.getString(param.processName) else null
                
                if (renderer.isNullOrEmpty() || renderer == "default") {
                    renderer = if (json.has(com.jefino.skiashift.SkiaShiftProvider.KEY_GLOBAL_RENDERER)) json.getString(com.jefino.skiashift.SkiaShiftProvider.KEY_GLOBAL_RENDERER) else "skiavk"
                }

                if (!renderer.isNullOrEmpty()) {
                    Log.i(TAG, "Read renderer $renderer for ${param.processName} from /data/local/tmp/skiashift_config.json")
                    setRendererNative(renderer)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to read configuration from /data/local/tmp", e)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load native library.", e)
        }
    }

    private external fun setRendererNative(renderer: String)
}
