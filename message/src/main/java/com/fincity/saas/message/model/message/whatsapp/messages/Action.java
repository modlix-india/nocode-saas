package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Action implements Serializable {

    @Serial
    private static final long serialVersionUID = 2249865771618667634L;

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
