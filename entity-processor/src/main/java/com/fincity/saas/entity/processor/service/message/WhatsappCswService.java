package com.fincity.saas.entity.processor.service.message;

import com.fincity.saas.entity.processor.dao.message.WhatsappMessageDAO;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Meta's 24-hour customer service window, evaluated where the messages now live.
 *
 * <p>Outside the window, only an approved template may be sent. A free-form message is rejected by
 * Meta, so this decides whether the composer offers a text box or forces a template.
 *
 * <p>Moved here with the message data. The consequence, accepted deliberately: the message service
 * no longer holds the timestamps and can no longer defend itself, so a caller that gets this wrong
 * burns a real policy violation with Meta rather than being stopped locally. Tolerable while the
 * owning service is the only caller, and worth revisiting if that stops being true.
 */
@Service
public class WhatsappCswService {

    private static final int CUSTOMER_SERVICE_WINDOW_HOURS = 24;

    private final WhatsappMessageDAO dao;

    public WhatsappCswService(WhatsappMessageDAO dao) {
        this.dao = dao;
    }

    /**
     * Window state for a conversation.
     *
     * <p>Takes the resolved set of visible deals, the same union the thread read uses, so the
     * window is computed over the whole conversation. Scoping it to one deal would call the window
     * shut because the customer's last reply happened to be filed against a sibling deal.
     */
    public Mono<CswStatus> status(String appCode, String clientCode, java.util.List<ULong> ticketIds) {

        // Deliberately UTC. The stored timestamps are UTC, and the previous implementation used a
        // bare LocalDateTime.now(), which is correct on the servers and silently shortens the
        // window to 18.5 hours on an IST developer machine.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime windowStart = now.minusHours(CUSTOMER_SERVICE_WINDOW_HOURS);

        return this.dao
                .lastInboundAt(appCode, clientCode, ticketIds)
                .map(lastInbound -> {
                    boolean open = lastInbound.isAfter(windowStart);
                    return new CswStatus(
                            open,
                            false,
                            lastInbound.plusHours(CUSTOMER_SERVICE_WINDOW_HOURS),
                            lastInbound,
                            open ? Duration.between(now, lastInbound.plusHours(CUSTOMER_SERVICE_WINDOW_HOURS))
                                    .toMinutes()
                                    : 0);
                })
                // No inbound message ever means the customer has not written to us, so the window
                // was never opened. That is the cold-lead case: template only.
                .defaultIfEmpty(new CswStatus(false, true, null, null, 0));
    }

    /** Whether a free-form (non-template) message is permitted right now. */
    public Mono<Boolean> canSendFreeForm(String appCode, String clientCode, java.util.List<ULong> ticketIds) {
        return this.status(appCode, clientCode, ticketIds).map(CswStatus::windowOpen);
    }

    /**
     * @param minutesRemaining how long the composer can keep offering free-form text, so the UI can
     *     warn before it closes rather than failing the send when it does
     */
    public record CswStatus(
            boolean windowOpen,
            boolean isFirstMessage,
            LocalDateTime windowExpiresAt,
            LocalDateTime lastCustomerMessageAt,
            long minutesRemaining) {

        public boolean canOnlySendTemplateMessage() {
            return !windowOpen;
        }
    }
}
