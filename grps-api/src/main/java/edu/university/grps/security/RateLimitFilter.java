package edu.university.grps.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.university.grps.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SEC-08: 10 sign-in attempts per minute per IP and 100 write requests per minute per user.
 * Counts are per instance; put a shared limiter at the load balancer when running several instances.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final int LOGIN_PER_MINUTE = 10;
    static final int WRITES_PER_MINUTE = 100;

    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    private record Window(long minute, AtomicInteger count) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String key = null;
        int limit = 0;
        if ("POST".equals(method) && path.endsWith("/auth/login")) {
            key = "login:" + clientIp(request);
            limit = LOGIN_PER_MINUTE;
        } else if (!"GET".equals(method) && !"OPTIONS".equals(method) && !"HEAD".equals(method)) {
            var user = CurrentUser.optionalId();
            if (user.isPresent()) {
                key = "write:" + user.get();
                limit = WRITES_PER_MINUTE;
            }
        }
        if (key != null && !allow(key, limit)) {
            response.setHeader("Retry-After", "60");
            SecurityConfig.writeProblem(mapper, request, response, ErrorCode.RATE_LIMITED,
                    "Too many requests. Wait a minute and try again.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long minute = clock.millis() / 60_000;
        Window w = windows.compute(key, (k, old) -> old == null || old.minute() != minute
                ? new Window(minute, new AtomicInteger()) : old);
        if (windows.size() > 50_000) windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        return w.count().incrementAndGet() <= limit;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
