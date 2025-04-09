package com.fincity.saas.commons.model.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public abstract class AbstractDTO<I extends Serializable, U extends Serializable> implements Serializable {

    @Serial
    private static final long serialVersionUID = 7628167781600904807L;

    @Id
    private I id;

    private LocalDateTime createdAt;
    private U createdBy;
}
