package com.orotrain.oro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.orotrain.oro.ui.OroApp
import com.orotrain.oro.ui.theme.OroTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OroApp(viewModel = viewModel)
                }
            }
        }
    }
}

