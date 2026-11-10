package lat.nexofood.api.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Servicio de encriptación AES-256-GCM para proteger credenciales sensibles
 * (tokens de Mercado Pago) en reposo.
 *
 * El IV de 12 bytes se antepone al ciphertext en el arreglo de bytes resultante:
 * [IV (12 bytes)] + [ciphertext + GCM tag]
 */
@Service
public class CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;

    public CryptoService(@Value("${nexofood.crypto.master-key}") String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        if (decodedKey.length != 32) {
            throw new IllegalArgumentException(
                "La clave maestra AES-256 debe tener exactamente 32 bytes (256 bits) en Base64."
            );
        }
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    /**
     * Encripta un String en texto plano y devuelve el resultado como bytes.
     * El formato es: [IV 12 bytes][ciphertext + GCM tag].
     */
    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Error al encriptar el valor.", e);
        }
    }

    /**
     * Desencripta los bytes (formato [IV][ciphertext+tag]) y devuelve el texto plano.
     */
    public String decrypt(byte[] encryptedData) {
        try {
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(encryptedData, IV_LENGTH_BYTES, encryptedData.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Error al desencriptar el valor.", e);
        }
    }
}
