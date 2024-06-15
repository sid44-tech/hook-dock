package dev.hookdock.delivery;

public enum DeliveryStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    /** Destination failed the outbound policy at send time; nothing was sent. */
    BLOCKED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == BLOCKED;
    }
}
