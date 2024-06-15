package dev.hookdock.delivery;

public enum ErrorCategory {
    /** Target answered with a non-2xx status. */
    HTTP_STATUS,
    TIMEOUT,
    CONNECTION,
    POLICY_BLOCKED,
    DESTINATION_DISABLED,
    INTERNAL
}
