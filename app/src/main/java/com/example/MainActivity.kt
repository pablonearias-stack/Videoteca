package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.ui.PromptCutViewModel
import com.example.ui.StudioScreen
import com.example.ui.theme.PromptCutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            PromptCutTheme {
                val studioViewModel: PromptCutViewModel = viewModel()
                StudioScreen(viewModel = studioViewModel)
            }
        }
    }
}
