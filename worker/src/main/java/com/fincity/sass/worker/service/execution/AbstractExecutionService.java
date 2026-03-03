package com.fincity.sass.worker.service.execution;

import com.fincity.sass.worker.dto.Task;
import com.modlix.saas.commons2.exception.GenericException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

public abstract class AbstractExecutionService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${worker.task-execution.timeout-minutes:5}")
    protected int timeoutMinutes;

    @Value("${worker.task-execution.max-result-length:2000}")
    protected int maxResultLength;

    public abstract String execute(Task task);

    protected String truncateResult(String result) {
        if (result == null) return null;

        if (result.length() <= maxResultLength) return result;

        return result.substring(0, maxResultLength) + "...";
    }

    protected <T> T runWithTimeout(Supplier<T> supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier).get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new GenericException(
                    HttpStatus.REQUEST_TIMEOUT, "Execution timed out after " + timeoutMinutes + " minutes", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof GenericException ge) throw ge;

            if (cause instanceof RuntimeException re) throw re;

            throw new GenericException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    cause != null ? cause.getMessage() : e.getMessage(),
                    cause != null ? cause : e);
        }
    }
}
