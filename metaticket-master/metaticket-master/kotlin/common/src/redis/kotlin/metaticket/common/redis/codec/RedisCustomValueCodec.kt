package metaticket.common.redis.codec

import com.fasterxml.jackson.databind.ObjectMapper
import io.lettuce.core.codec.RedisCodec
import java.nio.ByteBuffer
import java.nio.charset.Charset
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 *
 * @author dongcatchay
 */
class RedisCustomValueCodec(private val jsonMapper: ObjectMapper) : RedisCodec<String, Any> {

    private val EMPTY = ByteArray(0)
    private val charset = Charset.defaultCharset()

    override fun decodeKey(bytes: ByteBuffer): String {
        return  charset.decode(bytes).toString()
    }

    override fun decodeValue(bytes: ByteBuffer): Any? {
        val ba = getBytes(bytes)

        if (ba.contentEquals(EMPTY)) return null

        val deserialized = ba.decodeToString()

        return jsonMapper.readValue(deserialized, Any::class.java)
    }

    override fun encodeKey(key: String): ByteBuffer {
        return charset.encode(key)
    }

    override fun encodeValue(value: Any?): ByteBuffer {
        value ?: return ByteBuffer.wrap(EMPTY)
        val serialized = jsonMapper.writeValueAsString(value)
        return ByteBuffer.wrap(serialized.encodeToByteArray())
    }

    private fun getBytes(buffer: ByteBuffer): ByteArray {
        val remaining = buffer.remaining()
        if (remaining == 0) {
            return EMPTY
        }
        val b = ByteArray(remaining)
        buffer[b]
        return b
    }
}
