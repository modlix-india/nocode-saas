package com.modlix.saas.commons2.configuration;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import com.modlix.saas.commons2.exception.GenericException;

import feign.FeignException;
import jakarta.annotation.Priority;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@Priority(0)
public class ControllerAdvice {

    @Autowired
    private AbstractMessageService resourceService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(ControllerAdvice.class);

    @ExceptionHandler(GenericException.class)
    public ResponseEntity<Object> handleGenericException(GenericException ex) {
        logger.debug("GenericException Occurred : ", ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.toExceptionData());
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Object> handleFeignException(FeignException fe) {
        logger.debug("FeignException Occurred : ", fe);
        return handleFeignExceptionInternal(fe);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOtherExceptions(Exception ex) {
        logger.debug("Exception Occurred : ", ex);
        return handleOtherExceptionsInternal(ex);
    }

    private ResponseEntity<Object> handleOtherExceptionsInternal(Exception ex) {
        String eId = GenericException.uniqueId();
        String msg = resourceService.getMessageSync(AbstractMessageService.UNKNOWN_ERROR_WITH_ID, eId);

        logger.error("Error : {}", eId, ex);

        final HttpStatus status = (ex instanceof ResponseStatusException rse)
                ? HttpStatus.valueOf(rse.getStatusCode().value())
                : HttpStatus.INTERNAL_SERVER_ERROR;

        GenericException g = new GenericException(status, eId, msg, ex);
        return ResponseEntity.status(g.getStatusCode())
                .body(g.toExceptionData());
    }

    private ResponseEntity<Object> handleFeignExceptionInternal(FeignException fe) {
        Optional<ByteBuffer> byteBuffer = fe.responseBody();
        if (byteBuffer.isPresent() && byteBuffer.get()
                .hasArray()) {

            Collection<String> ctype = fe.responseHeaders()
                    .get(HttpHeaders.CONTENT_TYPE);
            if (ctype != null && ctype.contains("application/json")) {
                try {
                    Map<String, Object> map = this.objectMapper.readValue(byteBuffer.get()
                            .array(), new TypeReference<Map<String, Object>>() {
                    });
                    GenericException g = new GenericException(HttpStatus.valueOf(fe.status()),
                            map.get("message") == null ? ""
                                    : map.get("message")
                                    .toString(),
                            fe);
                    return ResponseEntity.status(g.getStatusCode())
                            .body(g.toExceptionData());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // Fallback to generic error handling
        return handleOtherExceptionsInternal(fe);
    }

}
