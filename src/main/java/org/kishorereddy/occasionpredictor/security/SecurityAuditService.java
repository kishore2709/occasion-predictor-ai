package org.kishorereddy.occasionpredictor.security;

import org.kishorereddy.occasionpredictor.entity.SecurityAuditLog;
import org.kishorereddy.occasionpredictor.repository.SecurityAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class SecurityAuditService {

    // Separate logger so these lines can be routed to a dedicated audit appender
    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final SecurityAuditLogRepository repository;

    public SecurityAuditService(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a data-access or admin event.
     *
     * @param action     e.g. READ_PREDICTION, CREATE_PREDICTION, ADMIN_REPLAY
     * @param resourceId prediction UUID or orderId
     * @param auth       current Spring Security authentication (may be anonymous in dev)
     * @param ip         client IP from the HTTP request
     */
    public void logAccess(String action, String resourceId, Authentication auth, String ip) {
        String principal = extractPrincipal(auth);
        String roles     = extractRoles(auth);

        auditLog.info("action={} resource={} principal={} roles={} ip={}",
                action, resourceId, principal, roles, ip);

        SecurityAuditLog entry = new SecurityAuditLog();
        entry.setPrincipal(principal);
        entry.setAction(action);
        entry.setResourceId(resourceId);
        entry.setRoles(roles);
        entry.setIpAddress(ip);
        repository.save(entry);
    }

    private String extractPrincipal(Authentication auth) {
        if (auth == null) return "anonymous";
        // JwtAuthenticationToken.getName() returns the JWT "sub" claim
        // AnonymousAuthenticationToken.getName() returns "anonymousUser"
        return auth.getName();
    }

    private String extractRoles(Authentication auth) {
        if (auth == null) return "";
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }
}
