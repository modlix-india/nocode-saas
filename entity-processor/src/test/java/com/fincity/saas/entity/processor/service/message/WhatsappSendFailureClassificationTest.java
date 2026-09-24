package com.fincity.saas.entity.processor.service.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * Whether a failed send was WhatsApp refusing, or something of ours going wrong.
 *
 * <p>The whole rejection hold turns on this one classification, and getting it wrong fails silently
 * in both directions: read a refusal as an ordinary error and the number keeps sending into a live
 * restriction, which is the bug being fixed; read a connect timeout as a refusal and a momentary
 * blip rests the number for an hour.
 *
 * <p>The shapes below are the ones production actually produces. Reactive feign raises
 * {@link FeignException} for a status the message service answered with, by way of feign's default
 * ErrorDecoder, and wraps transport failures in a {@code ReactiveFeignException} carrying a
 * {@link WebClientRequestException} that has no status at all.
 */
class WhatsappSendFailureClassificationTest {

    private static final int WHATSAPP_REFUSED = 423;

    private static FeignException feignWith(int status) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://message/api/message/whatsapp/sessions/internal/ABC/messages",
                Map.of(),
                new byte[0],
                StandardCharsets.UTF_8,
                new RequestTemplate());

        return FeignException.errorStatus(
                "IFeignMessageService#sendWhatsappSessionMessage",
                feign.Response.builder()
                        .status(status)
                        .reason("")
                        .request(request)
                        .headers(Map.of())
                        .build());
    }

    @Test
    @DisplayName("a 423 from the bridge is read as WhatsApp refusing")
    void readsTheRefusalStatus() {
        assertEquals(WHATSAPP_REFUSED, WhatsappSessionService.statusOf(feignWith(WHATSAPP_REFUSED)));
    }

    /**
     * The status arrives buried. Reactor wraps on assembly and the send path adds its own layers, so
     * a classifier that only looked at the top of the chain would see nothing and file every refusal
     * as an ordinary failure.
     */
    @Test
    @DisplayName("the status is found however deeply the exception is wrapped")
    void unwrapsNestedCauses() {
        Throwable wrapped = new IllegalStateException("outer", new RuntimeException("inner", feignWith(423)));

        assertEquals(WHATSAPP_REFUSED, WhatsappSessionService.statusOf(wrapped));
    }

    /**
     * 409 is country_mismatch and not_sendable, which is exactly why the bridge stopped using it for
     * a refusal. Neither is a reason to rest the number.
     */
    @Test
    @DisplayName("a 409 is not a refusal, since three unrelated conditions share it")
    void conflictIsNotARefusal() {
        assertEquals(409, WhatsappSessionService.statusOf(feignWith(409)));
    }

    @Test
    @DisplayName("a transport failure carries no status, so it can never rest the number")
    void transportFailureHasNoStatus() {
        Throwable connectFailure = new RuntimeException(
                "connection refused",
                new WebClientRequestException(
                        new java.net.ConnectException("refused"),
                        org.springframework.http.HttpMethod.POST,
                        java.net.URI.create("http://message/x"),
                        org.springframework.http.HttpHeaders.EMPTY));

        assertNull(WhatsappSessionService.statusOf(connectFailure));
    }

    @Test
    @DisplayName("an error with no cause and no status is not mistaken for one")
    void plainErrorHasNoStatus() {
        assertNull(WhatsappSessionService.statusOf(new IllegalArgumentException("no session")));
    }
}
