package com.thermalprinter

import android.app.Application
import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.com.thermalprinter.NitroThermalPrinterOnLoad

/**
 * Provides application context to Nitro HybridObjects.
 * Uses a ContentProvider to automatically initialize with the app context.
 */
@DoNotStrip
@Keep
object ApplicationContextProvider {
    @Volatile
    var context: Context? = null
        private set

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
}

/**
 * ContentProvider that initializes the ApplicationContextProvider.
 * This is automatically called when the app starts.
 */
@DoNotStrip
@Keep
class ThermalPrinterInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { ctx ->
            ApplicationContextProvider.initialize(ctx)
        }
        // Load the native library and register HybridObjects in the Nitro registry
        NitroThermalPrinterOnLoad.initializeNative()
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
