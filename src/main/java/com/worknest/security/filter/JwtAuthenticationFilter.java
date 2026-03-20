package com.worknest.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worknest.common.api.ErrorResponse;
import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.InactiveUserException;
import com.worknest.common.exception.InvalidTokenException;
import com.worknest.common.exception.InvalidCredentialsException;
import com.worknest.security.jwt.JwtService;
import com.worknest.security.model.PlatformUserPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    private final String tenantHeaderName;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            ObjectMapper objectMapper,
            @Value("${app.tenant.header:X-Tenant-ID}") String tenantHeaderName) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
        this.tenantHeaderName = tenantHeaderName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        try {
            String email = jwtService.extractUsername(jwt);
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                PlatformUserPrincipal principal =
                        (PlatformUserPrincipal) userDetailsService.loadUserByUsername(email);

                if (!principal.isEnabled()) {
                    throw new InactiveUserException("User account is inactive");
                }

                if (!jwtService.isTokenValid(jwt, principal)) {
                    throw new InvalidTokenException("Access token is invalid");
                }

                validateTenantBinding(request, jwt, principal);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            writeErrorResponse(response, request.getRequestURI(),
                    HttpStatus.UNAUTHORIZED, "Access token has expired", "TOKEN_EXPIRED");
        } catch (InactiveUserException ex) {
            writeErrorResponse(response, request.getRequestURI(),
                    HttpStatus.FORBIDDEN, ex.getMessage(), "INACTIVE_USER");
        } catch (ForbiddenOperationException ex) {
            writeErrorResponse(response, request.getRequestURI(),
                    HttpStatus.FORBIDDEN, ex.getMessage(), "FORBIDDEN_OPERATION");
        } catch (InvalidCredentialsException | InvalidTokenException | JwtException | ClassCastException ex) {
            writeErrorResponse(response, request.getRequestURI(),
                    HttpStatus.UNAUTHORIZED, "Access token is invalid", "INVALID_TOKEN");
        } finally {
            if (response.getStatus() >= 400) {
                SecurityContextHolder.clearContext();
            }
        }
    }

    private void validateTenantBinding(
            HttpServletRequest request,
            String jwt,
            PlatformUserPrincipal principal) {

        PlatformRole role = principal.getRole();
        if (!role.isTenantScoped()) {
            return;
        }

        String tokenTenantKey = jwtService.extractTenantKey(jwt);
        String userTenantKey = principal.getTenantKey();

        if (tokenTenantKey == null || userTenantKey == null ||
                !tokenTenantKey.equalsIgnoreCase(userTenantKey)) {
            throw new InvalidTokenException("Token tenant does not match the user tenant");
        }

        if (isTenantEndpoint(request.getRequestURI())) {
            String requestTenantHeader = request.getHeader(tenantHeaderName);
            if (requestTenantHeader == null ||
                    !requestTenantHeader.equalsIgnoreCase(tokenTenantKey)) {
                throw new ForbiddenOperationException("Token tenant does not match request tenant header");
            }
        }
    }

    private boolean isTenantEndpoint(String uri) {
        return uri.startsWith("/api/tenant/");
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            String requestUri,
            HttpStatus status,
            String message,
            String errorCode) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse errorResponse = ErrorResponse.of(status, errorCode, message, requestUri);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
