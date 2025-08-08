package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ButtonType;
import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UrlButton extends Button {

    @Serial
    private static final long serialVersionUID = -7539730767103112070L;

    private String url;

    @JsonProperty("example")
    private List<String> urlExample;

    protected UrlButton() {
        super(ButtonType.URL);
    }

    public UrlButton(String text) {
        super(ButtonType.URL, text);
    }
}
