package edu.university.grps.notification.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Live unread-count updates over server-sent events (FR-NTF-03). */
@Component
public class SseHub {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId, long unreadCount) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = emitters.get(userId);
            if (list != null) list.remove(emitter);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("unread").data(Map.of("unread", unreadCount)));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void push(UUID userId, Object notification, long unreadCount) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
                emitter.send(SseEmitter.event().name("unread").data(Map.of("unread", unreadCount)));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }
    }

    /** Keeps proxies from closing idle streams. */
    @Scheduled(fixedDelay = 25_000)
    void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    list.remove(emitter);
                }
            }
        });
    }
}
