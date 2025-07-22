package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;
import com.fincity.saas.message.model.message.whatsapp.templates.type.HeaderFormat;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeaderComponent extends Component<HeaderComponent> {
    
    private HeaderFormat format;

    
    public HeaderComponent() {
        super(ComponentType.HEADER);
    }

    
    public HeaderFormat getFormat() {
        return format;
    }

    
    public HeaderComponent setFormat(HeaderFormat format) {
        this.format = format;
        return this;
    }
}
