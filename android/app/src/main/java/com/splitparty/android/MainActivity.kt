package com.splitparty.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.splitparty.android.ui.root.SplitPartyApp
import com.splitparty.android.ui.theme.SplitPartyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitPartyTheme {
                SplitPartyApp()
            }
        }
    }
}
