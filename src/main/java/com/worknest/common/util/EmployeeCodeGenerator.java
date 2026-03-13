package com.worknest.common.util;

import java.time.Year;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class to generate unique employee codes.
 * Format: EMP{YEAR}{SEQUENCE}
 * Example: EMP2024001, EMP2024002, etc.
 */
public class EmployeeCodeGenerator {

    private static final AtomicInteger sequence = new AtomicInteger(0);

    private EmployeeCodeGenerator() {
        // Private constructor to prevent instantiation
    }

    /**
     * Generate a unique employee code.
     * @return formatted employee code (e.g., EMP2024001)
     */
    public static String generateCode() {
        int currentYear = Year.now().getValue();
        int nextSequence = sequence.incrementAndGet();
        return String.format("EMP%d%03d", currentYear, nextSequence);
    }

    /**
     * Generate employee code with specific sequence number.
     * Used for testing or specific requirements.
     * @param sequenceNumber the sequence number
     * @return formatted employee code
     */
    public static String generateCode(int sequenceNumber) {
        int currentYear = Year.now().getValue();
        return String.format("EMP%d%03d", currentYear, sequenceNumber);
    }

    /**
     * Reset sequence counter.
     * Use with caution - primarily for testing.
     */
    public static void resetSequence() {
        sequence.set(0);
    }
}

