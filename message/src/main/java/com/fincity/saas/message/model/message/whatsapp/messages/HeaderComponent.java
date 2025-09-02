package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ComponentType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeaderComponent extends Component<HeaderComponent> implements Serializable {

    @Serial
    private static final long serialVersionUID = -1879757547870722340L;

    public HeaderComponent() {
        super(ComponentType.HEADER);
    }
}
