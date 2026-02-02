package com.fincity.saas.message.model.message.whatsapp.media;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FileType {
    JPEG("image/jpeg"),
    PNG("image/png"),

    TEXT("text/plain"),
    PDF("application/pdf"),
    PPT("application/vnd.ms-powerpoint"),
    DOC("application/msword"),
    XLS("application/vnd.ms-excel"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),

    AAC("audio/aac"),
    MP4("audio/mp4"),
    MPEG("audio/mpeg"),
    AMR("audio/amr"),
    OGG("audio/ogg"),
    OPUS("audio/opus"),

    MP4_VIDEO("video/mp4"),
    THREEGP("video/3gp"),

    WEBP("image/webp");

    private final String type;

    FileType(String type) {
        this.type = type;
    }

    @JsonValue
    public String getType() {
        return type;
    }
}
