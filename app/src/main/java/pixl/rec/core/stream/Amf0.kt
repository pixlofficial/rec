package pixl.rec.core.stream

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.charset.StandardCharsets

/**
 * Action Message Format 0 (AMF0) serializer and deserializer.
 * Implements binary encoding/decoding for RTMP command flows and onMetaData headers.
 */
object Amf0 {
    const val TYPE_NUMBER: Byte = 0x00
    const val TYPE_BOOLEAN: Byte = 0x01
    const val TYPE_STRING: Byte = 0x02
    const val TYPE_OBJECT: Byte = 0x03
    const val TYPE_NULL: Byte = 0x05
    const val TYPE_UNDEFINED: Byte = 0x06
    const val TYPE_ECMA_ARRAY: Byte = 0x08
    const val TYPE_OBJECT_END: Byte = 0x09
    const val TYPE_STRICT_ARRAY: Byte = 0x0A
    const val TYPE_LONG_STRING: Byte = 0x0C

    /**
     * Serializes values into AMF0 binary representation.
     */
    class Writer {
        private val baos = ByteArrayOutputStream()
        private val dos = DataOutputStream(baos)

        fun writeNumber(value: Double): Writer {
            dos.writeByte(TYPE_NUMBER.toInt())
            dos.writeDouble(value)
            return this
        }

        fun writeBoolean(value: Boolean): Writer {
            dos.writeByte(TYPE_BOOLEAN.toInt())
            dos.writeByte(if (value) 1 else 0)
            return this
        }

        fun writeString(value: String): Writer {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size <= 65535) {
                dos.writeByte(TYPE_STRING.toInt())
                dos.writeShort(bytes.size)
                dos.write(bytes)
            } else {
                dos.writeByte(TYPE_LONG_STRING.toInt())
                dos.writeInt(bytes.size)
                dos.write(bytes)
            }
            return this
        }

        fun writeNull(): Writer {
            dos.writeByte(TYPE_NULL.toInt())
            return this
        }

        fun writeUndefined(): Writer {
            dos.writeByte(TYPE_UNDEFINED.toInt())
            return this
        }

        fun writeObject(properties: Map<String, Any?>): Writer {
            dos.writeByte(TYPE_OBJECT.toInt())
            for ((key, value) in properties) {
                writePropertyKey(key)
                writeValue(value)
            }
            writePropertyEnd()
            return this
        }

        fun writeEcmaArray(properties: Map<String, Any?>): Writer {
            dos.writeByte(TYPE_ECMA_ARRAY.toInt())
            dos.writeInt(properties.size)
            for ((key, value) in properties) {
                writePropertyKey(key)
                writeValue(value)
            }
            writePropertyEnd()
            return this
        }

        fun writeValue(value: Any?): Writer {
            when (value) {
                null -> writeNull()
                is Double -> writeNumber(value)
                is Float -> writeNumber(value.toDouble())
                is Number -> writeNumber(value.toDouble())
                is Boolean -> writeBoolean(value)
                is String -> writeString(value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    writeObject(value as Map<String, Any?>)
                }
                else -> writeString(value.toString())
            }
            return this
        }

        private fun writePropertyKey(key: String) {
            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
            dos.writeShort(keyBytes.size)
            dos.write(keyBytes)
        }

        private fun writePropertyEnd() {
            dos.writeShort(0) // 2-byte empty string
            dos.writeByte(TYPE_OBJECT_END.toInt())
        }

