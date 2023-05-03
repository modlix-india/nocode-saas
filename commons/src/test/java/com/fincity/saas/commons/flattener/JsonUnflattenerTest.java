package com.fincity.saas.commons.flattener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

class JsonUnflattenerTest {

    @Test
    void test() {
        Map<String, String> flatList = Map.of("professionalDetails.address.building_name", "sri",
                "professionalDetails.address.pincode", "533003", "professionalDetails.address.city", "bnglr",
                "basicDetails.phone", "12341",
                "userType", "ACTIVE", "parentsName[0].firstName", "kiran", "parentsName[1].firstName", "kumar");

        var ja = new JsonArray();
        var temp1 = new JsonObject();
        temp1.add("firstName", new JsonPrimitive("kiran"));

        var temp2 = new JsonObject();
        temp2.add("firstName", new JsonPrimitive("kumar"));
        ja.add(temp1);
        ja.add(temp2);

        JsonObject jsono = new JsonObject();
        jsono.add("professionalDetails", new JsonObject());
        jsono.add("parentsName", ja);
        jsono.add("basicDetails", new JsonObject());
        jsono.addProperty("userType", "ACTIVE");
        jsono.getAsJsonObject("professionalDetails").add("address", new JsonObject());
        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").addProperty("city", "bnglr");
        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").addProperty("building_name", "sri");
        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").addProperty("pincode", 533003);
        jsono.getAsJsonObject("basicDetails").addProperty("phone", 12341);

        Map<String, Set<SchemaType>> propsSchema = new TreeMap<>();
        propsSchema.put("professionalDetails.address.building_name",
                Type.of(SchemaType.STRING).getAllowedSchemaTypes());
        propsSchema.put("professionalDetails.address.pincode", Type.of(SchemaType.LONG).getAllowedSchemaTypes());
        propsSchema.put("professionalDetails.address.city", Type.of(SchemaType.STRING).getAllowedSchemaTypes());
        propsSchema.put("basicDetails.phone", Type.of(SchemaType.INTEGER).getAllowedSchemaTypes());
        propsSchema.put("userType", Type.of(SchemaType.STRING).getAllowedSchemaTypes());
        propsSchema.put("parentsName[0].firstName", Type.of(SchemaType.STRING).getAllowedSchemaTypes());
        propsSchema.put("parentsName[1].firstName", Type.of(SchemaType.STRING).getAllowedSchemaTypes());
        propsSchema.put("parentsName[0].a[0].b", Type.of(SchemaType.STRING).getAllowedSchemaTypes());
        propsSchema.put("parentsName[0].a[1].b", Type.of(SchemaType.BOOLEAN).getAllowedSchemaTypes());

        JsonObject result = JsonUnflattener.unflatten(flatList, propsSchema);


        assertEquals(jsono, result);
    }

}