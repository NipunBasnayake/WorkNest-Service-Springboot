package com.worknest.config;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminBootstrapTest {

    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void createsPlatformAdminWhenNoneExists() {
        when(platformUserRepository.existsByRole(PlatformRole.PLATFORM_ADMIN)).thenReturn(false);
        when(platformUserRepository.existsByEmailIgnoreCase("platform.admin@worknest.local")).thenReturn(false);
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("encoded-password");

        bootstrap(true).run();

        ArgumentCaptor<PlatformUser> captor = ArgumentCaptor.forClass(PlatformUser.class);
        verify(platformUserRepository).save(captor.capture());
        PlatformUser saved = captor.getValue();
        assertThat(saved.getFullName()).isEqualTo("Platform Admin");
        assertThat(saved.getEmail()).isEqualTo("platform.admin@worknest.local");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(saved.getRole()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getTenantKey()).isNull();
        assertThat(saved.isPasswordChangeRequired()).isTrue();
    }

    @Test
    void leavesExistingPlatformAdminUntouched() {
        when(platformUserRepository.existsByRole(PlatformRole.PLATFORM_ADMIN)).thenReturn(true);

        bootstrap(true).run();

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(platformUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void disabledBootstrapDoesNotReadOrWriteAccounts() {
        bootstrap(false).run();

        verify(platformUserRepository, never()).existsByRole(org.mockito.ArgumentMatchers.any());
        verify(platformUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesToOverwriteAnExistingNonAdminEmail() {
        when(platformUserRepository.existsByRole(PlatformRole.PLATFORM_ADMIN)).thenReturn(false);
        when(platformUserRepository.existsByEmailIgnoreCase("platform.admin@worknest.local")).thenReturn(true);

        assertThatThrownBy(() -> bootstrap(true).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already assigned");

        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
        verify(platformUserRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private PlatformAdminBootstrap bootstrap(boolean enabled) {
        return new PlatformAdminBootstrap(
                platformUserRepository,
                passwordEncoder,
                enabled,
                "Platform Admin",
                "platform.admin@worknest.local",
                "ChangeMe123!");
    }
}
