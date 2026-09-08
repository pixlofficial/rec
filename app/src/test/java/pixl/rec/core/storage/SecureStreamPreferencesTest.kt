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

class SecureStreamPreferencesTest {

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

        every { sharedPreferences.edit() } returns editor

        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String?>()
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
    fun testSaveAndRetrieveStreamKey() {
        val testKey = "live_839210482_pixlStreamSecret123"
        SecureStreamPreferences.saveStreamKey(context, testKey)

        assertTrue(SecureStreamPreferences.hasStreamKey(context))
        val retrieved = SecureStreamPreferences.getStreamKey(context)
        assertEquals(testKey, retrieved)

        // Ensure key is NOT stored in plaintext inside the underlying prefs map
        val rawStored = inMemoryPrefs["encrypted_stream_key"] as? String
        assertTrue(rawStored != null)
        assertFalse(rawStored == testKey)
    }

    @Test
    fun testClearStreamKey() {
        SecureStreamPreferences.saveStreamKey(context, "temporary_key_to_delete")
        assertTrue(SecureStreamPreferences.hasStreamKey(context))

        SecureStreamPreferences.clearStreamKey(context)
        assertFalse(SecureStreamPreferences.hasStreamKey(context))
        assertEquals("", SecureStreamPreferences.getStreamKey(context))
    }

    @Test
    fun testSaveBlankKeyClearsStorage() {
        SecureStreamPreferences.saveStreamKey(context, "initial_key")
        assertTrue(SecureStreamPreferences.hasStreamKey(context))

        SecureStreamPreferences.saveStreamKey(context, "   ")
        assertFalse(SecureStreamPreferences.hasStreamKey(context))
        assertEquals("", SecureStreamPreferences.getStreamKey(context))
    }

    @Test
    fun testSpecialCharactersAndLongStreamKey() {
        val longComplexKey = "live_sk_?token=abc-123_XYZ==!@#\$%^&*()_+~"
        SecureStreamPreferences.saveStreamKey(context, longComplexKey)

        assertEquals(longComplexKey, SecureStreamPreferences.getStreamKey(context))
    }
}
