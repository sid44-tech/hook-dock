package dev.hookdock.delivery;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "hookdock.delivery")
public record DeliveryProperties(
        boolean workerEnabled,
        Duration pollInterval,
        int batchSize,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration leaseDuration,
        DataSize responseExcerptSize) {

    public DeliveryProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(2);
        }
        if (batchSize <= 0) {
            batchSize = 10;
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(15);
        }
        if (leaseDuration == null) {
            leaseDuration = Duration.ofMinutes(2);
        }
        if (responseExcerptSize == null) {
            responseExcerptSize = DataSize.ofKilobytes(4);
        }
    }
}
