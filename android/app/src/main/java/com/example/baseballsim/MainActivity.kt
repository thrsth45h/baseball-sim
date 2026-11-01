package com.example.baseballsim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

class MainActivity : ComponentActivity() {
    val matchViewModel: MatchViewModel by lazy {
        ViewModelProvider(this, MatchViewModel.Factory(NativeGamePortFactory))[MatchViewModel::class.java]
    }
    val pennantViewModel: PennantViewModel by lazy {
        ViewModelProvider(this, PennantViewModel.Factory(PennantRuntime.actions(applicationContext)))[PennantViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.BLACK),
        )
        setContent {
            BaseballTheme {
                var pennant by rememberSaveable { mutableStateOf(false) }
                Scaffold(topBar = {
                    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                        TextButton({ pennant = false }, Modifier.testTag("mode_match")) { Text("1試合") }
                        TextButton({ pennant = true }, Modifier.testTag("mode_pennant")) { Text("ペナント") }
                    }
                }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        if (pennant) PennantRoute(pennantViewModel) else MatchRoute(matchViewModel)
                    }
                }
            }
        }
    }
}
