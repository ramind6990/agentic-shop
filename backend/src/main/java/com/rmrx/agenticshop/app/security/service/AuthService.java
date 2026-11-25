package com.rmrx.agenticshop.app.security.service;

import com.rmrx.agenticshop.app.common.dto.RefMapper;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.identity.entity.UserStatus;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.security.CurrentUser;
import com.rmrx.agenticshop.app.security.TokenService;
import com.rmrx.agenticshop.app.security.dto.AuthDtos.LoginResponse;
import com.rmrx.agenticshop.app.security.dto.AuthDtos.Me;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String INVALID = "Invalid username or password";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefMapper refs;
    /** Hash checked for unknown usernames, so a wrong username takes as long as a wrong password. */
    private final String dummyHash;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                       RefMapper refs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refs = refs;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Checks the credentials and issues an access token; inactive users cannot sign in. */
    public LoginResponse login(String username, String password) {
        Optional<AppUser> found = userRepository.findByUsername(username.trim().toLowerCase());
        String hash = found.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        AppUser user = found.filter(u -> matches && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException(INVALID));
        TokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.token(), token.expiresAt());
    }

    public Me me(CurrentUser current) {
        AppUser user = userRepository.findByUsername(current.username())
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        return new Me(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getRole(),
                refs.seller(user.getSeller()), current.permissions());
    }
}
