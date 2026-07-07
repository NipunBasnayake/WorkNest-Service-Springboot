package com.worknest.auth.service.impl;

import com.worknest.auth.dto.LoginAttemptBucketType;
import com.worknest.auth.service.AuthLoginThrottleService;
import com.worknest.common.exception.TooManyRequestsException;
import com.worknest.master.entity.AuthLoginAttempt;
import com.worknest.master.repository.AuthLoginAttemptRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class AuthLoginThrottleServiceImpl implements AuthLoginThrottleService {

    private final AuthLoginAttemptRepository repository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final int maxAttempts;
    private final int lockMinutes;

    public AuthLoginThrottleServiceImpl(
            AuthLoginAttemptRepository repository,
            MasterTenantContextRunner masterTenantContextRunner,
            @Value("${app.auth.throttle.max-attempts:5}") int maxAttempts,
            @Value("${app.auth.throttle.lock-minutes:15}") int lockMinutes) {
        this.repository = repository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.maxAttempts = Math.max(3, maxAttempts);
        this.lockMinutes = Math.max(5, lockMinutes);
    }

    @Override
    public void assertLoginAllowed(String identifier, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        AuthLoginAttempt identifierBucket = findBucket(LoginAttemptBucketType.IDENTIFIER, normalize(identifier));
        AuthLoginAttempt ipBucket = findBucket(LoginAttemptBucketType.IP_ADDRESS, normalize(ipAddress));

        if (isLocked(identifierBucket, now) || isLocked(ipBucket, now)) {
            throw new TooManyRequestsException("Too many login attempts. Please try again later.");
        }
    }

    @Override
    public void recordSuccessfulLogin(String identifier, String ipAddress) {
        masterTenantContextRunner.runInMasterContext(() -> {
            resetBucket(LoginAttemptBucketType.IDENTIFIER, normalize(identifier));
            resetBucket(LoginAttemptBucketType.IP_ADDRESS, normalize(ipAddress));
            return null;
        });
    }

    @Override
    public void recordFailedLogin(String identifier, String ipAddress, String userAgent) {
        masterTenantContextRunner.runInMasterContext(() -> {
            bumpBucket(LoginAttemptBucketType.IDENTIFIER, normalize(identifier), ipAddress, userAgent);
            bumpBucket(LoginAttemptBucketType.IP_ADDRESS, normalize(ipAddress), ipAddress, userAgent);
            return null;
        });
    }

    private AuthLoginAttempt findBucket(LoginAttemptBucketType bucketType, String bucketKey) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return null;
        }
        return masterTenantContextRunner.runInMasterContext(() -> repository.findByBucketTypeAndBucketKey(bucketType, bucketKey).orElse(null));
    }

    private void resetBucket(LoginAttemptBucketType bucketType, String bucketKey) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return;
        }

        repository.findByBucketTypeAndBucketKey(bucketType, bucketKey).ifPresentOrElse(bucket -> {
            bucket.setFailedAttempts(0);
            bucket.setLockedUntil(null);
            bucket.setLastAttemptAt(LocalDateTime.now());
            repository.save(bucket);
        }, () -> {
            AuthLoginAttempt bucket = new AuthLoginAttempt();
            bucket.setBucketType(bucketType);
            bucket.setBucketKey(bucketKey);
            bucket.setFailedAttempts(0);
            bucket.setLastAttemptAt(LocalDateTime.now());
            repository.save(bucket);
        });
    }

    private void bumpBucket(LoginAttemptBucketType bucketType, String bucketKey, String ipAddress, String userAgent) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return;
        }

        AuthLoginAttempt bucket = repository.findByBucketTypeAndBucketKey(bucketType, bucketKey)
                .orElseGet(() -> {
                    AuthLoginAttempt created = new AuthLoginAttempt();
                    created.setBucketType(bucketType);
                    created.setBucketKey(bucketKey);
                    created.setFailedAttempts(0);
                    return created;
                });

        bucket.setFailedAttempts(bucket.getFailedAttempts() + 1);
        bucket.setLastAttemptAt(LocalDateTime.now());
        bucket.setLastIpAddress(ipAddress);
        bucket.setLastUserAgent(userAgent);

        if (bucket.getFailedAttempts() >= maxAttempts) {
            bucket.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
        }

        repository.save(bucket);
    }

    private boolean isLocked(AuthLoginAttempt attempt, LocalDateTime now) {
        return attempt != null && attempt.getLockedUntil() != null && attempt.getLockedUntil().isAfter(now);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}