package com.rmrx.agenticshop.app.security.web;

import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.dto.AuthDtos.LoginRequest;
import com.rmrx.agenticshop.app.security.dto.AuthDtos.LoginResponse;
import com.rmrx.agenticshop.app.security.dto.AuthDtos.Me;
import com.rmrx.agenticshop.app.security.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Sign-in (username/password to access token) and the current session. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccessScope scope;

    public AuthController(AuthService authService, AccessScope scope) {
        this.authService = authService;
        this.scope = scope;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    /** Profile, role and permissions of the signed-in user. */
    @GetMapping("/me")
    public Me me() {
        return authService.me(scope.current());
    }
}
