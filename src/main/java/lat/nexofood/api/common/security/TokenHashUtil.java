package lat.nexofood.api.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilidad para generar hashes SHA-256 de tokens de refresco.
 * Los tokens se almacenan hasheados en BD para que un volcado
 * de la tabla no exponga tokens válidos.
 */
public final class TokenHashUtil {

    private TokenHashUtil() {}

    /**
     * Calcula el hash SHA-256 de un token y lo devuelve como string hexadecimal (64 chars).
     */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                String hexByte = Integer.toHexString(0xff & b);
                if (hexByte.length() == 1) hex.append('0');
                hex.append(hexByte);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
