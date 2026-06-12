package com.jefino.skiashift

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class SkiaShiftProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.jefino.skiashift.provider"
        val URI: Uri = Uri.parse("content://$AUTHORITY/prefs")
        const val PREFS_NAME = "skiashift_prefs"
        const val KEY_GLOBAL_RENDERER = "global_renderer"
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return null

        val packageName = selectionArgs?.firstOrNull() ?: return null

        val globalRenderer = prefs.getString(KEY_GLOBAL_RENDERER, "skiavk") ?: "skiavk"
        val appRenderer = prefs.getString(packageName, globalRenderer) ?: globalRenderer

        val cursor = MatrixCursor(arrayOf("renderer"))
        cursor.addRow(arrayOf(appRenderer))
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
