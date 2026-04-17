package controlador.platform;

public record ServerValidationResult(
        boolean valid,
        String message
) {
    public static ServerValidationResult ok() {
        return new ServerValidationResult(true, null);
    }

    public static ServerValidationResult error(String message) {
        return new ServerValidationResult(false, message);
    }
}
