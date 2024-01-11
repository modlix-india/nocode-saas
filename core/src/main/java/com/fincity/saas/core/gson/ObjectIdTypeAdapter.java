package com.fincity.saas.core.gson;

import org.bson.types.ObjectId;

import com.google.gson.TypeAdapter;

public class ObjectIdTypeAdapter extends TypeAdapter<ObjectId> {

    @Override
    public void write(com.google.gson.stream.JsonWriter out, ObjectId value) throws java.io.IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    @Override
    public ObjectId read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        } else {
            return new ObjectId(in.nextString());
        }
    }
}
