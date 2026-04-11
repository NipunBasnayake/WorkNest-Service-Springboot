package com.worknest.security.model;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class PlatformUserPrincipal implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final PlatformRole role;
    private final UserStatus status;
    private final String tenantKey;
    private final boolean passwordChangeRequired;

    public PlatformUserPrincipal(PlatformUser platformUser) {
        this.id = platformUser.getId();
        this.fullName = platformUser.getFullName();
        this.email = platformUser.getEmail();
        this.passwordHash = platformUser.getPasswordHash();
        this.role = platformUser.getRole();
        this.status = platformUser.getStatus();
        this.tenantKey = platformUser.getTenantKey();
        this.passwordChangeRequired = platformUser.isPasswordChangeRequired();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
