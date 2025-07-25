package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Example implements Serializable {

    @Serial
    private static final long serialVersionUID = -2519552499207279269L;

    @JsonProperty("header_handle")
    private List<String> headerHandle;

    @JsonProperty("body_text")
    private List<List<String>> bodyText;

    @JsonProperty("header_text")
    private List<String> headerText;

    public Example addHeaderHandleExamples(String... example) {
        if (this.headerHandle == null) this.headerHandle = new ArrayList<>();
        if (example != null) this.headerHandle.addAll(Arrays.stream(example).toList());
        return this;
    }

    public Example addHeaderTextExamples(String... example) {
        if (this.headerText == null) this.headerText = new ArrayList<>();
        if (example != null) this.headerText.addAll(Arrays.stream(example).toList());
        return this;
    }

    public Example addBodyTextExamples(String... example) {
        if (bodyText == null) this.bodyText = new ArrayList<>();
        if (example != null) bodyText.add(Arrays.stream(example).toList());
        return this;
    }
}
