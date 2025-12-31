package com.fincity.saas.entity.processor.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;

public class SortTypeAdapter extends TypeAdapter<Sort> {

    private static final String FIELD_PROPERTY = "property";
    private static final String FIELD_DIRECTION = "direction";
    private static final String FIELD_IGNORE_CASE = "ignoreCase";

    @Override
    public void write(JsonWriter out, Sort value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        if (!value.isSorted()) {
            out.nullValue();
            return;
        }

        List<Sort.Order> orders = value.toList();
        if (orders.size() == 1) {
            this.writeOrder(out, orders.getFirst());
        } else {
            out.beginArray();
            for (Sort.Order order : orders) {
                this.writeOrder(out, order);
            }
            out.endArray();
        }
    }

    private void writeOrder(JsonWriter out, Sort.Order order) throws IOException {
        out.beginObject();
        out.name(FIELD_PROPERTY).value(order.getProperty());
        out.name(FIELD_DIRECTION).value(order.getDirection().name());
        if (order.isIgnoreCase()) {
            out.name(FIELD_IGNORE_CASE).value(true);
        }
        out.endObject();
    }

    @Override
    public Sort read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.NULL) {
            in.nextNull();
            return Sort.unsorted();
        }

        if (token == JsonToken.STRING) {
            return Sort.by(Sort.Order.asc(in.nextString()));
        }

        if (token == JsonToken.BEGIN_OBJECT) {
            Sort.Order order = this.readOrderFromObject(in);
            return order != null ? Sort.by(order) : Sort.unsorted();
        }

        if (token == JsonToken.BEGIN_ARRAY) {
            in.beginArray();
            List<Sort.Order> orderList = new ArrayList<>();

            while (in.hasNext()) {
                Sort.Order order =
                        switch (in.peek()) {
                            case BEGIN_OBJECT -> this.readOrderFromObject(in);
                            case STRING -> Sort.Order.asc(in.nextString());
                            default -> {
                                in.skipValue();
                                yield null;
                            }
                        };

                if (order != null) orderList.add(order);
            }
            in.endArray();

            return orderList.isEmpty() ? Sort.unsorted() : Sort.by(orderList);
        }

        in.skipValue();
        return Sort.unsorted();
    }

    private Sort.Order readOrderFromObject(JsonReader in) throws IOException {
        in.beginObject();
        String property = null;
        Sort.Direction direction = Sort.Direction.ASC;
        boolean ignoreCase = false;

        while (in.hasNext()) {
            String fieldName = in.nextName();
            switch (fieldName) {
                case FIELD_PROPERTY, "field", "column" -> property = in.nextString();
                case FIELD_DIRECTION, "dir" -> direction = parseDirection(in.nextString());
                case FIELD_IGNORE_CASE -> ignoreCase = in.nextBoolean();
                default -> in.skipValue();
            }
        }
        in.endObject();

        if (property == null) return null;

        Sort.Order order = new Sort.Order(direction, property);
        return ignoreCase ? order.ignoreCase() : order;
    }

    private Sort.Direction parseDirection(String dirStr) {
        return dirStr != null && (dirStr.equalsIgnoreCase("DESC") || dirStr.equalsIgnoreCase("DESCENDING"))
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
    }
}
