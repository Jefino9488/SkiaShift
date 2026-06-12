package com.jefino.skiashift

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean,
    var icon: Drawable? = null
)

object AppManager {
    suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val scopedPackages = mutableSetOf<String>()
        try {
            val cacheDb = java.io.File(context.cacheDir, "modules_config.db")
            val cacheDir = context.cacheDir.absolutePath
            
            val cmd = "cp /data/adb/lspd/config/modules_config.db* $cacheDir/ && chmod 0644 $cacheDir/modules_config.db*"
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
            
            if (cacheDb.exists()) {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    cacheDb.absolutePath, 
                    null, 
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                val cursor = db.rawQuery("SELECT app_pkg_name FROM scope WHERE module_pkg_name='com.jefino.skiashift'", null)
                while (cursor.moveToNext()) {
                    scopedPackages.add(cursor.getString(0))
                }
                cursor.close()
                db.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val allApps = apps.map { app ->
            AppInfo(
                name = pm.getApplicationLabel(app).toString(),
                packageName = app.packageName,
                isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
        
        val finalApps = if (scopedPackages.isNotEmpty()) {
            allApps.filter { scopedPackages.contains(it.packageName) }
        } else {
            allApps
        }
        
        finalApps.sortedBy { it.name.lowercase() }
    }

    fun loadIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
}
