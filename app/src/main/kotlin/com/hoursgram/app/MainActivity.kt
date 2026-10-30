package com.hoursgram.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

@Composable
fun Modifier.androidxScroll(): Modifier = this.verticalScroll(rememberScrollState())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
fun App() {
    val app = HorusApp.instance
    var palette by remember {
        val saved = app.prefs().getString("palette", null)
        mutableStateOf(Palettes.firstOrNull { it.name == saved } ?: Palettes[0])
    }
    var crash by remember { mutableStateOf(app.lastCrash()) }
    val repo by app.repo.collectAsState()
    val error by app.startError.collectAsState()

    HgTheme(palette) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                val current = repo
                val failure = error
                when {
                    failure != null -> StartErrorScreen(failure)
                    current == null -> ApiKeysScreen { id, hash -> app.saveKeys(id, hash) }
                    else -> MainFlow(current, palette) {
                        palette = it
                        app.prefs().edit().putString("palette", it.name).apply()
                    }
                }
                crash?.let {
                    CrashDialog(it) {
                        app.clearCrash()
                        crash = null
                    }
                }
            }
        }
    }
}
