package metaticket.common.mongodb.codec;

import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

/**
 * @author dongcatchay
 */
public class EnumCodecProvider implements CodecProvider {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        if (Enum.class.isAssignableFrom(clazz)) {
            return new EnumCodec(clazz);
        }
        return null;
    }

}
