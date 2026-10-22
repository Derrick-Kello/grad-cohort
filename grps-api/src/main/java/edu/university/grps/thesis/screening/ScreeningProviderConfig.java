package edu.university.grps.thesis.screening;

import edu.university.grps.common.GrpsProperties;
import edu.university.grps.storage.UploadChecks;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScreeningProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(ScreeningProviderConfig.class);

    @Bean
    public ScreeningProvider screeningProvider(GrpsProperties props) {
        String provider = props.screening().provider();
        if ("stub".equalsIgnoreCase(provider)) return new StubScreeningProvider();
        log.warn("Screening provider '{}' has no adapter configured yet; admins must enter screening results manually.", provider);
        return new ScreeningProvider() {
            @Override
            public String submit(Submission submission) {
                throw new IllegalStateException("Screening provider " + provider + " is not configured");
            }

            @Override
            public Optional<Result> fetchResult(String providerRef) {
                return Optional.empty();
            }

            @Override
            public String name() {
                return provider;
            }
        };
    }

    /**
     * Deterministic stand-in for development and tests. Scores derive from the document hash unless a test or the
     * local dev tools queued specific scores for the next submissions.
     */
    public static class StubScreeningProvider implements ScreeningProvider {

        private final ConcurrentLinkedQueue<BigDecimal[]> queued = new ConcurrentLinkedQueue<>();
        private final Map<String, Result> results = new ConcurrentHashMap<>();

        public void queueNext(BigDecimal similarity, BigDecimal ai) {
            queued.add(new BigDecimal[] {similarity, ai});
        }

        @Override
        public String submit(Submission submission) {
            String sha = UploadChecks.sha256(submission.content());
            String ref = "stub-" + sha.substring(0, 16) + "-" + System.nanoTime();
            BigDecimal[] next = queued.poll();
            BigDecimal similarity;
            BigDecimal ai;
            if (next != null) {
                similarity = next[0];
                ai = next[1];
            } else {
                long h = Long.parseUnsignedLong(sha.substring(0, 12), 16);
                similarity = BigDecimal.valueOf(3 + (h % 160) / 10.0).setScale(1, RoundingMode.HALF_UP);
                ai = BigDecimal.valueOf(1 + ((h >> 12) % 120) / 10.0).setScale(1, RoundingMode.HALF_UP);
            }
            results.put(ref, new Result(similarity, ai, null));
            return ref;
        }

        @Override
        public Optional<Result> fetchResult(String providerRef) {
            return Optional.ofNullable(results.get(providerRef));
        }

        @Override
        public String name() {
            return "stub";
        }
    }
}
