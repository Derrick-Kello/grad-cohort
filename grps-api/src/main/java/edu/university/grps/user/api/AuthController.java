package edu.university.grps.user.api;

import edu.university.grps.user.service.AuthService;
import edu.university.grps.user.service.AuthService.TokenPair;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record LoginRequest(@NotBlank String login, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ForgotRequest(@NotBlank String email) {}

    public record TokenPasswordRequest(@NotBlank String token, @NotBlank String password) {}

    @PostMapping("/login")
    public TokenPair login(@RequestBody @Valid LoginRequest request) {
        return auth.login(request.login(), request.password());
    }

    @PostMapping("/refresh")
    public TokenPair refresh(@RequestBody @Valid RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) RefreshRequest request) {
        auth.logout(request == null ? null : request.refreshToken());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@RequestBody @Valid ForgotRequest request) {
        auth.forgotPassword(request.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@RequestBody @Valid TokenPasswordRequest request) {
        auth.resetPassword(request.token(), request.password());
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@RequestBody @Valid TokenPasswordRequest request) {
        auth.activate(request.token(), request.password());
    }
}
