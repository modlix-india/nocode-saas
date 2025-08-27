package com.fincity.saas.entity.processor.model.request.ticket;

import com.fincity.saas.entity.processor.model.request.content.INoteRequest;
import com.fincity.saas.entity.processor.model.request.content.NoteRequest;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TicketReassignRequest implements INoteRequest, Serializable {

    @Serial
    private static final long serialVersionUID = 2820332844561129084L;

    private ULong userId;
    private String comment;
    private NoteRequest noteRequest;
}
