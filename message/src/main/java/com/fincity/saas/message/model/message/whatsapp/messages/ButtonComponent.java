package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ButtonSubType;
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
public class ButtonComponent extends Component<ButtonComponent> implements Serializable {

    @Serial
    private static final long serialVersionUID = 4719958375726074763L;

    @JsonProperty("index")
    private int index;

    @JsonProperty("sub_type")
    private ButtonSubType subType;

    public ButtonComponent() {
        super(ComponentType.BUTTON);
    }
}
