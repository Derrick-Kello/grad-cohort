package edu.university.grps.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings under {@code grps.*}. Secrets come from environment variables (SEC-10). */
@ConfigurationProperties(prefix = "grps")
public record GrpsProperties(
        String timezone,
        String webOrigin,
        String frontendUrl,
        boolean devTools,
        Jwt jwt,
        Storage storage,
        Screening screening,
        VirusScan virusScan,
        Mail mail) {

    public record Jwt(String privateKey, String publicKey, Duration accessTtl, Duration refreshTtl) {}

    public record Storage(String type, String dir, String signingKey, Duration linkTtl) {}

    public record Screening(String provider) {}

    public record VirusScan(String mode, String host, int port) {}

    public record Mail(boolean enabled, String from) {}
}
