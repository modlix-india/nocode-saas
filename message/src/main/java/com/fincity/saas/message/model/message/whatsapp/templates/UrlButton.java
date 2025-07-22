package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ButtonType;
import java.util.List;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UrlButton extends Button {

    private String url;

    @JsonProperty("example")
    private List<String> urlExample;

    
    protected UrlButton() {
        super(ButtonType.URL);
    }

    
    public UrlButton(String text) {
        super(ButtonType.URL, text);
    }

    
    public String getUrl() {
        return url;
    }

    
    public UrlButton setUrl(String url) {
        this.url = url;
        return this;
    }

    
    public List<String> getUrlExample() {
        return urlExample;
    }

    
    public UrlButton setUrlExample(List<String> urlExample) {
        this.urlExample = urlExample;
        return this;
    }
}
