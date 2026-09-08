package pixl.rec.core.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Amf0Test {

    @Test
    fun testPrimitivesRoundtrip() {
        val writer = Amf0.Writer()
            .writeNumber(42.5)
            .writeBoolean(true)
            .writeBoolean(false)
            .writeString("PixL REC")
            .writeNull()

        val reader = Amf0.Reader(writer.toByteArray())

        assertEquals(42.5, reader.readNext() as Double, 0.0001)
        assertEquals(true, reader.readNext() as Boolean)
        assertEquals(false, reader.readNext() as Boolean)
        assertEquals("PixL REC", reader.readNext() as String)
        assertNull(reader.readNext())
        assertNull(reader.readNext()) // End of stream
    }

    @Test
    fun testObjectSerialization() {
        val properties = linkedMapOf<String, Any?>(
            "app" to "live2",
            "tcUrl" to "rtmp://localhost/live2",
            "fpad" to false,
            "capabilities" to 15.0
        )

        val bytes = Amf0.Writer().writeObject(properties).toByteArray()
        val reader = Amf0.Reader(bytes)

        @Suppress("UNCHECKED_CAST")
        val decoded = reader.readNext() as Map<String, Any?>

        assertNotNull(decoded)
        assertEquals("live2", decoded["app"])
        assertEquals("rtmp://localhost/live2", decoded["tcUrl"])
        assertEquals(false, decoded["fpad"])
        assertEquals(15.0, decoded["capabilities"] as Double, 0.0001)
    }

    @Test
    fun testEcmaArraySerialization() {
        val meta = linkedMapOf<String, Any?>(
            "width" to 1920.0,
            "height" to 1080.0,
            "framerate" to 60.0,
            "videocodecid" to 7.0
        )

        val bytes = Amf0.Writer().writeEcmaArray(meta).toByteArray()
        val reader = Amf0.Reader(bytes)

        @Suppress("UNCHECKED_CAST")
        val decoded = reader.readNext() as Map<String, Any?>

        assertNotNull(decoded)
        assertEquals(1920.0, decoded["width"] as Double, 0.0001)
        assertEquals(1080.0, decoded["height"] as Double, 0.0001)
        assertEquals(60.0, decoded["framerate"] as Double, 0.0001)
        assertEquals(7.0, decoded["videocodecid"] as Double, 0.0001)
    }

    @Test
    fun testConnectCommandPayload() {
        val bytes = Amf0.encodeConnect(
            transactionId = 1.0,
            app = "live",
            tcUrl = "rtmp://a.rtmp.youtube.com/live2"
        )

        val reader = Amf0.Reader(bytes)
        assertEquals("connect", reader.readNext())
        assertEquals(1.0, reader.readNext() as Double, 0.0001)

        @Suppress("UNCHECKED_CAST")
        val obj = reader.readNext() as Map<String, Any?>
        assertEquals("live", obj["app"])
        assertEquals("rtmp://a.rtmp.youtube.com/live2", obj["tcUrl"])
        assertEquals("FMLE/3.0 (compatible; PixL REC)", obj["flashVer"])
    }

    @Test
    fun testPublishCommandPayload() {
        val bytes = Amf0.encodePublish(
            transactionId = 5.0,
            streamKey = "test-secret-stream-key",
            publishType = "live"
        )

        val reader = Amf0.Reader(bytes)
        assertEquals("publish", reader.readNext())
        assertEquals(5.0, reader.readNext() as Double, 0.0001)
        assertNull(reader.readNext()) // null command object
        assertEquals("test-secret-stream-key", reader.readNext())
        assertEquals("live", reader.readNext())
    }

    @Test
    fun testOnMetaDataPayload() {
        val bytes = Amf0.encodeOnMetaData(
            width = 2560,
            height = 1440,
            fps = 120,
            videoBitrateKbps = 18000,
            audioBitrateKbps = 192,
            sampleRate = 48000,
            videoCodec = "hvc1"
        )

        val reader = Amf0.Reader(bytes)
        assertEquals("@setDataFrame", reader.readNext())
        assertEquals("onMetaData", reader.readNext())

        @Suppress("UNCHECKED_CAST")
        val meta = reader.readNext() as Map<String, Any?>
        assertEquals(2560.0, meta["width"] as Double, 0.0001)
        assertEquals(1440.0, meta["height"] as Double, 0.0001)
        assertEquals(120.0, meta["framerate"] as Double, 0.0001)
        assertEquals("hvc1", meta["videocodecid"])
        assertEquals(10.0, meta["audiocodecid"] as Double, 0.0001)
    }

    @Test
    fun testParseCommandResponse_result() {
        // Build mock _result packet
        val bytes = Amf0.Writer()
            .writeString("_result")
            .writeNumber(4.0) // createStream transactionId
            .writeNull()
            .writeNumber(1.0) // assigned streamId
            .toByteArray()

        val resp = Amf0.parseCommand(bytes)
        assertNotNull(resp)
        assertEquals("_result", resp?.commandName)
        assertEquals(4.0, resp?.transactionId ?: 0.0, 0.0001)
        assertEquals(1.0, resp?.streamId ?: 0.0, 0.0001)
    }

    @Test
    fun testParseCommandResponse_onStatus() {
        val info = mapOf(
            "level" to "status",
            "code" to "NetStream.Publish.Start",
            "description" to "Publishing test stream"
        )

        val bytes = Amf0.Writer()
            .writeString("onStatus")
            .writeNumber(0.0)
            .writeNull()
            .writeObject(info)
            .toByteArray()

        val resp = Amf0.parseCommand(bytes)
        assertNotNull(resp)
        assertEquals("onStatus", resp?.commandName)
        assertEquals("status", resp?.statusLevel)
        assertEquals("NetStream.Publish.Start", resp?.statusCode)
    }
}
