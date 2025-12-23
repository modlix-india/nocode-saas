package com.fincity.saas.entity.processor.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageableTypeAdapter extends TypeAdapter<Pageable> {

    private static final String FIELD_PAGE = "page";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_SORT = "sort";
    private static final String FIELD_PROPERTY = "property";
    private static final String FIELD_DIRECTION = "direction";
    private static final String FIELD_IGNORE_CASE = "ignoreCase";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final String DEFAULT_SORT_FIELD = "id";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    @Override
    public void write(JsonWriter out, Pageable value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        out.beginObject();
        out.name(FIELD_PAGE).value(value.getPageNumber());
        out.name(FIELD_SIZE).value(value.getPageSize());

        Sort sort = value.getSort();
        if (sort.isSorted()) {
            out.name(FIELD_SORT);
            this.writeSort(out, sort);
        }
        out.endObject();
    }

    @Override
    public Pageable read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        return switch (token) {
            case NULL -> {
                in.nextNull();
                yield this.getDefaultPageable();
            }
            case BEGIN_OBJECT -> this.readPageRequest(in);
            default -> {
                in.skipValue();
                yield this.getDefaultPageable();
            }
        };
    }

    private Pageable getDefaultPageable() {
        return PageRequest.of(DEFAULT_PAGE, DEFAULT_SIZE, this.getDefaultSort());
    }

    private Pageable readPageRequest(JsonReader in) throws IOException {
        in.beginObject();
        int page = DEFAULT_PAGE;
        int size = DEFAULT_SIZE;
        Sort sort = this.getDefaultSort();

        while (in.hasNext()) {
            String fieldName = in.nextName();
            switch (fieldName) {
                case FIELD_PAGE -> page = in.nextInt();
                case FIELD_SIZE -> size = in.nextInt();
                case FIELD_SORT -> sort = this.readSort(in);
                default -> in.skipValue();
            }
        }
        in.endObject();

        return PageRequest.of(page, size, sort);
    }

    private Sort getDefaultSort() {
        return Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD);
    }

    private void writeSort(JsonWriter out, Sort sort) throws IOException {
        out.beginArray();
        for (Sort.Order order : sort) {
            out.beginObject();
            out.name(FIELD_PROPERTY).value(order.getProperty());
            out.name(FIELD_DIRECTION).value(order.getDirection().name());

            if (order.isIgnoreCase()) out.name(FIELD_IGNORE_CASE).value(true);

            out.endObject();
        }
        out.endArray();
    }

    private Sort readSort(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.NULL) {
            in.nextNull();
            return Sort.unsorted();
        }

        if (token == JsonToken.STRING) return Sort.by(Sort.Order.asc(in.nextString()));

        if (token != JsonToken.BEGIN_ARRAY) {
            in.skipValue();
            return Sort.unsorted();
        }

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
