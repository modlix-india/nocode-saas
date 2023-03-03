package com.fincity.saas.commons.flattener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class JsonUnflattenerTest {

    @Test
    void test() {
        Map<String, String> flatList = Map.of("professionalDetails.address.building_name", "sri",
                "professionalDetails.address.pincode", "533003", "professionalDetails.address.city", "bnglr",
                "basicDetails.phone", "1234",
                "userType", "ACTIVE", "parentsName[0].firstName", "kiran", "parentsName[1].firstName", "kumar",
                "parentsName[0].a[0].b", "bvalue", "parentsName[0].a[1].b", "cValue");

        JsonObject jsono = new JsonObject();
        jsono.add("professionalDetails", new JsonObject());
        jsono.add("basicDetails", new JsonObject());
        jsono.addProperty("userType", "ACTIVE");
        jsono.getAsJsonObject("professionalDetails").add("address", new JsonObject());
        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").addProperty("building_name", "sri");
//                new JsonPrimitive("building_name"));
        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").addProperty("pincode", "533003");
//                new JsonPrimitive("pincode"));
        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").addProperty("city", "bnglr");
//                new JsonPrimitive("city"));
        jsono.getAsJsonObject("basicDetails").addProperty("phone", "1234");
        System.out.println(jsono);

        JsonObject result = JsonUnflattener.unflatten(flatList);
        System.out.println(result);
        assertEquals(jsono, result);
    }

//    @Test
//    void test2() {
//        List<String> flatList = List.of("professionalDetails.address.building_name",
//                "professionalDetails.address.pincode");
//        ,"parentsName[0].firstName","parentsName[1].firstName","parentsName[0].a[0].b","parentsName[0].a[0].c");

//        JsonObject jsono = new JsonObject();
//        jsono.add("professionalDetails", new JsonObject());
//        jsono.getAsJsonObject("professionalDetails").add("address", new JsonObject());
//        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").add("building_name", new JsonObject());
//        jsono.getAsJsonObject("professionalDetails").getAsJsonObject("address").add("pincode", new JsonObject());
//        System.out.println(jsono);
//        JsonObject result = JsonUnflattener.unflatten(flatList, new JsonObject());
//        System.out.println(result);
//        assertEquals(jsono, result);
//    }

}