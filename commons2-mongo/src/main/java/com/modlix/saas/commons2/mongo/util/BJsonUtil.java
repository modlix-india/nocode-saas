package com.modlix.saas.commons2.mongo.util;

import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.StreamSupport;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class BJsonUtil {

    public static Document from(Set<String> idKeys, JsonObject job) {

        Document doc = new Document();

        for (Entry<String, BsonValue> entry : ((BsonDocument) fromElement(idKeys, job)).entrySet()) {

            doc.append(entry.getKey(), entry.getValue());
        }

        return doc;
    }

    public static BsonValue fromElement(Set<String> idKeys, JsonElement value) { // NOSONAR
        // It doesn't make sense to break this method.

        if (value.isJsonNull()) {

            return BsonNull.VALUE;
        } else if (value.isJsonObject()) {

            BsonDocument doc = new BsonDocument();

            for (Entry<String, JsonElement> entry : value.getAsJsonObject()
                    .entrySet()) {

                BsonValue bValue;

                if (idKeys.contains(entry.getKey())) {
                    bValue = entry.getValue().isJsonArray()
                            ? new BsonArray(StreamSupport.stream(entry.getValue().getAsJsonArray().spliterator(), false)
                                    .map(JsonElement::getAsString).map(ObjectId::new).map(BsonObjectId::new).toList())
                            : new BsonObjectId(new ObjectId(entry.getValue().getAsString()));
                } else {
                    bValue = fromElement(Set.of(), entry.getValue());
                }

                doc.append(entry.getKey(), bValue);
            }

            return doc;
        } else if (value.isJsonArray()) {

            JsonArray ja = value.getAsJsonArray();

            BsonArray ba = new BsonArray(ja.size());
            for (JsonElement je : ja)
                ba.add(fromElement(Set.of(), je));

            return ba;
        } else if (value.isJsonPrimitive()) {

            JsonPrimitive jp = value.getAsJsonPrimitive();

            if (jp.isBoolean())
                return BsonBoolean.valueOf(jp.getAsBoolean());

            if (jp.isString())
                return new BsonString(jp.getAsString());

            if (jp.isNumber()) {

                Double bd = jp.getAsNumber()
                        .doubleValue();
                if (bd.doubleValue() == bd.intValue()) {
                    return new BsonInt32(bd.intValue());
                } else if (bd.doubleValue() == bd.longValue()) {
                    return new BsonInt64(bd.longValue());
                } else if (bd.doubleValue() == bd.floatValue()) {
                    return new BsonDouble(bd.floatValue()); // float value type
                }

                return new BsonDouble(bd); // returing as double value
            }

            return new BsonString(jp.getAsString());
        }

        return new BsonString(value.getAsString());
    }

    private BJsonUtil() {
    }
}

