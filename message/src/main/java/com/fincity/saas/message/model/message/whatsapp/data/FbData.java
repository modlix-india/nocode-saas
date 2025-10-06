package com.fincity.saas.message.model.message.whatsapp.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FbData<D extends Serializable> implements Serializable {

    @JsonProperty("data")
    private List<D> data;
}
