package edu.university.grps.common;

import org.springframework.http.HttpStatus;

/** Machine-readable error codes returned in problem details (PRD section 23). */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not signed in"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Sign-in failed"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Not allowed"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    MILESTONE_LOCKED(HttpStatus.CONFLICT, "Milestone is locked"),
    THESIS_LOCKED(HttpStatus.CONFLICT, "Thesis submission is locked"),
    SCHEDULE_CLASH(HttpStatus.CONFLICT, "Schedule clash"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "Record changed by someone else"),
    INVALID_STATE(HttpStatus.CONFLICT, "Action not allowed in the current state"),
    DUPLICATE(HttpStatus.CONFLICT, "Duplicate record"),
    EVIDENCE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Evidence required"),
    SUPERVISOR_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Supervisor required"),
    MAX_ATTEMPTS_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "Maximum attempts reached"),
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule not met"),
    FILE_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "File rejected"),
    IMPORT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Import failed"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File too large"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String typeSlug() {
        return name().toLowerCase().replace('_', '-');
    }
}
