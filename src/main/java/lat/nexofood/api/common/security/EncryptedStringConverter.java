package lat.nexofood.api.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lat.nexofood.api.common.config.ApplicationContextProvider;

/**
 * JPA AttributeConverter que encripta/desencripta Strings usando AES-256-GCM
 * de forma transparente al pasar datos entre la entidad JPA y la base de datos.
 *
 * La columna en BD es de tipo BYTEA; en la entidad JPA sigue siendo String.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

    private CryptoService getCryptoService() {
        return ApplicationContextProvider.getBean(CryptoService.class);
    }

    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return null;
        }
        return getCryptoService().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null || dbData.length == 0) {
            return null;
        }
        return getCryptoService().decrypt(dbData);
    }
}
