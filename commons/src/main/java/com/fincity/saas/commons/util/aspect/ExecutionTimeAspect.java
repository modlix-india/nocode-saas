package com.fincity.saas.commons.util.aspect;

import java.util.concurrent.atomic.AtomicLong;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Aspect
@Component
public class ExecutionTimeAspect {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeAspect.class);

    @Around("@annotation(ReactiveTime)")
    public Object captureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {

        Object result = joinPoint.proceed();

        String method = joinPoint.getSignature().toShortString();

        if (result instanceof Mono<?> mono) return this.captureMonoTime(mono, method);

        if (result instanceof Flux<?> flux) return this.captureFluxTime(flux, method);

        return result;
    }

    private <T> Mono<T> captureMonoTime(Mono<T> mono, String method) {
        AtomicLong startTime = new AtomicLong();
        return mono.doOnSubscribe(subscription -> startTime.set(System.nanoTime()))
                .doFinally(signal -> this.logTime("Mono", method, startTime.get(), signal));
    }

    private <T> Flux<T> captureFluxTime(Flux<T> flux, String method) {
        AtomicLong startTime = new AtomicLong();
        return flux.doOnSubscribe(subscription -> startTime.set(System.nanoTime()))
                .doFinally(signal -> this.logTime("Flux", method, startTime.get(), signal));
    }

    private void logTime(String type, String method, long start, SignalType signal) {
        long elapsedNanos = System.nanoTime() - start;
        long timeMs = elapsedNanos / 1_000_000;

        switch (signal) {
            case ON_ERROR -> logger.warn("{} {} failed in {} ms", type, method, timeMs);
            case ON_COMPLETE -> logger.debug("{} {} completed in {} ms", type, method, timeMs);
            case CANCEL -> logger.debug("{} {} cancelled in {} ms", type, method, timeMs);
            default -> logger.debug("{} {} terminated with {} in {} ms", type, method, signal, timeMs);
        }
    }
}
