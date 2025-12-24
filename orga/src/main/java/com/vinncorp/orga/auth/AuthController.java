package com.vinncorp.orga.auth;

import com.vinncorp.orga.auth.dto.AuthResponse;
import com.vinncorp.orga.auth.dto.LoginRequest;
import com.vinncorp.orga.auth.dto.SignupRequest;
import com.vinncorp.orga.security.JwtUtil;
import com.vinncorp.orga.tenant.Tenant;
import com.vinncorp.orga.tenant.TenantRepository;
import com.vinncorp.orga.user.User;
import com.vinncorp.orga.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          TenantRepository tenantRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, "Email already in use"));
        }

        // Extract domain from email for tenant detection
        String emailDomain = extractDomainFromEmail(request.getEmail());
        
        // Try to find tenant by domain (supports both "vinncorp" and "vinncorp.com")
        Tenant tenant = tenantRepository.findByDomain(emailDomain)
                .or(() -> {
                    // If not found, try with full domain (e.g., "vinncorp.com")
                    String fullDomain = request.getEmail().substring(request.getEmail().indexOf('@') + 1);
                    return tenantRepository.findByDomain(fullDomain);
                })
                .orElseGet(() -> {
                    // Fallback to default tenant if no matching tenant found
                    return tenantRepository.findByDomain("default")
                            .orElseGet(() -> {
                                Tenant newTenant = new Tenant("Default Organization", "default");
                                return tenantRepository.save(newTenant);
                            });
                });

        String hashed = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getEmail(), request.getFullName(), hashed, tenant);
        userRepository.save(user);

        // Generate token and return it (auto-login after signup)
        String token = jwtUtil.generateToken(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "message", "User created successfully",
                "email", user.getEmail()
        ));
    }

    /**
     * Extracts the domain identifier from an email address.
     * Examples:
     * - "user@vinncorp.com" -> "vinncorp"
     * - "user@acme.example.com" -> "acme"
     * 
     * @param email The email address
     * @return The domain identifier (without TLD)
     */
    private String extractDomainFromEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex == -1 || atIndex == email.length() - 1) {
            return "default";
        }
        
        String domain = email.substring(atIndex + 1);
        // Extract the main domain part (before first dot)
        // "vinncorp.com" -> "vinncorp"
        // "acme.example.com" -> "acme"
        String[] parts = domain.split("\\.");
        return parts.length > 0 ? parts[0] : "default";
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
            authenticationManager.authenticate(authToken);
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(new AuthResponse(null, "Invalid credentials"));
        }

        String token = jwtUtil.generateToken(request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ResponseEntity.ok(Map.of(
                "token", token,
                "message", "Login successful",
                "role", user.getRole().name(),
                "email", user.getEmail()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "role", user.getRole().name(),
                "tenantId", user.getTenant().getId(),
                "tenantName", user.getTenant().getName()
        ));
    }
}


