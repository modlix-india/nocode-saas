package com.fincity.saas.entity.processor.model.response.message;

import com.fincity.saas.entity.processor.dto.message.WhatsappMessage;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * One window of a conversation, plus where to ask for the next one.
 *
 * <h2>Why this is not a Spring Page</h2>
 *
 * <p>It deliberately keeps {@code content} under that name, and carries {@code totalElements}, so
 * the deal profile's WhatsApp panel - which reads this same endpoint with {@code page}/{@code size}
 * and binds {@code .content} - sees a response it already understands. Changing the shape for
 * everyone in order to fix one screen would have broken the other one silently, since a binding to a
 * field that no longer exists renders empty rather than failing.
 *
 * <p>What a Page cannot carry is the cursor, and the cursor is the point. A page number describes a
 * position in a list that is still growing at one end; a cursor describes a position in the
 * conversation, which is the thing that does not move.
 */
@Data
@Accessors(chain = true)
public class WhatsappThreadWindow {

    /** Newest first, matching how the thread is rendered. */
    private List<WhatsappMessage> content;

    /**
     * Total messages in the thread, kept for the callers that still page by number.
     *
     * <p>Not used by cursor callers, and deliberately not their signal for whether more exists:
     * comparing a running total against how many are on screen is exactly the arithmetic that goes
     * wrong the moment a message arrives mid-scroll. {@link #hasMore} answers that from the query.
     */
    private long totalElements;

    /**
     * Pass as {@code before} to get the next older window. Null when the thread is exhausted.
     *
     * <p>Taken from the last row actually returned, so it stays correct even when rows are filtered
     * out on the way back.
     */
    private String olderCursor;

    /**
     * Pass as {@code after} to collect what has arrived since. Null only when the window is empty.
     *
     * <p>This is what turns a live update from "refetch everything on screen" into one small query
     * for the handful of messages that are actually new.
     */
    private String newerCursor;

    /**
     * Whether anything older exists.
     *
     * <p>Answered by asking for one row more than was wanted and seeing whether it came back, rather
     * than by counting. A count is a second query and is stale by the time it is compared.
     */
    private boolean hasMore;
}
