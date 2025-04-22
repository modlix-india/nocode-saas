package com.fincity.saas.entity.processor.model.base;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@FieldNameConstants
@JsonDeserialize(using = Email.EmailDeserializer.class)
public class Email implements Serializable {

    @Serial
    private static final long serialVersionUID = 5559427114893544785L;

    private String address;

    public static Email of(String email) {
        return new Email().setAddress(email);
    }

    public static class EmailDeserializer extends JsonDeserializer<Email> {
        @Override
        public Email deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

            JsonNode node = p.getCodec().readTree(p);

            if (node.isTextual()) return Email.of(node.asText());

            String mail = node.has(Fields.address) ? node.get(Fields.address).asText() : null;
            return Email.of(mail);
        }
    }
}
