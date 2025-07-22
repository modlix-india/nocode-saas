package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Action {

    @JsonProperty("catalog_id")
    private String catalogId;

    @JsonProperty("product_retailer_id")
    private String productRetailerId;

    @JsonProperty("button")
    private String buttonText;

    @JsonProperty("buttons")
    private List<Button> buttons;

    @JsonProperty("sections")
    private List<Section> sections;
}
