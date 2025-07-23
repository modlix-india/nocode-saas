package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FooterComponent extends Component<FooterComponent> {

    @Serial
    private static final long serialVersionUID = -5473599325504980558L;

    public FooterComponent() {
        super(ComponentType.FOOTER);
    }
}
