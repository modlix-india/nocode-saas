package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageType;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 6162760594885532126L;

    @JsonProperty("reaction")
    private IReaction reaction;

    @JsonProperty("image")
    private IImage image;

    @JsonProperty("sticker")
    private ISticker sticker;

    @JsonProperty("location")
    private ILocation location;

    @JsonProperty("contacts")
    private List<IContact> contacts;

    @JsonProperty("button")
    private IButton button;

    @JsonProperty("context")
    private IContext context;

    @JsonProperty("from")
    private String from;

    @JsonProperty("id")
    private String id;

    @JsonProperty("text")
    private IText text;

    @JsonProperty("errors")
    private List<IError> errors;

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("interactive")
    private IInteractive interactive;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("referral")
    private IReferral referral;

    @JsonProperty("order")
    private IOrder order;

    @JsonProperty("system")
    private System system;

    @JsonProperty("video")
    private IVideo video;

    @JsonProperty("audio")
    private IAudio audio;

    @JsonProperty("document")
    private IDocument document;

    public LocalDateTime getTimestampAsDate() {
        return this.timestamp != null
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(timestamp)), ZoneOffset.UTC)
                : LocalDateTime.now();
    }
}
