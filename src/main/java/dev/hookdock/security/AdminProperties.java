package dev.hookdock.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Initial admin account, read from the environment. Only used to create the
 * first user when the user table is empty.
 */
@ConfigurationProperties(prefix = "hookdock.admin")
public record AdminProperties(String username, String password) {
}
