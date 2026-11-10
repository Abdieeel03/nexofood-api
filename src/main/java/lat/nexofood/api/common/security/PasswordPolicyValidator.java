package lat.nexofood.api.common.security;

import lat.nexofood.api.common.constants.ErrorMessages;
import lat.nexofood.api.common.exception.BadRequestException;

import java.util.Locale;

/**
 * Utilidad de seguridad para normalización de correos electrónicos y validación
 * de política de contraseñas.
 */
public final class PasswordPolicyValidator {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 100;

    private PasswordPolicyValidator() {
    }

    /**
     * Normaliza el correo electrónico aplicando {@code trim()} y convirtiendo a minúsculas.
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Verifica que la contraseña cumpla la política de seguridad:
     * longitud entre 8 y 100 caracteres, sin espacios en blanco, al menos una letra y un dígito.
     */
    public static void validatePassword(String password) {
        if (password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BadRequestException(ErrorMessages.WEAK_PASSWORD);
        }

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isWhitespace(ch)) {
                throw new BadRequestException(ErrorMessages.WEAK_PASSWORD);
            }
            if (Character.isLetter(ch)) {
                hasLetter = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }

        if (!hasLetter || !hasDigit) {
            throw new BadRequestException(ErrorMessages.WEAK_PASSWORD);
        }
    }
}
