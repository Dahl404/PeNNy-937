package com.dahl404.penny937

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.dahl404.penny937.data.ReferenceFiles
import com.dahl404.penny937.ui.PennyApp
import com.dahl404.penny937.ui.theme.PennyTheme

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
