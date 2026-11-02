package com.tsd.ascanner.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tsd.ascanner.R
import java.util.Locale

/** One app-lifetime player. All playback state is confined to the main thread. */
class ServerSoundPlayer(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()
    private val readySoundIds = mutableSetOf<Int>()
    private val failedSoundIds = mutableSetOf<Int>()
    private var streamId = 0
    private var pendingSoundName: String? = null

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
                            if (status == 0) {
                                readySoundIds.add(sampleId)
                            } else {
                                failedSoundIds.add(sampleId)
                                Log.w("ServerSoundPlayer", "Sound $sampleId failed to load: $status")
                            }
                            if (soundIds[pendingSoundName] == sampleId) {
                                pendingSoundName = null
                                if (status == 0) playSample(sampleId)
                            }
                        }
                    }
                }
                soundIds["error1"] = soundPool.load(appContext, R.raw.error1, 1)
                soundIds["error2"] = soundPool.load(appContext, R.raw.error2, 1)
                soundIds["error3"] = soundPool.load(appContext, R.raw.error3, 1)
            }
        }
    }

    fun play(name: String) {
        val soundName = when (val normalized = name.trim().lowercase(Locale.ROOT)) {
            "error" -> "error2" // Preserve the original server contract.
            "error1", "error2", "error3" -> normalized
            else -> return
        }
        handler.post {
            safely {
                // Only the latest requested sound may play after asynchronous loading.
                pendingSoundName = null
                val sampleId = soundIds[soundName] ?: return@safely
                if (sampleId == 0 || sampleId in failedSoundIds) return@safely
                if (sampleId in readySoundIds) playSample(sampleId)
                else pendingSoundName = soundName
            }
        }
    }

    private fun playSample(sampleId: Int) {
        val soundPool = pool ?: return
        if (streamId != 0) soundPool.stop(streamId)
        streamId = soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
    }

    private inline fun safely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.w("ServerSoundPlayer", "Unable to play server sound", e)
        }
    }
}
