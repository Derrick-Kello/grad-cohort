package edu.university.grps.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.grps.common.GrpsProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Short-lived signed download links, valid for 10 minutes by default (FR-EVD-03, SEC-06). */
@Component
public class SignedLinks {

    public record Payload(String key, String fileName, String contentType, boolean inline, long expiresAt) {}

    public record Link(String url, Instant expiresAt) {}

    private final ObjectMapper mapper;
    private final GrpsProperties props;
    private final Clock clock;
    private final byte[] secret;

    public SignedLinks(ObjectMapper mapper, GrpsProperties props, Clock clock) {
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        this.secret = props.storage().signingKey().getBytes(StandardCharsets.UTF_8);
    }

    public Link create(String key, String fileName, String contentType, boolean inline) {
        Instant expires = clock.instant().plus(props.storage().linkTtl());
        try {
            byte[] json = mapper.writeValueAsBytes(new Payload(key, fileName, contentType, inline, expires.getEpochSecond()));
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
            String token = body + "." + sign(body);
            String url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/files/").path(token).toUriString();
            return new Link(url, expires);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign link", e);
        }
    }

    /** Returns the payload if the signature is valid and the link has not expired; otherwise null. */
    public Payload verify(String token) {
        int dot = token.lastIndexOf('.');
        if (dot < 1) return null;
        String body = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.US_ASCII), sig.getBytes(StandardCharsets.US_ASCII))) {
            return null;
        }
        try {
            Payload payload = mapper.readValue(Base64.getUrlDecoder().decode(body), Payload.class);
            return payload.expiresAt() < clock.instant().getEpochSecond() ? null : payload;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
