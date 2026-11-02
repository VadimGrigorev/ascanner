package com.tsd.ascanner

import android.media.SoundPool
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tsd.ascanner.data.net.ApiClient
import com.tsd.ascanner.utils.ServerErrorResponseException
import com.tsd.ascanner.utils.ServerSoundPlayer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException

@RunWith(AndroidJUnit4::class)
class ServerSoundPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: ServerSoundPlayer

    @Before
    fun loadAudioOnDevice() {
        player = ServerSoundPlayer(instrumentation.targetContext)
        val deadline = SystemClock.uptimeMillis() + 5000
        while (state<Set<Int>>("readySoundIds").size < 3 && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20)
        }
        assertEquals("All three MP3s must decode and load on this device", 3, state<Set<Int>>("readySoundIds").size)
    }

    @After
    fun releaseTestPlayer() {
        instrumentation.runOnMainSync {
            val field = ServerSoundPlayer::class.java.getDeclaredField("pool")
            field.isAccessible = true
            (field.get(player) as? SoundPool)?.release()
        }
    }

    @Test
    fun allThreeServerSoundNamesPlayDistinctSamples() {
        val client = ApiClient(onSound = player::play)
        val ids = state<Map<String, Int>>("soundIds")
        assertEquals(3, ids.values.toSet().size)
        var previousStream = 0
        for (name in listOf("error1", "error2", "error3")) {
            process(client, """{"MessageType":"refresh","Sound":"$name"}""")
            val stream = state<Int>("streamId")
            assertTrue("$name must start playback", stream > 0)
            assertNotEquals(previousStream, stream)
            previousStream = stream
            SystemClock.sleep(2100)
        }
    }

    @Test
    fun responsePlaysAudioAndRepeatedResponseRestartsIt() {
        var dispatches = 0
        val client = ApiClient(onSound = { dispatches++; player.play(it) })
        process(client, """{"MessageType":"refresh","Form":"doc","Sound":"error"}""")
        val firstStream = state<Int>("streamId")
        assertTrue("SoundPool must accept playback", firstStream > 0)
        SystemClock.sleep(200)
        process(client, """{"MessageType":"refresh","Sound":"error"}""")
        val secondStream = state<Int>("streamId")
        assertTrue(secondStream > 0)
        assertNotEquals(firstStream, secondStream)
        assertEquals(2, dispatches)
        SystemClock.sleep(1800)
    }

    @Test
    fun missingMalformedAndUnsupportedSoundsStaySilent() {
        val client = ApiClient(onSound = player::play)
        for (response in listOf(
            "{}", """{"Sound":null}""", """{"Sound":42}""",
            """{"Sound":true}""", """{"Sound":{}}""", """{"Sound":[]}""",
            """{"Sound":""}""", """{"Sound":"success"}""",
            """{"Sound":"warning"}""", """{"Sound":"unknown"}"""
        )) process(client, response)
        assertEquals(0, state<Int>("streamId"))
    }

    @Test
    fun errorResponseStillThrowsWithMetadataAndPlaysSound() {
        val client = ApiClient(onSound = player::play)
        try {
            process(client, """{"MessageType":"error","Message":"test error","SelectedId":"row-1","Sound":"error"}""")
            fail("Server error must retain its original behavior")
        } catch (e: ServerErrorResponseException) {
            assertEquals("test error", e.message)
            assertEquals("row-1", e.selectedId)
        }
        assertTrue(state<Int>("streamId") > 0)
        SystemClock.sleep(1800)
    }

    @Test
    fun playbackFailureDoesNotReplaceServerError() {
        val client = ApiClient(onSound = { throw IllegalStateException("audio unavailable") })
        try {
            process(client, """{"MessageType":"error","Message":"original error","Sound":"error"}""")
            fail("Expected original server error")
        } catch (e: ServerErrorResponseException) {
            assertEquals("original error", e.message)
        }
    }

    // Exercise the shared response handler without contacting the user's server.
    private fun process(client: ApiClient, json: String) {
        val method = ApiClient::class.java.getDeclaredMethod(
            "throwIfServerError", String::class.java, Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        try {
            method.invoke(client, json, false)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> state(name: String): T {
        var result: Any? = null
        instrumentation.runOnMainSync {
            val field = ServerSoundPlayer::class.java.getDeclaredField(name)
            field.isAccessible = true
            result = field.get(player)
        }
        return result as T
    }
}
