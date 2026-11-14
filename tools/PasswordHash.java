import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Offline operator tool: no database access, accounts or role changes. */
class PasswordHash {
    static String hash(String password) {
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Use at least 12 characters and at most 72 UTF-8 bytes");
        }
        return new BCryptPasswordEncoder().encode(password);
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--self-test".equals(args[0])) {
            String dummy = "Fictitious-self-test!";
            String encoded = hash(dummy);
            if (encoded.equals(dummy) || !new BCryptPasswordEncoder().matches(dummy, encoded)) {
                throw new AssertionError("BCrypt round-trip failed");
            }
            for (String invalid : new String[]{"short", "a".repeat(73), "é".repeat(37)}) {
                try {
                    hash(invalid);
                    throw new AssertionError("Invalid length accepted");
                } catch (IllegalArgumentException expected) {
                    // Both byte length and character length must satisfy the service policy.
                }
            }
            System.out.println("PASSWORD_HASH_SELF_TEST_OK");
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("No password arguments are accepted");
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Run from an interactive console, not a pipe or IDE");
        char[] password = console.readPassword("Password: ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            console.printf("%s%n", hash(new String(password)));
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
