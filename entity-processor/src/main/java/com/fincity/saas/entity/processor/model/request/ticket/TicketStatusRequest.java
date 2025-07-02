package com.fincity.saas.entity.processor.model.request.ticket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.model.request.content.TaskRequest;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TicketStatusRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 5481917829025092560L;

    private Identity statusId;

    private Identity stageId;

    private String comment;
    private TaskRequest taskRequest;

    @JsonIgnore
    public boolean hasTask() {
        return this.taskRequest != null;
    }
}
