package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.MessageType;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 6162760594885532126L;

    @JsonProperty("reaction")
    private Reaction reaction;

    @JsonProperty("image")
    private Image image;

    @JsonProperty("sticker")
    private Sticker sticker;

    @JsonProperty("location")
    private Location location;

    @JsonProperty("contacts")
    private List<Contact> contacts;

    @JsonProperty("button")
    private Button button;

    @JsonProperty("context")
    private Context context;

    @JsonProperty("from")
    private String from;

    @JsonProperty("id")
    private String id;

    @JsonProperty("text")
    private Text text;

    @JsonProperty("errors")
    private List<Error> errors;

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("interactive")
    private Interactive interactive;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("referral")
    private Referral referral;

    @JsonProperty("order")
    private Order order;

    @JsonProperty("system")
    private System system;

    @JsonProperty("video")
    private Video video;

    @JsonProperty("audio")
    private Audio audio;

    @JsonProperty("document")
    private Document document;
}
