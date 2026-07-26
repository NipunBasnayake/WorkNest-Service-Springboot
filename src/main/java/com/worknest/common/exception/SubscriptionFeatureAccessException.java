package com.worknest.common.exception;

public class SubscriptionFeatureAccessException extends RuntimeException {

    public SubscriptionFeatureAccessException() {
        super("Subscription plan does not include this feature.");
    }
}
