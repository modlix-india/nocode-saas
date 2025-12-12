package com.fincity.saas.entity.processor.model.request.ticket;

import com.fincity.saas.entity.processor.enums.Tag;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TicketTagRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 6738569615029017150L;

    private Tag tag;

    private String comment;
    private TaskRequest taskRequest;
}
