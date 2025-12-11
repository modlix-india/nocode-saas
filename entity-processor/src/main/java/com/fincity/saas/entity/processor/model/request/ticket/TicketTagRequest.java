package com.fincity.saas.entity.processor.model.request.ticket;

import java.io.Serial;
import java.io.Serializable;

import com.fincity.saas.entity.processor.enums.TicketTagType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TicketTagRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6738569615029017150L;

    private TicketTagType tag;

    private String comment;
    private TaskRequest taskRequest;
}
