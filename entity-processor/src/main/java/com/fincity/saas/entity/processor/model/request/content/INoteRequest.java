package com.fincity.saas.entity.processor.model.request.content;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface INoteRequest {

    NoteRequest getNoteRequest();

    String getComment();

    @JsonIgnore
    default boolean hasNote() {
        return (getNoteRequest() != null && getNoteRequest().hasContent()) || getComment() != null;
    }
}
