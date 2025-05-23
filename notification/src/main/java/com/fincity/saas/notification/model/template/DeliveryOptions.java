package com.fincity.saas.notification.model.template;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DeliveryOptions implements Serializable {

    @Serial
    private static final long serialVersionUID = 6058881914131731503L;

    private boolean instant = Boolean.TRUE;
    private String cronStatement;
    private boolean allowUnsubscribing = Boolean.TRUE;
}
