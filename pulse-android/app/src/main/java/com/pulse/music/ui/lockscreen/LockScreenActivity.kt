package com.pulse.music.ui.lockscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pulse.music.MainActivity
import com.pulse.music.player.MusicPlayerManager
import com.pulse.music.ui.theme.PulseMusicTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LockScreenActivity : ComponentActivity() {

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Check if device is actually locked, if not, redirect to MainActivity
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isKeyguardLocked) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            PulseMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    LockScreenPlayer(
                        musicPlayerManager = musicPlayerManager,
                        onUnlock = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                keyguardManager.requestDismissKeyguard(this@LockScreenActivity, object : KeyguardManager.KeyguardDismissCallback() {
                                    override fun onDismissSucceeded() {
                                        startActivity(Intent(this@LockScreenActivity, MainActivity::class.java))
                                        finish()
                                    }
                                })
                            } else {
                                // API 24-25 has no requestDismissKeyguard
                                @Suppress("DEPRECATION")
                                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                                startActivity(Intent(this@LockScreenActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }
}
