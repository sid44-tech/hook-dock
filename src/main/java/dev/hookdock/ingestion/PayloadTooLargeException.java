package dev.hookdock.ingestion;

public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(long limit) {
        super("Request body exceeds limit of " + limit + " bytes");
    }
}
