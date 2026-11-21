package dev.hookdock.ingestion;

/** Why an inbound request to a known endpoint was turned away. */
public enum RejectionReason {

    RATE_LIMITED("Rate limited");

    private final String label;

    RejectionReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
