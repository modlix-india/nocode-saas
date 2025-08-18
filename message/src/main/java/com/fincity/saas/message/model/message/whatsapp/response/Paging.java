package com.fincity.saas.message.model.message.whatsapp.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Paging implements Serializable {

    @Serial
    private static final long serialVersionUID = 1742946410551923308L;

    @JsonProperty("cursors")
    private Cursors cursors;

    @JsonProperty("next")
    private String next;
}
