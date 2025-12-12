package com.fincity.saas.entity.processor.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageTypeAdapter<T> extends TypeAdapter<Page<T>> {

    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_PAGEABLE = "pageable";
    private static final String FIELD_TOTAL_ELEMENTS = "totalElements";
    private static final String FIELD_TOTAL_PAGES = "totalPages";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_NUMBER = "number";
    private static final String FIELD_NUMBER_OF_ELEMENTS = "numberOfElements";
    private static final String FIELD_FIRST = "first";
    private static final String FIELD_LAST = "last";
    private static final String FIELD_EMPTY = "empty";
    private static final String FIELD_SORT = "sort";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final String DEFAULT_SORT_FIELD = "id";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private final Gson gson;
    private final Type contentType;

    public PageTypeAdapter(Gson gson, Type contentType) {
        this.gson = gson;
        this.contentType = contentType;
    }

    @Override
    public void write(JsonWriter out, Page<T> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        out.beginObject();
        out.name(FIELD_CONTENT);
        this.gson.toJson(value.getContent(), this.contentType, out);

        out.name(FIELD_PAGEABLE);
        this.gson.toJson(value.getPageable(), Pageable.class, out);

        out.name(FIELD_TOTAL_ELEMENTS).value(value.getTotalElements());
        out.name(FIELD_TOTAL_PAGES).value(value.getTotalPages());
        out.name(FIELD_SIZE).value(value.getSize());
        out.name(FIELD_NUMBER).value(value.getNumber());
        out.name(FIELD_NUMBER_OF_ELEMENTS).value(value.getNumberOfElements());
        out.name(FIELD_FIRST).value(value.isFirst());
        out.name(FIELD_LAST).value(value.isLast());
        out.name(FIELD_EMPTY).value(value.isEmpty());

        if (value.getSort().isSorted()) {
            out.name(FIELD_SORT);
            this.gson.toJson(value.getSort(), Sort.class, out);
        }

        out.endObject();
    }

    @Override
    public Page<T> read(JsonReader in) throws IOException {
        JsonToken token = in.peek();

        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        if (token != JsonToken.BEGIN_OBJECT) {
            in.skipValue();
            return null;
        }

        in.beginObject();
        List<T> content = new ArrayList<>();
        Pageable pageable = null;
        long totalElements = 0;

        while (in.hasNext()) {
            String fieldName = in.nextName();
            switch (fieldName) {
                case FIELD_CONTENT -> {
                    in.beginArray();
                    while (in.hasNext()) {
                        T item = this.gson.fromJson(in, this.contentType);
                        if (item != null) content.add(item);
                    }
                    in.endArray();
                }
                case FIELD_PAGEABLE -> pageable = this.gson.fromJson(in, Pageable.class);
                case FIELD_TOTAL_ELEMENTS -> totalElements = in.nextLong();
                default -> in.skipValue();
            }
        }
        in.endObject();

        if (pageable == null) {
            Sort defaultSort = Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD);
            pageable = PageRequest.of(DEFAULT_PAGE, DEFAULT_SIZE, defaultSort);
        }

        return new PageImpl<>(content, pageable, totalElements);
    }

    public static class Factory implements TypeAdapterFactory {

        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (!Page.class.isAssignableFrom(type.getRawType())) {
                return null;
            }

            Type contentType = Object.class;
            if (type.getType() instanceof ParameterizedType paramType) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    contentType = typeArgs[0];
                }
            }

            return (TypeAdapter<T>) new PageTypeAdapter<>(gson, contentType);
        }
    }
}
