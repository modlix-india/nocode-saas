package com.fincity.saas.entity.processor.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageableTypeAdapter extends TypeAdapter<Pageable> {

    private static final String FIELD_PAGE = "page";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_SORT = "sort";

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final String DEFAULT_SORT_FIELD = "id";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private final SortTypeAdapter sortAdapter = new SortTypeAdapter();

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
            this.sortAdapter.write(out, sort);
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
                case FIELD_SORT -> sort = this.sortAdapter.read(in);
                default -> in.skipValue();
            }
        }
        in.endObject();

        return PageRequest.of(page, size, sort);
    }

    private Sort getDefaultSort() {
        return Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD);
    }
}
