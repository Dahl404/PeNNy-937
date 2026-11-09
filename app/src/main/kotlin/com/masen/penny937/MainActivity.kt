package com.masen.penny937

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.masen.penny937.data.ReferenceFiles
import com.masen.penny937.ui.PennyApp
import com.masen.penny937.ui.theme.PennyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReferenceFiles.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            AppContent()
        }
    }

    @Composable
    private fun AppContent() {
        PennyTheme {
            PennyApp()
        }
    }
}
