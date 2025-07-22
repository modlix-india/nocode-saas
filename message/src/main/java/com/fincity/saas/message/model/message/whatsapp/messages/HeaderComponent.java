package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ComponentType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class HeaderComponent extends Component<HeaderComponent> {

    public HeaderComponent() {
        super(ComponentType.HEADER);
    }
}
