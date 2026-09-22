package lat.nexofood.api.common.constants;

public class ErrorMessages {

    private ErrorMessages() {
    }

    public static final String USER_NOT_FOUND = "Usuario no encontrado";
    public static final String EMAIL_ALREADY_EXISTS = "El correo electrónico ya está registrado";
    public static final String INVALID_CREDENTIALS = "Las credenciales proporcionadas son incorrectas";
    public static final String USER_INACTIVE = "La cuenta de usuario está desactivada";
    public static final String INVALID_REFRESH_TOKEN = "El token de refresco es inválido o ha expirado";
    public static final String TOKEN_REVOKED = "El token de refresco ha sido revocado";
}
