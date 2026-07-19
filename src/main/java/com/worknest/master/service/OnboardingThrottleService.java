package com.worknest.master.service;

import com.worknest.common.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnboardingThrottleService {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;

    public OnboardingThrottleService(
            @Value("${app.onboarding.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.onboarding.rate-limit.window-minutes:60}") long windowMinutes) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.window = Duration.ofMinutes(Math.max(1, windowMinutes));
    }

    public void checkAndRecord(String remoteAddress) {
        String key = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
        Instant now = Instant.now();
        Deque<Instant> bucket = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            Instant cutoff = now.minus(window);
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) bucket.removeFirst();
            if (bucket.size() >= maxAttempts) {
                throw new TooManyRequestsException("Too many tenant registration attempts. Please try again later.");
            }
            bucket.addLast(now);
        }
        if (attempts.size() > 10_000) attempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
