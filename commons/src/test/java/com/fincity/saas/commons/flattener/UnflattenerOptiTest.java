package com.fincity.saas.commons.flattener;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

class UnflattenerOptiTest {

    // first do for objects next verify for arrays

    @Test
    void test() {
        String flattener = "a.b[2].c[1].d[2].e[].f[]";
        String[] splited = flattener.split("\\.");

        int len = splited.length;
        JsonObject jsonObj = new JsonObject();

        if (len == 1) {
            if (splited[0].contains("[")) {
                jsonObj.add(splited[0].substring(0, splited[0].indexOf("[")), new JsonArray());
            } else
                jsonObj.add(splited[0], new JsonPrimitive(splited[len - 1]));

        } else {

            if (splited[0].contains("[")) {
                jsonObj.add(getTrimmedName(splited[0]), new JsonArray());
            } else {
                if (!jsonObj.has(splited[0]))
                    jsonObj.add(splited[0], new JsonObject());

            }
            JsonElement je = jsonObj;
            for (int i = 1; i <= len - 1; i++) {

                if (splited[i].contains("[") && i < len - 1) { // reevaluate condition here
                    System.out.println(je + " from array " + i);
                    System.out.println(jsonObj + " from array  json" + i);
                    if (je.getAsJsonObject().has(splited[i - 1])) {
                        je = je.getAsJsonObject().get(splited[i - 1]);

                        je.getAsJsonObject().add(getTrimmedName(splited[i]),
                                new JsonArray());

                    } else {
                        je.getAsJsonObject().add(getTrimmedName(splited[i]), new JsonArray());
                    }

                } else {
//                    if (splited[i - 1].contains("[") ? !je.getAsJsonArray().getAsJsonObject().has(splited[i - 1])
                    System.out.println(jsonObj + " from json " + i);
                    System.out.println(je + " from obj " + i);

                    if (splited[i - 1].contains("[") ? je.getAsJsonObject().has(getTrimmedName(splited[i - 1]))
                            : !je.getAsJsonObject().has(splited[i - 1])) {
//                        if (!jsonObj.has(splited[i]))
//                            jsonObj.add(splited[i], new JsonObject());
                        if (!je.getAsJsonObject().has(splited[i]))
                            je.getAsJsonObject().add(splited[i], new JsonObject());
                        else {
                            je = je.getAsJsonObject().get(getTrimmedName(splited[i - 1])).getAsJsonObject()
                                    .get(splited[i]);
//                            je.getAsJsonObject().get(splited[i]).getAsJsonObject().add(splited[i],
//                                    new JsonObject());
                        }
                    } else {
                        int j = i - 1;
                        while (j < i) {
                            je = je.getAsJsonObject().get(getTrimmedName(splited[j])).getAsJsonObject();
                            j++;
                        }
                        if (i != len - 1) {
                            if (!je.getAsJsonObject().has(splited[i]))
                                je.getAsJsonObject().add(splited[i], new JsonObject());
                        } else {
                            if (splited[i].contains("["))
                                je.getAsJsonObject().add(getTrimmedName(splited[i]), new JsonArray());
                            else
                                je.getAsJsonObject().add(splited[i], new JsonPrimitive(splited[i]));
                        }
                    }

                }

            }
        }
        System.out.println(jsonObj);
    }

    private String getTrimmedName(String str) {
        return str.contains("[") ? str.substring(0, str.indexOf("[")) : str;
    }

}