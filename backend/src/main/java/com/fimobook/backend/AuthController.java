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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(String email, String password, String displayName) {
    }

    public record AuthResponse(String token, long userId, String email, String displayName) {
    }

    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AuthRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    private AuthResponse response(AuthRepository.User user) {
        return new AuthResponse(jwtService.create(user.id(), user.email()),
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
