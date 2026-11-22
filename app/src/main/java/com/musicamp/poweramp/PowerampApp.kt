package com.musicamp.poweramp

import android.app.Application

/**
 * Owns the single [PowerampController] for the whole process so playback keeps running
 * (and the notification / media session stay in sync) when the Activity is closed.
 */
class PowerampApp : Application() {
    lateinit var controller: PowerampController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = PowerampController(this)
    }
}
