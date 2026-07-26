package com.worknest.master.enums;

/**
 * Identifies who initiated a subscription change. Future billing adapters can
 * use PAYMENT or WEBHOOK without changing subscription rules.
 */
public enum SubscriptionChangeSource {
    SYSTEM,
    MANUAL,
    PAYMENT,
    WEBHOOK
}
