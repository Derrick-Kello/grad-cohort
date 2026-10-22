package edu.university.grps.common;

import java.util.Map;

/** A rule or state violation that maps to an RFC 7807 problem response. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public ApiException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.properties = properties;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException invalidState(String detail) {
        return new ApiException(ErrorCode.INVALID_STATE, detail);
    }

    public static ApiException rule(String detail) {
        return new ApiException(ErrorCode.BUSINESS_RULE, detail);
    }

    public static ApiException validation(String detail) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, detail);
    }
}
