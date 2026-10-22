package edu.university.grps.comment.api;

import edu.university.grps.comment.domain.ReviewComment;
import edu.university.grps.comment.service.CommentService;
import edu.university.grps.comment.service.CommentService.CommentDto;
import edu.university.grps.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentService service;

    public CommentController(CommentService service) {
        this.service = service;
    }

    public record CommentRequest(@NotNull ReviewComment.TargetType targetType, @NotNull UUID targetId,
                                 @NotBlank String body) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto add(@RequestBody @Valid CommentRequest request) {
        return service.add(request.targetType(), request.targetId(), request.body());
    }

    @GetMapping
    public List<CommentDto> list(@RequestParam ReviewComment.TargetType targetType, @RequestParam UUID targetId) {
        return service.list(targetType, targetId);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public PageResponse<CommentDto> mine(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(service.mine(PageRequest.of(page, Math.min(size, 100))), c -> c);
    }
}
