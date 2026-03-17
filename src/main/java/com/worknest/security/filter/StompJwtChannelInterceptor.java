package com.worknest.security.filter;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.InvalidTokenException;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.security.jwt.JwtService;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.tenant.context.TenantContext;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.HrConversation;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.entity.TeamChat;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.HrConversationRepository;
import com.worknest.tenant.repository.TeamChatRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TEAM_CHAT_DESTINATION_PATTERN =
            Pattern.compile("^/(topic|app)/tenant/[^/]+/team-chat/(\\d+)(?:/.*)?$");
    private static final Pattern HR_CHAT_DESTINATION_PATTERN =
            Pattern.compile("^/(topic|app)/tenant/[^/]+/hr-chat/(\\d+)(?:/.*)?$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final String tenantHeaderName;
    private final MasterTenantLookupService masterTenantLookupService;
    private final EmployeeRepository employeeRepository;
    private final TeamChatRepository teamChatRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final HrConversationRepository hrConversationRepository;

    public StompJwtChannelInterceptor(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            MasterTenantLookupService masterTenantLookupService,
            EmployeeRepository employeeRepository,
            TeamChatRepository teamChatRepository,
            TeamMemberRepository teamMemberRepository,
            HrConversationRepository hrConversationRepository,
            @Value("${app.tenant.header:X-Tenant-ID}") String tenantHeaderName) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.masterTenantLookupService = masterTenantLookupService;
        this.employeeRepository = employeeRepository;
        this.teamChatRepository = teamChatRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.hrConversationRepository = hrConversationRepository;
        this.tenantHeaderName = tenantHeaderName;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticateConnect(accessor);
            return message;
        }

        if (accessor.getCommand() == StompCommand.SEND || accessor.getCommand() == StompCommand.SUBSCRIBE) {
            enforceDestinationTenantBinding(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or invalid Authorization header for STOMP connection");
        }

        String token = authHeader.substring(7);
        try {
            String email = jwtService.extractUsername(token);
            if (email == null) {
                throw new InvalidTokenException("Invalid STOMP access token");
            }

            PlatformUserPrincipal principal = (PlatformUserPrincipal) userDetailsService.loadUserByUsername(email);
            if (!jwtService.isTokenValid(token, principal)) {
                throw new InvalidTokenException("Invalid STOMP access token");
            }

            validateTenantBinding(accessor, token, principal);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(authentication);

        } catch (JwtException | ClassCastException ex) {
            throw new InvalidTokenException("Invalid STOMP access token");
        }
    }

    private void enforceDestinationTenantBinding(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication) ||
                !(authentication.getPrincipal() instanceof PlatformUserPrincipal principal)) {
            throw new InvalidTokenException("STOMP session is not authenticated");
        }

        String destination = accessor.getDestination();
        validateDestinationTenant(principal, destination);
        validateChatDestinationAccess(principal, destination);
    }

    private void validateTenantBinding(
            StompHeaderAccessor accessor,
            String token,
            PlatformUserPrincipal principal) {

        PlatformRole role = principal.getRole();
        if (!role.isTenantScoped()) {
            return;
        }

        String tokenTenant = jwtService.extractTenantKey(token);
        String userTenant = principal.getTenantKey();

        if (tokenTenant == null || userTenant == null || !tokenTenant.equalsIgnoreCase(userTenant)) {
            throw new InvalidTokenException("Token tenant mismatch for STOMP connection");
        }

        String requestTenant = accessor.getFirstNativeHeader(tenantHeaderName);
        if (requestTenant == null || !requestTenant.equalsIgnoreCase(tokenTenant)) {
            throw new ForbiddenOperationException("STOMP tenant header does not match token tenant");
        }

        boolean activeTenant = masterTenantLookupService.findByTenantKey(tokenTenant)
                .map(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .orElse(false);
        if (!activeTenant) {
            throw new ForbiddenOperationException("Tenant is not active for STOMP connection");
        }
    }

    private void validateDestinationTenant(PlatformUserPrincipal principal, String destination) {
        if (destination == null) {
            return;
        }

        String tenantFromDestination = extractTenantFromDestination(destination, "/topic/tenant/");
        if (tenantFromDestination == null) {
            tenantFromDestination = extractTenantFromDestination(destination, "/app/tenant/");
        }

        if (tenantFromDestination == null) {
            return;
        }

        PlatformRole role = principal.getRole();
        if (!role.isTenantScoped()) {
            throw new ForbiddenOperationException("Platform-level users cannot access tenant websocket destinations");
        }

        if (principal.getTenantKey() == null ||
                !tenantFromDestination.equalsIgnoreCase(principal.getTenantKey())) {
            throw new ForbiddenOperationException("STOMP destination tenant does not match authenticated tenant");
        }
    }

    private void validateChatDestinationAccess(PlatformUserPrincipal principal, String destination) {
        if (destination == null || principal.getTenantKey() == null) {
            return;
        }

        if (!principal.getRole().isTenantScoped()) {
            return;
        }

        runInTenantContext(principal.getTenantKey(), () -> {
            Matcher teamMatcher = TEAM_CHAT_DESTINATION_PATTERN.matcher(destination);
            if (teamMatcher.matches()) {
                long teamChatId = parseLongOrThrow(teamMatcher.group(2), "Invalid team chat destination");
                validateTeamChatAccess(principal, teamChatId);
                return null;
            }

            Matcher hrMatcher = HR_CHAT_DESTINATION_PATTERN.matcher(destination);
            if (hrMatcher.matches()) {
                long conversationId = parseLongOrThrow(hrMatcher.group(2), "Invalid HR chat destination");
                validateHrChatAccess(principal, conversationId);
                return null;
            }

            return null;
        });
    }

    private void validateTeamChatAccess(PlatformUserPrincipal principal, Long teamChatId) {
        if (principal.getRole() == PlatformRole.TENANT_ADMIN || principal.getRole() == PlatformRole.ADMIN) {
            return;
        }

        TeamChat teamChat = teamChatRepository.findByIdWithTeamAndManager(teamChatId)
                .orElseThrow(() -> new ForbiddenOperationException("Team chat not found"));
        Employee currentEmployee = getEmployeeByEmailOrThrow(principal.getUsername());

        Team team = teamChat.getTeam();
        boolean isManager = team.getManager() != null && team.getManager().getId().equals(currentEmployee.getId());
        boolean isMember = teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), currentEmployee.getId())
                .isPresent();

        if (!isManager && !isMember) {
            throw new ForbiddenOperationException("You are not allowed to subscribe or send on this team chat");
        }
    }

    private void validateHrChatAccess(PlatformUserPrincipal principal, Long conversationId) {
        HrConversation conversation = hrConversationRepository.findByIdWithParticipants(conversationId)
                .orElseThrow(() -> new ForbiddenOperationException("HR conversation not found"));
        Employee currentEmployee = getEmployeeByEmailOrThrow(principal.getUsername());

        boolean isParticipant = conversation.getEmployee().getId().equals(currentEmployee.getId())
                || conversation.getHr().getId().equals(currentEmployee.getId());
        if (!isParticipant) {
            throw new ForbiddenOperationException("You are not allowed to subscribe or send on this HR chat");
        }
    }

    private Employee getEmployeeByEmailOrThrow(String email) {
        return employeeRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ForbiddenOperationException("Current user is not linked to an employee profile"));
    }

    private String extractTenantFromDestination(String destination, String prefix) {
        if (!destination.startsWith(prefix)) {
            return null;
        }

        String remainder = destination.substring(prefix.length());
        int nextSlash = remainder.indexOf('/');
        if (nextSlash < 0) {
            return remainder;
        }
        return remainder.substring(0, nextSlash);
    }

    private long parseLongOrThrow(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ForbiddenOperationException(message);
        }
    }

    private <T> T runInTenantContext(String tenantKey, Supplier<T> supplier) {
        String previousTenant = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantKey.trim().toLowerCase());
            return supplier.get();
        } finally {
            if (previousTenant == null || previousTenant.isBlank()) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
    }
}
