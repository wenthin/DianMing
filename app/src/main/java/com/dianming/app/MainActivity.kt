package com.dianming.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dianming.app.ui.nav.AppNav
import com.dianming.app.ui.theme.DianMingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DianMingTheme {
                AppNav()
            }
        }
    }
}
