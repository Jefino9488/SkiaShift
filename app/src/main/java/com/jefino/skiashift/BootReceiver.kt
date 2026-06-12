package com.jefino.skiashift

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(SkiaShiftProvider.PREFS_NAME, Context.MODE_PRIVATE)
            val globalRenderer = prefs.getString(SkiaShiftProvider.KEY_GLOBAL_RENDERER, "skiavk") ?: "skiavk"
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val isVk = globalRenderer == "skiavk"
                    val setProps = """
                        setprop debug.hwui.renderer $globalRenderer
                        setprop debug.renderengine.backend ${if (isVk) "skiavkthreaded" else "skiaglthreaded"}
                        setprop debug.hwui.use_buffer_age ${if (isVk) "true" else "false"}
                        setprop debug.hwui.skia_use_perf_hint true
                        ${if (isVk) "setprop renderthread.skia.reduceopstasksplitting true" else ""}
                    """.trimIndent()
                    
                    val cacheScript = java.io.File(context.cacheDir, "apply_props.sh")
                    cacheScript.writeText(setProps)
                    
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "sh ${cacheScript.absolutePath}")).waitFor()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
