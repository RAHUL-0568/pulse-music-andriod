package com.pulse.music.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PlaybackService : Service() {
    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