        fun toByteArray(): ByteArray {
            dos.flush()
            return baos.toByteArray()
        }
    }

    /**
     * Deserializes values from AMF0 binary stream.
     */
    class Reader(data: ByteArray) {
        private val bais = ByteArrayInputStream(data)
        private val dis = DataInputStream(bais)

        fun hasRemaining(): Boolean = bais.available() > 0

        fun readNext(): Any? {
            if (!hasRemaining()) return null
            val marker = dis.readByte()
            return when (marker) {
                TYPE_NUMBER -> dis.readDouble()
                TYPE_BOOLEAN -> dis.readByte() != 0.toByte()
                TYPE_STRING -> readStringPayload()
                TYPE_LONG_STRING -> readLongStringPayload()
                TYPE_NULL, TYPE_UNDEFINED -> null
                TYPE_OBJECT -> readObjectPayload()
                TYPE_ECMA_ARRAY -> {
                    val count = dis.readInt() // Associative count
                    readObjectPayload()
                }
                TYPE_STRICT_ARRAY -> {
                    val count = dis.readInt()
                    val list = mutableListOf<Any?>()
                    for (i in 0 until count) {
                        list.add(readNext())
                    }
                    list
                }
                TYPE_OBJECT_END -> null
                else -> null
            }
        }

        private fun readStringPayload(): String {
            val length = dis.readUnsignedShort()
            val bytes = ByteArray(length)
            dis.readFully(bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }

        private fun readLongStringPayload(): String {
            val length = dis.readInt()
            val bytes = ByteArray(length)
            dis.readFully(bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }

        private fun readObjectPayload(): Map<String, Any?> {
            val map = linkedMapOf<String, Any?>()
            while (true) {
                val keyLength = dis.readUnsignedShort()
                if (keyLength == 0) {
                    val endMarker = dis.readByte()
                    if (endMarker == TYPE_OBJECT_END) break
                }
                val keyBytes = ByteArray(keyLength)
                dis.readFully(keyBytes)
                val key = String(keyBytes, StandardCharsets.UTF_8)
                val value = readNext()
                map[key] = value
            }
            return map
        }
    }

    // --- High-Level RTMP Command Encoders ---

    /**
     * Encodes NetConnection.connect AMF0 command packet payload.
     */
    fun encodeConnect(
        transactionId: Double = 1.0,
        app: String,
        tcUrl: String,
        flashVer: String = "FMLE/3.0 (compatible; PixL REC)",
        swfUrl: String? = null
    ): ByteArray {
        val writer = Writer()
            .writeString("connect")
            .writeNumber(transactionId)

        val commandObject = mutableMapOf<String, Any?>(
            "app" to app,
            "flashVer" to flashVer,
            "tcUrl" to tcUrl,
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 0x0400.toDouble(), // AAC supported
            "videoCodecs" to 0x0080.toDouble(), // AVC / H.264 supported
            "videoFunction" to 1.0
        )
        if (swfUrl != null) {
            commandObject["swfUrl"] = swfUrl
        }
        writer.writeObject(commandObject)
        return writer.toByteArray()
    }

    /**
     * Encodes releaseStream command (FMS / NGINX-RTMP standard for stream key claiming).
     */
    fun encodeReleaseStream(transactionId: Double = 2.0, streamKey: String): ByteArray {
        return Writer()
            .writeString("releaseStream")
            .writeNumber(transactionId)
            .writeNull()
            .writeString(streamKey)
            .toByteArray()
    }

    /**
     * Encodes FCPublish command (Flash Communication Publishing verification).
     */
    fun encodeFCPublish(transactionId: Double = 3.0, streamKey: String): ByteArray {
        return Writer()
            .writeString("FCPublish")
            .writeNumber(transactionId)
            .writeNull()
            .writeString(streamKey)
            .toByteArray()
    }

    /**
     * Encodes createStream command.
     */
    fun encodeCreateStream(transactionId: Double = 4.0): ByteArray {
        return Writer()
            .writeString("createStream")
            .writeNumber(transactionId)
            .writeNull()
            .toByteArray()
    }

    /**
     * Encodes publish command.
     */
    fun encodePublish(
        transactionId: Double = 5.0,
        streamKey: String,
        publishType: String = "live"
    ): ByteArray {
        return Writer()
            .writeString("publish")
            .writeNumber(transactionId)
            .writeNull()
            .writeString(streamKey)
            .writeString(publishType)
            .toByteArray()
    }

    /**
     * Encodes FCUnpublish command for teardown.
     */
    fun encodeFCUnpublish(transactionId: Double, streamKey: String): ByteArray {
        return Writer()
            .writeString("FCUnpublish")
            .writeNumber(transactionId)
            .writeNull()
            .writeString(streamKey)
            .toByteArray()
    }

    /**
     * Encodes deleteStream command for teardown.
     */
    fun encodeDeleteStream(transactionId: Double, streamId: Double): ByteArray {
        return Writer()
            .writeString("deleteStream")
            .writeNumber(transactionId)
            .writeNull()
            .writeNumber(streamId)
            .toByteArray()
    }

    /**
     * Encodes @setDataFrame / onMetaData message.
     */
    fun encodeOnMetaData(
        width: Int,
        height: Int,
        fps: Int,
        videoBitrateKbps: Int,
        audioBitrateKbps: Int,
        sampleRate: Int,
        videoCodec: String = "avc1", // or "hvc1" for Enhanced RTMP
        audioCodec: String = "mp4a"
    ): ByteArray {
        val meta = linkedMapOf<String, Any?>(
            "width" to width.toDouble(),
            "height" to height.toDouble(),
            "framerate" to fps.toDouble(),
            "videodatarate" to videoBitrateKbps.toDouble(),
            "videocodecid" to if (videoCodec == "hvc1") "hvc1" else 7.0, // 7 = AVC
            "audiodatarate" to audioBitrateKbps.toDouble(),
            "audiosamplerate" to sampleRate.toDouble(),
            "audiosamplesize" to 16.0,
            "audiocodecid" to 10.0, // 10 = AAC
            "stereo" to true,
            "encoder" to "PixL REC Studio"
        )

        return Writer()
            .writeString("@setDataFrame")
            .writeString("onMetaData")
            .writeEcmaArray(meta)
            .toByteArray()
    }

    /**
     * Parsed RTMP server command response (e.g. _result, onStatus).
     */
    data class CommandResponse(
        val commandName: String,
        val transactionId: Double,
        val properties: Any?,
        val information: Any?
    ) {
        val statusLevel: String?
            get() = (information as? Map<*, *>)?.get("level") as? String

        val statusCode: String?
            get() = (information as? Map<*, *>)?.get("code") as? String

        val streamId: Double?
            get() = (information as? Double) ?: (properties as? Double)
    }

    /**
     * Parses an AMF0 command packet received from the RTMP server.
     */
    fun parseCommand(payload: ByteArray): CommandResponse? {
        val reader = Reader(payload)
        val commandName = reader.readNext() as? String ?: return null
        val transactionId = (reader.readNext() as? Number)?.toDouble() ?: 0.0
        val properties = reader.readNext()
        val information = reader.readNext()

        return CommandResponse(
            commandName = commandName,
            transactionId = transactionId,
            properties = properties,
            information = information
        )
    }
}
