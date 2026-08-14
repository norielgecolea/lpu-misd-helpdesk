package org.lpu.dev.codes.helpdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.model.Role;
import org.lpu.dev.codes.helpdesk.model.User;
import org.lpu.dev.codes.helpdesk.repository.UserRepository;
import org.lpu.dev.codes.helpdesk.service.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LogManager.getLogger(JwtAuthFilter.class);
    private static final Set<Role> STAFF_ROLES = EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN, Role.MONITORING);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (jwtService.isTokenValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = jwtService.extractEmail(token);
            Role role = jwtService.extractRole(token);
            Long userId = jwtService.extractUserId(token);
            String name = jwtService.extractName(token);

            // JWTs are stateless, so a deactivated staff account's still-valid token
            // would otherwise keep working until it expires. Re-check active status on
            // every request for staff-role tokens (cheap primary-key lookup).
            if (!STAFF_ROLES.contains(role) || isActiveStaff(userId)) {
                AuthenticatedUser principal = new AuthenticatedUser(userId, email, name, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.warn("Rejected token for deactivated/missing staff account userId={}", userId);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isActiveStaff(Long userId) {
        return userRepository.findById(userId).map(User::isActive).orElse(false);
    }
}
