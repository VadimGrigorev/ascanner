package com.tsd.ascanner.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tsd.ascanner.R

/** One app-lifetime player. All playback state is confined to the main thread. */
class ServerSoundPlayer(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var pool: SoundPool? = null
    private var errorSoundId = 0
    private var streamId = 0
    private var ready = false
    private var pendingError = false

    init {
        val appContext = context.applicationContext
        handler.post {
            safely {
                val soundPool = SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                pool = soundPool
                soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                    handler.post {
                        safely {
                            if (sampleId == errorSoundId) {
                                ready = status == 0
                                if (!ready) {
                                    Log.w("ServerSoundPlayer", "Error sound failed to load: $status")
                                }
                                val shouldPlay = ready && pendingError
                                pendingError = false
                                if (shouldPlay) playError()
                            }
                        }
                    }
                }
                errorSoundId = soundPool.load(appContext, R.raw.error, 1)
            }
        }
    }

    fun play(name: String) {
        if (!name.trim().equals("error", ignoreCase = true)) return
        handler.post {
            safely {
                if (ready) playError()
                else if (errorSoundId != 0) pendingError = true
            }
        }
    }

    private fun playError() {
        val soundPool = pool ?: return
        if (streamId != 0) soundPool.stop(streamId)
        streamId = soundPool.play(errorSoundId, 1f, 1f, 1, 0, 1f)
    }

    private inline fun safely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.w("ServerSoundPlayer", "Unable to play server sound", e)
        }
    }
}
