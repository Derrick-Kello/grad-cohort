package edu.university.grps.security;

import edu.university.grps.common.GrpsProperties;
import edu.university.grps.user.domain.AppUser;
import edu.university.grps.user.domain.RoleCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final GrpsProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, GrpsProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public String issueAccessToken(AppUser user) {
        Instant now = clock.instant();
        List<String> roles = user.roleCodes().stream().map(RoleCode::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("grps")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().accessTtl()))
                .claim("roles", roles)
                .claim("name", user.getFullName())
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("grps-1").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTtlSeconds() {
        return props.jwt().accessTtl().toSeconds();
    }

    /** A random opaque token; only its SHA-256 hash is stored. */
    public static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
