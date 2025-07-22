package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ButtonSubType;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ComponentType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ButtonComponent extends Component<ButtonComponent> {

    @JsonProperty("index")
    private int index;

    @JsonProperty("sub_type")
    private ButtonSubType subType;

    public ButtonComponent() {
        super(ComponentType.BUTTON);
    }
}
