package pixl.rec.core.storage

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamPlatform

class ConfigPreferencesStreamTest {

    private val inMemoryPrefs = mutableMapOf<String, Any?>()
    private val context = mockk<Context>()
    private val sharedPreferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()

    @Before
    fun setUp() {
        inMemoryPrefs.clear()

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences

        every { sharedPreferences.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<String?>()
            (inMemoryPrefs[key] as? String) ?: def
        }

        every { sharedPreferences.getInt(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<Int>()
            (inMemoryPrefs[key] as? Int) ?: def
        }

        every { sharedPreferences.getBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<Boolean>()
            (inMemoryPrefs[key] as? Boolean) ?: def
        }

        every { sharedPreferences.edit() } returns editor

        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String?>()
            inMemoryPrefs[key] = value
            editor
        }

        every { editor.putInt(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Int>()
            inMemoryPrefs[key] = value
            editor
        }

        every { editor.putBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Boolean>()
            inMemoryPrefs[key] = value
            editor
        }

        every { editor.remove(any()) } answers {
            val key = firstArg<String>()
            inMemoryPrefs.remove(key)
            editor
        }

        every { editor.apply() } returns Unit
    }

    @Test
    fun testStudioModePersistence() {
        // Defaults to RECORD
        assertEquals(StudioMode.RECORD, ConfigPreferences.getStudioMode(context))

        // Switch to STREAM
        ConfigPreferences.setStudioMode(context, StudioMode.STREAM)
        assertEquals(StudioMode.STREAM, ConfigPreferences.getStudioMode(context))

        // Switch back to RECORD
        ConfigPreferences.setStudioMode(context, StudioMode.RECORD)
        assertEquals(StudioMode.RECORD, ConfigPreferences.getStudioMode(context))
    }

    @Test
    fun testLiveStreamingMasterDisarmToggle() {
        // Defaults to enabled (true)
        assertTrue(ConfigPreferences.isLiveStreamingEnabled(context))

        // Disarm live streaming
        ConfigPreferences.setLiveStreamingEnabled(context, false)
        assertFalse(ConfigPreferences.isLiveStreamingEnabled(context))

        // Re-arm live streaming
        ConfigPreferences.setLiveStreamingEnabled(context, true)
        assertTrue(ConfigPreferences.isLiveStreamingEnabled(context))
    }

    @Test
    fun testStreamConfigSaveAndLoad() {
        val config = StreamConfig(
            platform = StreamPlatform.TWITCH,
            customEndpointUrl = "",
            streamKey = "live_twitch_key_xyz",
            videoBitrate = 6_000_000,
            enableAbr = true,
            minBitrate = 2_000_000,
            maxBitrate = 8_000_000,
            saveLocalMasterArchive = false,
            useEnhancedHevc = false
        )

        ConfigPreferences.saveStreamConfig(context, config)

        val loaded = ConfigPreferences.loadStreamConfig(context)
        assertEquals(StreamPlatform.TWITCH, loaded.platform)
        assertEquals(6_000_000, loaded.videoBitrate)
        assertTrue(loaded.enableAbr)
        assertFalse(loaded.saveLocalMasterArchive)
        assertFalse(loaded.useEnhancedHevc)
        assertEquals("live_twitch_key_xyz", loaded.streamKey)
    }
}
