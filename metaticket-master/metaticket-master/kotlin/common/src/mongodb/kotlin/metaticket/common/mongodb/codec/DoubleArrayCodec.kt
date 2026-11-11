package metaticket.common.mongodb.codec

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class DoubleArrayCodec: Codec<DoubleArray> {

    override fun encode(writer: BsonWriter, value: DoubleArray, encoderContext: EncoderContext) {
        writer.writeStartArray()
        for (v in value) {
            writer.writeDouble(v)
        }
        writer.writeEndArray()
    }

    override fun getEncoderClass(): Class<DoubleArray> {
       return DoubleArray::class.java
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): DoubleArray {
        val list = ArrayList<Double>()
        reader.readStartArray()
        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            list.add(reader.readDouble())
        }
        reader.readEndArray()
        return list.toDoubleArray()
    }
}
