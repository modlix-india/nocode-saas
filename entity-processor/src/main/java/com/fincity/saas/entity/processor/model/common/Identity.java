package com.fincity.saas.entity.processor.model.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
@JsonSerialize(using = Identity.IdentitySerializer.class)
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

    public static Identity ofNull() {
        return of(null, null);
    }

    @JsonIgnore
    public boolean isId() {
        return id != null;
    }

    @JsonIgnore
    public ULong getULongId() {
        return ULongUtil.valueOf(this.getId());
    }

    @JsonIgnore
    public boolean isNull() {
        return id == null && code == null;
    }

    @JsonIgnore
    public boolean isCode() {
        return code != null && id == null;
    }

    public Identity setCode(String code) {

        if (code == null) return this;
        if (code.length() == BaseDto.CODE_LENGTH) this.code = code;

        try {
            this.id = new BigInteger(code);
        } catch (NumberFormatException e) {
            // nothing
            return this;
        }

        return this;
    }

    @Override
    public String toString() {
        if (isNull()) return "null";
        if (isCode()) return code;
        if (isId()) return id.toString();
        return "Identity{id=" + id + ", code='" + code + "'}";
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

    public static class IdentitySerializer extends JsonSerializer<Identity> {

        @Override
        public void serialize(Identity value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

            if (value.getId() != null && value.getCode() != null) {
                gen.writeStartObject();
                gen.writeObjectField(Fields.id, value.getId());
                gen.writeStringField(Fields.code, value.getCode());
                gen.writeEndObject();
            } else if (value.getId() != null) {
                gen.writeNumber(value.getId());
            } else if (value.getCode() != null) {
                gen.writeString(value.getCode());
            } else {
                gen.writeNull();
            }
        }
    }
}
