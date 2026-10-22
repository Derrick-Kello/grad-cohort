package edu.university.grps.notification.api;

import edu.university.grps.common.PageResponse;
import edu.university.grps.notification.domain.NotificationCode;
import edu.university.grps.notification.service.NotificationService;
import edu.university.grps.notification.service.NotificationService.NotificationDto;
import edu.university.grps.notification.service.SseHub;
import edu.university.grps.security.CurrentUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;
    private final SseHub sseHub;

    public NotificationController(NotificationService service, SseHub sseHub) {
        this.service = service;
        this.sseHub = sseHub;
    }

    @GetMapping
    public PageResponse<NotificationDto> inbox(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(required = false) String code,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        NotificationCode parsed = code == null || code.isBlank() ? null : NotificationCode.valueOf(code.replace("-", ""));
        return PageResponse.of(service.inbox(CurrentUser.id(), unreadOnly, parsed, PageRequest.of(page, Math.min(size, 100))),
                n -> n);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("unread", service.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable UUID id) {
        service.markRead(CurrentUser.id(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead(CurrentUser.id()));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        UUID userId = CurrentUser.id();
        return sseHub.register(userId, service.unreadCount(userId));
    }
}
