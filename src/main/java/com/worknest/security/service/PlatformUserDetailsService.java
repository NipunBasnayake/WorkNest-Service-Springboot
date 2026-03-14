package com.worknest.security.service;

import com.worknest.common.exception.InvalidCredentialsException;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlatformUserDetailsService implements UserDetailsService {

    private final PlatformUserRepository platformUserRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public PlatformUserDetailsService(
            PlatformUserRepository platformUserRepository,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.platformUserRepository = platformUserRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        PlatformUser platformUser = masterTenantContextRunner.runInMasterContext(() ->
                platformUserRepository.findByEmailIgnoreCase(normalizeEmail(username))
                        .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password")));
        return new PlatformUserPrincipal(platformUser);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
