package ai.coditiy.scheduler.controller;

import ai.coditiy.scheduler.dto.JwtResponse;
import ai.coditiy.scheduler.dto.LoginRequest;
import ai.coditiy.scheduler.dto.RegisterRequest;
import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.*;
import ai.coditiy.scheduler.service.auth.JwtTokenProvider;
import ai.coditiy.scheduler.service.auth.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return ResponseEntity.ok(new JwtResponse(jwt,
                                                 userDetails.getId(), 
                                                 userDetails.getUsername(), 
                                                 userDetails.getEmail()));
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error: Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error: Email is already in use!"));
        }

        // Create new user's account
        User user = User.builder()
                .username(signUpRequest.getUsername())
                .email(signUpRequest.getEmail())
                .passwordHash(encoder.encode(signUpRequest.getPassword()))
                .build();

        userRepository.save(user);

        // Bootstrap: Create a default organization for the user
        Organization organization = Organization.builder()
                .name(user.getUsername() + "'s Org")
                .build();
        organizationRepository.save(organization);

        // Add user as ADMIN to this organization
        OrganizationMember member = OrganizationMember.builder()
                .organization(organization)
                .user(user)
                .role(Role.ADMIN)
                .build();
        organizationMemberRepository.save(member);

        // Bootstrap: Create a default project under this organization
        Project project = Project.builder()
                .name("Default Project")
                .organization(organization)
                .build();
        projectRepository.save(project);

        return ResponseEntity.ok(Map.of("message", "User registered successfully! Default Workspace created."));
    }
}
