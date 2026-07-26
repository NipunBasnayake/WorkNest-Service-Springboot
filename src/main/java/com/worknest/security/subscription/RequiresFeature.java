package com.worknest.security.subscription;

import com.worknest.master.enums.FeatureKey;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    FeatureKey[] value();

    /**
     * Optional controller method parameter containing a tenant key or slug.
     * Authenticated tenant endpoints normally use the current principal.
     */
    String tenantParameter() default "";
}
