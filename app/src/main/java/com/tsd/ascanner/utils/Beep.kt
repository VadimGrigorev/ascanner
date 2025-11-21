package com.tsd.ascanner.utils

import android.media.AudioManager
import android.media.ToneGenerator

class BeepPlayer {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    fun success() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    fun error() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 250)
    }

    fun release() {
        toneGenerator.release()
    }
}


