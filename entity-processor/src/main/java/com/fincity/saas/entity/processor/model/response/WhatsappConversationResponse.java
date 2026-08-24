package com.fincity.saas.entity.processor.model.response;

import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

/**
 * One row of the WhatsApp inbox.
 *
 * <p>Keyed by the customer's number rather than by deal, because that is what the customer sees on
 * their handset. A customer holding several deals produces one row here, not several, with {@link
 * #deals} listing them and {@link #primaryTicketId} naming the one the UI opens by default.
 *
 * <p>{@code unreadCount} and {@code lastMessagePreview} come from the message service and are
 * filled in per page rather than stored here, since they change on every read receipt.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WhatsappConversationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 4419283765512034887L;

    private Integer dialCode;
    private String phoneNumber;

    /** Most recent message either way, null for a deal that has never exchanged one. */
    private LocalDateTime lastMessageAt;

    /**
     * What the list is sorted on: {@code lastMessageAt} when there is a conversation, the deal's
     * {@code updatedAt} when there is not. The inbox lists every deal the caller can see, so
     * dormant ones sort by when they were last touched instead of dropping out.
     */
    private LocalDateTime orderedAt;

    private ULong primaryTicketId;
    private List<Deal> deals;

    private Integer unreadCount;
    private String lastMessagePreview;

    /**
     * The customer's WhatsApp avatar.
     *
     * <p>On the conversation rather than on each deal, because a conversation is a phone number and
     * so is a profile picture. The deals behind it all carry the same value; lifting it here saves
     * the client reaching into a list to find a face it is going to draw once.
     */
    private FileDetail profilePicFileDetail;

    /** The deals sharing this number, newest activity first. */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class Deal implements Serializable {

        @Serial
        private static final long serialVersionUID = 7710244690318823551L;

        private ULong id;
        private String code;
        private String name;
        private ULong productId;

        /**
         * The product's name, resolved for display.
         *
         * <p>Here because the deals sharing a number are frequently the <i>same lead</i> registered
         * against different products, so their {@code name} is identical on every one of them. A
         * picker showing three chips all reading "G&amp;SS1" tells the user nothing about which
         * deal a reply will be filed against, which is the single decision that picker exists to
         * support. The product is what distinguishes them.
         */
        private String productName;
        private ULong stage;
        private ULong status;
        private ULong assignedUserId;
        private LocalDateTime lastMessageAt;

        public static Deal of(Ticket ticket) {
            return new Deal()
                    .setId(ticket.getId())
                    .setCode(ticket.getCode())
                    .setName(ticket.getName())
                    .setProductId(ticket.getProductId())
                    .setStage(ticket.getStage())
                    .setStatus(ticket.getStatus())
                    .setAssignedUserId(ticket.getAssignedUserId())
                    .setLastMessageAt(ticket.getLastMessageAt());
        }
    }
}
