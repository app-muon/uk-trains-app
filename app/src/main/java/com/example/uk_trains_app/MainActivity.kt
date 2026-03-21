package com.example.uk_trains_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.uk_trains_app.ui.navigation.AppNavigation
import com.example.uk_trains_app.ui.theme.UktrainsappTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UktrainsappTheme {
                AppNavigation()
            }
        }
    }
}
