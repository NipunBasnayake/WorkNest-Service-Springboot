package com.worknest.security.subscription;

import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.enums.FeatureKey;
import com.worknest.master.service.FeatureAccessService;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.security.util.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class FeatureAccessAspect {

    private final FeatureAccessService featureAccessService;
    private final SecurityUtils securityUtils;
    private final MasterTenantLookupService masterTenantLookupService;

    public FeatureAccessAspect(
            FeatureAccessService featureAccessService,
            SecurityUtils securityUtils,
            MasterTenantLookupService masterTenantLookupService) {
        this.featureAccessService = featureAccessService;
        this.securityUtils = securityUtils;
        this.masterTenantLookupService = masterTenantLookupService;
    }

    @Around("@within(com.worknest.security.subscription.RequiresFeature) || "
            + "@annotation(com.worknest.security.subscription.RequiresFeature)")
    public Object enforceFeatureAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        RequiresFeature requirement = resolveRequirement(joinPoint);
        String tenantKey = resolveTenantKey(joinPoint, requirement);
        for (FeatureKey featureKey : requirement.value()) {
            featureAccessService.requireFeature(tenantKey, featureKey);
        }
        return joinPoint.proceed();
    }

    private RequiresFeature resolveRequirement(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(
                signature.getMethod(),
                AopUtils.getTargetClass(joinPoint.getTarget()));
        RequiresFeature requirement = AnnotationUtils.findAnnotation(method, RequiresFeature.class);
        if (requirement == null) {
            requirement = AnnotationUtils.findAnnotation(
                    AopUtils.getTargetClass(joinPoint.getTarget()),
                    RequiresFeature.class);
        }
        if (requirement == null) {
            throw new IllegalStateException("Feature requirement metadata is missing");
        }
        return requirement;
    }

    private String resolveTenantKey(
            ProceedingJoinPoint joinPoint,
            RequiresFeature requirement) {
        if (!requirement.tenantParameter().isBlank()) {
            String tenantIdentifier = resolveParameter(
                    joinPoint,
                    requirement.tenantParameter());
            return masterTenantLookupService.findByTenantKey(tenantIdentifier)
                    .or(() -> masterTenantLookupService.findBySlug(tenantIdentifier))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found: " + tenantIdentifier))
                    .getTenantKey();
        }
        try {
            return securityUtils.getCurrentTenantKeyOrThrow();
        } catch (ForbiddenOperationException exception) {
            throw new ForbiddenOperationException(
                    "Tenant context is required for feature authorization");
        }
    }

    private String resolveParameter(ProceedingJoinPoint joinPoint, String parameterName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        for (int index = 0; index < names.length; index++) {
            if (parameterName.equals(names[index]) && arguments[index] != null) {
                String value = String.valueOf(arguments[index]).trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        throw new IllegalArgumentException(
                "Tenant parameter '" + parameterName + "' is required");
    }
}
