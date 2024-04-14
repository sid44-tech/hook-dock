package dev.hookdock.endpoints;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Generates inbound endpoint tokens. The plaintext token is only shown to the
 * user once; we persist a hash of it.
 * <p>
 * 32 bytes from SecureRandom gives 256 bits of entropy. Random UUIDs only carry
 * 122 random bits and aren't guaranteed to come from a CSPRNG on every JVM.
 */
@Component
public class TokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
