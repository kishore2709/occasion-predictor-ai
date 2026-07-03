package org.kishorereddy.occasionpredictor.security;

/**
 * Masks PII fields before they appear in log output.
 * Use on any field that contains personal data (name, message, email, etc.).
 */
public final class PiiMasker {

    private PiiMasker() {}

    /** "Sarah" → "S***h".  Short names (≤2 chars) are fully masked. */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) return "***";
        if (name.length() <= 2) return "*".repeat(name.length());
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /** Free-text messages are fully suppressed; only the character count is retained. */
    public static String maskMessage(String message) {
        if (message == null || message.isBlank()) return null;
        return "[REDACTED:" + message.length() + "chars]";
    }

    /** Masks everything after the first '@': "sarah@example.com" → "s****@***.***" */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String local = email.substring(0, email.indexOf('@'));
        return (local.isEmpty() ? "*" : local.charAt(0) + "*".repeat(local.length() - 1)) + "@***.***";
    }
}
