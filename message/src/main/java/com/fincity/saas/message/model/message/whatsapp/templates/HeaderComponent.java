package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;
import com.fincity.saas.message.model.message.whatsapp.templates.type.HeaderFormat;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeaderComponent extends Component<HeaderComponent> {

    @Serial
    private static final long serialVersionUID = -7597048566423201662L;

    private HeaderFormat format;

    public HeaderComponent() {
        super(ComponentType.HEADER);
    }
}
