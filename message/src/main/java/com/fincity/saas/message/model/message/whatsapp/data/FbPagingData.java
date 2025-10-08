package com.fincity.saas.message.model.message.whatsapp.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.response.Paging;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FbPagingData<D extends Serializable> extends FbData<D> {

    @Serial
    private static final long serialVersionUID = 2794107745308883981L;

    @JsonProperty("paging")
    private Paging paging;
}
