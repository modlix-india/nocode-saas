package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Describes one record so security can say who is entitled to see it.
 *
 * <p>The fields are the columns an owning service's access condition tests, named generically
 * because security must not learn what a deal is. Today the only caller is entity-processor asking
 * about a ticket, where {@code assignedUserId} and {@code createdBy} are exactly the two user
 * columns {@code TicketDAO} filters on.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class RecordAudienceRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5241893206301985513L;

    /** The client that owns the record. Its reporting tree and hierarchy are what get walked. */
    private BigInteger clientId;

    /**
     * The same client, by code, for callers whose records only carry the code.
     *
     * <p>Not a convenience. Entity-processor's tickets populate {@code CLIENT_CODE} on every row and
     * {@code CLIENT_ID} on fewer than one in five, so an id-only contract silently resolved an empty
     * audience for most deals, and an empty audience is indistinguishable from "nobody is entitled".
     * Either field identifies the client; {@code clientId} wins when both are set.
     */
    private String clientCode;

    /** The user the record is assigned to, if the owning service assigns records to people. */
    private BigInteger assignedUserId;

    /** Who created it. What business-partner access is filtered on instead of the assignee. */
    private BigInteger createdBy;

    /** Only active users are returned when true, which is what a notification audience wants. */
    private boolean activeOnly = true;
}
