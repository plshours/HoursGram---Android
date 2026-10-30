package com.hoursgram.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class HorusApp : Application() {

    private val repoFlow = MutableStateFlow<TdRepo?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    val repo: StateFlow<TdRepo?> = repoFlow.asStateFlow()
    val startError: StateFlow<String?> = errorFlow.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLog()
        val keys = loadKeys()
        if (keys != null) start(keys.first, keys.second)
    }

    fun saveKeys(apiId: Int, apiHash: String) {
        prefs().edit().putInt("api_id", apiId).putString("api_hash", apiHash).apply()
        start(apiId, apiHash)
    }

    fun lastCrash(): String? {
        val file = File(filesDir, "crash.txt")
        return if (file.exists()) file.readText() else null
    }

    fun clearCrash() {
        File(filesDir, "crash.txt").delete()
    }

    private fun start(apiId: Int, apiHash: String) {
        try {
            repoFlow.value = TdRepo(this, apiId, apiHash)
            errorFlow.value = null
        } catch (t: Throwable) {
            // The native library failing to load must not take the whole app down.
            errorFlow.value = t.stackTraceToString()
        }
    }

    private fun loadKeys(): Pair<Int, String>? {
        if (BuildConfig.API_ID != 0 && BuildConfig.API_HASH.isNotEmpty()) {
            return BuildConfig.API_ID to BuildConfig.API_HASH
        }
        val prefs = prefs()
        val id = prefs.getInt("api_id", 0)
        val hash = prefs.getString("api_hash", "") ?: ""
        return if (id != 0 && hash.isNotEmpty()) id to hash else null
    }

    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(filesDir, "crash.txt").writeText(
                    "Thread: ${thread.name}\n" + throwable.stackTraceToString()
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun prefs() = getSharedPreferences("horusgram", Context.MODE_PRIVATE)

    companion object {
        lateinit var instance: HorusApp
            private set
    }
}
