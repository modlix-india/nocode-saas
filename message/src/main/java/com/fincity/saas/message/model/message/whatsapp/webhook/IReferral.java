package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IReferral implements Serializable {

    @Serial
    private static final long serialVersionUID = 9120698424964363780L;

    @JsonProperty("video_url")
    private String videoUrl;

    @JsonProperty("media_type")
    private String mediaType;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("source_type")
    private String sourceType;

    @JsonProperty("source_id")
    private String sourceId;

    @JsonProperty("body")
    private String body;

    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    @JsonProperty("headline")
    private String headline;

    @JsonProperty("source_url")
    private String sourceUrl;
}
