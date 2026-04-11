package com.worknest.master.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.dto.PlatformAnnouncementCreateRequestDto;
import com.worknest.master.dto.PlatformAnnouncementResponseDto;
import com.worknest.master.entity.PlatformAnnouncement;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformAnnouncementRepository;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.master.service.PlatformAnnouncementService;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class PlatformAnnouncementServiceImpl implements PlatformAnnouncementService {

    private final PlatformAnnouncementRepository platformAnnouncementRepository;
    private final PlatformUserRepository platformUserRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final EmailNotificationService emailNotificationService;

    public PlatformAnnouncementServiceImpl(
            PlatformAnnouncementRepository platformAnnouncementRepository,
            PlatformUserRepository platformUserRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            EmailNotificationService emailNotificationService) {
        this.platformAnnouncementRepository = platformAnnouncementRepository;
        this.platformUserRepository = platformUserRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public PlatformAnnouncementResponseDto createAnnouncement(PlatformAnnouncementCreateRequestDto requestDto) {
        return masterTenantContextRunner.runInMasterContext(() -> {
            PlatformUser actor = getCurrentPlatformAdminOrThrow();
            PlatformAnnouncement announcement = new PlatformAnnouncement();
            announcement.setTitle(requestDto.getTitle().trim());
            announcement.setMessage(requestDto.getMessage().trim());
            announcement.setCreatedBy(actor);

            PlatformAnnouncement saved = platformAnnouncementRepository.save(announcement);
            notifyTenantAdmins(saved);
            return toResponse(saved);
        });
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<PlatformAnnouncementResponseDto> listAnnouncements() {
        return masterTenantContextRunner.runInMasterContext(() -> platformAnnouncementRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(PlatformAnnouncement::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList());
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public PlatformAnnouncementResponseDto getAnnouncement(Long announcementId) {
        return masterTenantContextRunner.runInMasterContext(() -> toResponse(getAnnouncementOrThrow(announcementId)));
    }

    @Override
    public void deleteAnnouncement(Long announcementId) {
        masterTenantContextRunner.runInMasterContext(() -> {
            PlatformAnnouncement announcement = getAnnouncementOrThrow(announcementId);
            platformAnnouncementRepository.delete(announcement);
            return null;
        });
    }

    private PlatformAnnouncement getAnnouncementOrThrow(Long announcementId) {
        return platformAnnouncementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform announcement not found with id: " + announcementId));
    }

    private PlatformUser getCurrentPlatformAdminOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformUserPrincipal principal)) {
            throw new ForbiddenOperationException("Authenticated platform admin is required");
        }
        PlatformUser actor = platformUserRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Platform user not found"));
        if (actor.getRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new ForbiddenOperationException("Only platform admins can manage platform announcements");
        }
        return actor;
    }

    private void notifyTenantAdmins(PlatformAnnouncement announcement) {
        List<PlatformUser> recipients = platformUserRepository.findAll().stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRole().isTenantAdminEquivalent())
                .toList();

        String createdBy = announcement.getCreatedBy().getFullName();
        for (PlatformUser recipient : recipients) {
            emailNotificationService.sendCompanyAnnouncementEmail(
                    recipient.getEmail(),
                    recipient.getFullName(),
                    announcement.getTitle(),
                    announcement.getMessage(),
                    createdBy
            );
        }
    }

    private PlatformAnnouncementResponseDto toResponse(PlatformAnnouncement announcement) {
        return PlatformAnnouncementResponseDto.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .message(announcement.getMessage())
                .createdById(announcement.getCreatedBy().getId())
                .createdByName(announcement.getCreatedBy().getFullName())
                .createdByEmail(announcement.getCreatedBy().getEmail())
                .createdAt(announcement.getCreatedAt())
                .build();
    }
}
