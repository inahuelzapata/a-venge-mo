package com.splitparty.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.splitparty.android.data.AvatarImageStore
import com.splitparty.android.ui.components.LocalAvatarImages
import com.splitparty.android.ui.root.SplitPartyApp
import com.splitparty.android.ui.theme.SplitPartyTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val avatarImages: AvatarImageStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // As in the iOS captures: the page shows through a transparent status bar
        // with dark icons on every screen, whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            CompositionLocalProvider(LocalAvatarImages provides avatarImages) {
                SplitPartyTheme {
                    SplitPartyApp()
                }
            }
        }
    }
}
