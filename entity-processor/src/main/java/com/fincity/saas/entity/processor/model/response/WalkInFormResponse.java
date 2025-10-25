package com.fincity.saas.entity.processor.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.entity.processor.enums.AssignmentType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class WalkInFormResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 8523702812201154899L;

    @JsonIgnore
    private ULong productId;

    @JsonIgnore
    private ULong productTemplateId;

    @JsonIgnore
    private ULong stageId;

    @JsonIgnore
    private ULong statusId;

    private AssignmentType assignmentType;
}
