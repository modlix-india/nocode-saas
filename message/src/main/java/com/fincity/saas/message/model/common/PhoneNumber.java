package com.fincity.saas.message.model.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fincity.saas.message.util.PhoneUtil;
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
@JsonDeserialize(using = PhoneNumber.PhoneNumberDeserializer.class)
public class PhoneNumber implements Serializable {

    @Serial
    private static final long serialVersionUID = 1855000683356483889L;

    private Integer countryCode = PhoneUtil.getDefaultCallingCode();
    private String number;

    public static PhoneNumber of(Integer countryCode, String phoneNumber) {
        return PhoneUtil.parse(countryCode, phoneNumber);
    }

    public static PhoneNumber of(String phoneNumber) {
        return of(null, phoneNumber);
    }

    public static PhoneNumber ofWhatsapp(String phoneNumber) {
        return of(null, "+" + phoneNumber);
    }

    public static class PhoneNumberDeserializer extends JsonDeserializer<PhoneNumber> {
        @Override
        public PhoneNumber deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

            JsonNode node = p.getCodec().readTree(p);

            if (node.isTextual()) return PhoneNumber.of(node.asText());

            Integer countryCode =
                    node.has(Fields.countryCode) ? node.get(Fields.countryCode).asInt() : null;
            String number = node.has(Fields.number) ? node.get(Fields.number).asText() : null;

            return PhoneNumber.of(countryCode, number);
        }
    }
}
