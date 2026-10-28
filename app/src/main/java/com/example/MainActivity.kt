package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.AppScreen
import com.example.ui.DiagnosticViewModel
import com.example.ui.LogKeeperScreen
import com.example.ui.VianshieldHomeScreen
import com.example.ui.VianshieldSettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val diagnosticViewModel: DiagnosticViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val currentScreen by diagnosticViewModel.currentScreen.collectAsState()

                when (currentScreen) {
                    AppScreen.MAIN -> {
                        VianshieldHomeScreen(
                            onOpenSettings = { diagnosticViewModel.navigateTo(AppScreen.SETTINGS) },
                            onOpenLogKeeper = { diagnosticViewModel.navigateTo(AppScreen.LOG_KEEPER) }
                        )
                    }
                    AppScreen.SETTINGS -> {
                        VianshieldSettingsScreen(
                            onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) }
                        )
                    }
                    AppScreen.LOG_KEEPER -> {
                        LogKeeperScreen(
                            viewModel = diagnosticViewModel,
                            onBack = { diagnosticViewModel.navigateTo(AppScreen.MAIN) }
                        )
                    }
                }
            }
        }
    }
}
