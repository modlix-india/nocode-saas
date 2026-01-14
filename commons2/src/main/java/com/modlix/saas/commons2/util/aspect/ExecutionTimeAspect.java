package com.modlix.saas.commons2.util.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecutionTimeAspect {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeAspect.class);

    @Around("@annotation(ExecutionTime)")
    public Object captureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {

        String method = joinPoint.getSignature().toShortString();
        long startTime = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            long elapsedNanos = System.nanoTime() - startTime;
            long timeMs = elapsedNanos / 1_000_000;
            logger.debug("{} completed in {} ms", method, timeMs);
            return result;
        } catch (Throwable throwable) {
            long elapsedNanos = System.nanoTime() - startTime;
            long timeMs = elapsedNanos / 1_000_000;
            logger.warn("{} failed in {} ms", method, timeMs, throwable);
            throw throwable;
        }
    }
}
