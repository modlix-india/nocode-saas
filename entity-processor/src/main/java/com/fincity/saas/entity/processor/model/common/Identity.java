package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@FieldNameConstants
@JsonDeserialize(using = Identity.IdentityDeserializer.class)
public class Identity implements Serializable {

    @Serial
    private static final long serialVersionUID = 8383641756815921507L;

    private BigInteger id;
    private String code;

    public static Identity of(BigInteger id, String code) {
        return new Identity().setId(id).setCode(code);
    }

    public static Identity of(BigInteger id) {
        return of(id, null);
    }

    public static Identity of(String code) {
        return of(null, code);
    }

    public boolean isNull() {
        return id == null && code == null;
    }

    public boolean isCode() {
        return code != null && id == null;
    }

    public Identity setCode(String code) {
        if (code.length() == BaseDto.CODE_LENGTH) this.code = code;

        try {
            this.id = new BigInteger(code);
        } catch (NumberFormatException e) {
            // nothing
            return this;
        }

        return this;
    }

    public boolean isId() {
        return id != null;
    }

    public ULong getULongId() {
        return ULongUtil.valueOf(this.getId());
    }

    public static class IdentityDeserializer extends JsonDeserializer<Identity> {

        @Override
        public Identity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

            JsonNode node = p.getCodec().readTree(p);

            if (node.isNumber()) return Identity.of(node.bigIntegerValue());

            if (node.isTextual()) return Identity.of(node.asText());

            BigInteger id = node.has(Fields.id) ? node.get(Fields.id).bigIntegerValue() : null;
            String code = node.has(Fields.code) ? node.get(Fields.code).asText() : null;

            return Identity.of(id, code);
        }
    }
}
