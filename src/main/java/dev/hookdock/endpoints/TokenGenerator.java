package dev.hookdock.endpoints;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Generates inbound endpoint tokens. The plaintext token is only shown to the
 * user once; we persist a hash of it.
 */
@Component
public class TokenGenerator {

    public String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
