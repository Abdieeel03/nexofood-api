package lat.nexofood.api.common.constants;

public class ErrorMessages {

    private ErrorMessages() {
    }

    public static final String USER_NOT_FOUND = "Usuario no encontrado";
    public static final String EMAIL_ALREADY_EXISTS = "El correo electrónico ya está registrado";
    public static final String INVALID_CREDENTIALS = "Las credenciales proporcionadas son incorrectas";
    public static final String USER_INACTIVE = "La cuenta de usuario está desactivada";
    public static final String ACCOUNT_LOCKED = "La cuenta se encuentra bloqueada temporalmente por múltiples intentos fallidos. Intente más tarde";
    public static final String WEAK_PASSWORD = "La contraseña debe tener entre 8 y 100 caracteres, incluir al menos una letra y un número, y no contener espacios";
    public static final String INVALID_REFRESH_TOKEN = "El token de refresco es inválido o ha expirado";
    public static final String TOKEN_REVOKED = "El token de refresco ha sido revocado";
    public static final String RATE_LIMIT_EXCEEDED = "Demasiadas solicitudes de autenticación. Por favor, intente nuevamente más tarde";
}
