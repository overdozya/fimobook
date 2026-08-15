package com.fimobook.backend;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(String email, String password, String displayName) {
    }

    public record AuthResponse(String token, String refreshToken, long userId, String email, String displayName) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody Credentials request) {
        String email = normalizeEmail(request.email());
        String name = request.displayName() == null ? "" : request.displayName().trim();
        validatePassword(request.password());
        if (name.length() < 2 || name.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임은 2~50자로 입력해주세요.");
        }
        try {
            var user = repository.create(email, passwordEncoder.encode(request.password()), name);
            return response(user);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody Credentials request) {
        String email = normalizeEmail(request.email());
        var user = repository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 틀렸습니다."));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 틀렸습니다.");
        }
        return response(user);
    }

    @GetMapping("/me")
    public AuthResponse me(org.springframework.security.core.Authentication authentication) {
        long userId = (Long) authentication.getPrincipal();
        var user = repository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        return new AuthResponse(null, null, user.id(), user.email(), user.displayName());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody RefreshRequest request) {
        var rotation = refreshTokenService.rotate(request.refreshToken());
        var user = repository.findById(rotation.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        return response(user, rotation.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private AuthResponse response(AuthRepository.User user) {
        return response(user, refreshTokenService.create(user.id()));
    }

    private AuthResponse response(AuthRepository.User user, String refreshToken) {
        return new AuthResponse(jwtService.create(user.id(), user.email()), refreshToken,
                user.id(), user.email(), user.displayName());
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || normalized.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이메일을 입력해주세요.");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호는 8~72자로 입력해주세요.");
        }
    }
}
