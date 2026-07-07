package com.worknest.master.repository;

import com.worknest.auth.dto.LoginAttemptBucketType;
import com.worknest.master.entity.AuthLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthLoginAttemptRepository extends JpaRepository<AuthLoginAttempt, Long> {
    Optional<AuthLoginAttempt> findByBucketTypeAndBucketKey(LoginAttemptBucketType bucketType, String bucketKey);
}