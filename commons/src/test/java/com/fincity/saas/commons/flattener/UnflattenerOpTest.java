package com.fincity.saas.commons.flattener;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

class UnflattenerOpTest {

    // first do for objects next verify for arrays

    @Test
    void test() {
        String flattener = "parentsName[1].a[2].b";
        String[] splited = flattener.split("\\.");

        int len = splited.length;
        JsonObject jsonObj = new JsonObject();

        if (len == 1) {
            if (splited[0].contains("[")) {
                jsonObj.add(splited[0].substring(0, splited[0].indexOf("[")), new JsonArray());
            } else
                jsonObj.add(splited[0], new JsonPrimitive(splited[len - 1]));
        } else {

            if (splited[len - 1].contains("[")) {
                jsonObj.add(splited[len - 1].substring(0, splited[len - 1].indexOf("[")), new JsonArray());
            } else {
                jsonObj.add(splited[len - 1], new JsonObject());
            }
            for (int i = len - 2; i >= 0; i--) {
                if (splited[i].contains("[")) {
                    JsonElement temp = splited[i + 1].contains("[")
                            ? jsonObj
                                    .getAsJsonArray(splited[i + 1].substring(0, splited[i + 1].indexOf("["))).deepCopy()

                            : jsonObj.getAsJsonObject(splited[i + 1]).deepCopy();
                    jsonObj.add(splited[i].substring(0, splited[i].indexOf("[")), new JsonArray());
                    JsonObject jsonTemp = new JsonObject();
                    if (splited[i + 1].contains("[")) {
                        JsonArray jsonArr = new JsonArray();
                        jsonArr.add(temp.deepCopy());
                        jsonTemp.add(splited[i + 1].substring(0, splited[i + 1].indexOf("[")),
                                jsonArr.deepCopy());
                    } else {
                        jsonTemp.add(splited[i + 1], temp.deepCopy());
                    }
                    jsonTemp.add(splited[i + 1].contains("[") ? splited[i + 1].substring(0, splited[i + 1].indexOf("["))
                            : splited[i + 1], temp.deepCopy());
                    jsonObj.getAsJsonArray(splited[i].substring(0, splited[i].indexOf("["))).add(jsonTemp.deepCopy());
                    jsonObj.remove(
                            splited[i + 1].contains("[") ? splited[i + 1].substring(0, splited[i + 1].indexOf("["))
                                    : splited[i + 1]);

                } else {
                    if (jsonObj.has(splited[i + 1])) {
                        JsonElement temp = splited[i + 1].contains("[")
                                ? jsonObj
                                        .getAsJsonArray(splited[i + 1].substring(0, splited[i + 1].indexOf("[")))
                                        .deepCopy()
                                : jsonObj.getAsJsonObject(splited[i + 1]).deepCopy();

                        jsonObj.remove(
                                splited[i + 1].contains("[") ? splited[i + 1].substring(0, splited[i + 1].indexOf("["))
                                        : splited[i + 1]);
                        if (jsonObj.has(splited[i]))
                            jsonObj.add(splited[i], jsonObj.getAsJsonObject().deepCopy());
                        else
                            jsonObj.add(splited[i], new JsonObject());

                        jsonObj.getAsJsonObject(splited[i])
                                .add(splited[i + 1].contains("[")
                                        ? splited[i + 1].substring(0, splited[i + 1].indexOf("["))
                                        : splited[i + 1], temp.deepCopy());
                    } else {
                        jsonObj.getAsJsonObject(splited[i])
                                .add(splited[i + 1].contains("[")
                                        ? splited[i + 1].substring(0, splited[i + 1].indexOf("["))
                                        : splited[i + 1], new JsonObject());
                    }
                }
            }
        }

        // replacing nested element with json primitive

        System.out.println(jsonObj);
    }
}
