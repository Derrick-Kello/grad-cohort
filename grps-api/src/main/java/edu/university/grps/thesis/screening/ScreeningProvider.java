package edu.university.grps.thesis.screening;

import java.math.BigDecimal;
import java.util.Optional;

/** Similarity and AI-content screening (PRD section 24). The fallback is manual entry by an admin (FR-THS-09). */
public interface ScreeningProvider {

    record Submission(String fileName, String title, String authorName, byte[] content) {}

    record Result(BigDecimal similarityPct, BigDecimal aiPct, String reportUrl) {}

    /** Sends the document and returns the provider's reference. Throws if the provider is unavailable. */
    String submit(Submission submission);

    /** Returns the result once it is ready. */
    Optional<Result> fetchResult(String providerRef);

    String name();
}
