package com.fincity.saas.entity.processor.service;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Aspect
@Component
public class MethodUsageAspect {

    private final MethodUsageTracker tracker;

    public MethodUsageAspect(MethodUsageTracker tracker) {
        this.tracker = tracker;
    }

    @Pointcut("execution(public reactor.core.publisher.Mono "
            + "com.fincity.saas.entity.processor.service..*.*(..)) "
            + "&& !within(com.fincity.saas.entity.processor.service.MethodUsageAspect) "
            + "&& !within(com.fincity.saas.entity.processor.service.MethodUsageTracker)")
    public void monoServiceMethods() {}

    @Pointcut("execution(public reactor.core.publisher.Flux "
            + "com.fincity.saas.entity.processor.service..*.*(..)) "
            + "&& !within(com.fincity.saas.entity.processor.service.MethodUsageAspect) "
            + "&& !within(com.fincity.saas.entity.processor.service.MethodUsageTracker)")
    public void fluxServiceMethods() {}

    @Around("monoServiceMethods()")
    public Object trackMono(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        Object result = joinPoint.proceed();

        if (result instanceof Mono<?> mono) {
            return mono.doOnSubscribe(s -> tracker.record(method));
        }

        return result;
    }

    @Around("fluxServiceMethods()")
    public Object trackFlux(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        Object result = joinPoint.proceed();

        if (result instanceof Flux<?> flux) {
            return flux.doOnSubscribe(s -> tracker.record(method));
        }

        return result;
    }
}
