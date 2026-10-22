package edu.university.grps.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import edu.university.grps.common.GrpsProperties;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RS256 signing keys (SEC-03). Without configured PEM keys it generates a throwaway pair and says so. */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public RSAKey rsaKey(GrpsProperties props) throws Exception {
        String priv = props.jwt().privateKey();
        String pub = props.jwt().publicKey();
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (priv != null && !priv.isBlank() && pub != null && !pub.isBlank()) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(pemBody(priv)));
            publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pemBody(pub)));
        } else {
            log.warn("JWT_PRIVATE_KEY / JWT_PUBLIC_KEY not set: using a generated key pair. Tokens will not survive a restart.");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        }
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("grps-1").algorithm(JWSAlgorithm.RS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) throws Exception {
        return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    }

    private static byte[] pemBody(String pem) {
        String body = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
