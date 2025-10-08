package com.fincity.saas.entity.processor.model.request.content;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface INoteRequest {

    default NoteRequest getNoteRequest() {
        return null;
    }

    default String getComment() {
        return null;
    }

    @JsonIgnore
    default boolean hasNote() {
        return (getNoteRequest() != null && getNoteRequest().hasContent()) || getComment() != null;
    }
}
