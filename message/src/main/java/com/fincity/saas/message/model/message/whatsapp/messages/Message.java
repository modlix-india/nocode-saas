package com.fincity.saas.message.model.message.whatsapp.messages;

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
public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = -9202268963191141253L;

    @JsonProperty("messaging_product")
    private final String messagingProduct = "whatsapp";

    @JsonProperty("recipient_type")
    private final String recipientType = "individual";

    @JsonProperty("context")
    private Context context;

    @JsonProperty("interactive")
    private InteractiveMessage interactiveMessage;

    @JsonProperty("to")
    private String to;

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("text")
    private TextMessage textMessage;

    @JsonProperty("contacts")
    private List<Contact> contactMessage;

    @JsonProperty("template")
    private TemplateMessage templateMessage;

    @JsonProperty("audio")
    private AudioMessage audioMessage;

    @JsonProperty("document")
    private DocumentMessage documentMessage;

    @JsonProperty("image")
    private ImageMessage imageMessage;

    @JsonProperty("sticker")
    private StickerMessage stickerMessage;

    @JsonProperty("video")
    private VideoMessage videoMessage;

    @JsonProperty("reaction")
    private ReactionMessage reactionMessage;

    @JsonProperty("location")
    private LocationMessage locationMessage;

    private Message(String to, MessageType type, Context context) {
        this.to = to;
        this.type = type;
        this.context = context;
    }

    public static class MessageBuilder {

        private String to;
        private Context context;

        private MessageBuilder() {}

        public static MessageBuilder builder() {
            return new MessageBuilder();
        }

        public MessageBuilder setTo(String to) {
            this.to = to;
            return this;
        }

        public MessageBuilder setContext(Context context) {
            this.context = context;
            return this;
        }

        public Message buildTextMessage(TextMessage textMessage) {
            Message message = new Message(to, MessageType.TEXT, context);
            message.textMessage = textMessage;
            return message;
        }

        public Message buildContactMessage(ContactMessage contactMessage) {
            Message message = new Message(to, MessageType.CONTACTS, context);
            message.contactMessage = contactMessage.getContacts();
            return message;
        }

        public Message buildTemplateMessage(TemplateMessage templateMessage) {
            Message message = new Message(to, MessageType.TEMPLATE, context);
            message.templateMessage = templateMessage;
            return message;
        }

        public Message buildInteractiveMessage(InteractiveMessage interactiveMessage) {
            Message message = new Message(to, MessageType.INTERACTIVE, context);
            message.interactiveMessage = interactiveMessage;
            return message;
        }

        public Message buildAudioMessage(AudioMessage audioMessage) {
            Message message = new Message(to, MessageType.AUDIO, context);
            message.audioMessage = audioMessage;
            return message;
        }

        public Message buildDocumentMessage(DocumentMessage documentMessage) {
            Message message = new Message(to, MessageType.DOCUMENT, context);
            message.documentMessage = documentMessage;
            return message;
        }

        public Message buildImageMessage(ImageMessage imageMessage) {
            Message message = new Message(to, MessageType.IMAGE, context);
            message.imageMessage = imageMessage;
            return message;
        }

        public Message buildStickerMessage(StickerMessage stickerMessage) {
            Message message = new Message(to, MessageType.STICKER, context);
            message.stickerMessage = stickerMessage;
            return message;
        }

        public Message buildVideoMessage(VideoMessage videoMessage) {
            Message message = new Message(to, MessageType.VIDEO, context);
            message.videoMessage = videoMessage;
            return message;
        }

        public Message buildReactionMessage(ReactionMessage reactionMessage) {
            Message message = new Message(to, MessageType.REACTION, context);
            message.reactionMessage = reactionMessage;
            return message;
        }

        public Message buildLocationMessage(LocationMessage locationMessage) {
            Message message = new Message(to, MessageType.LOCATION, context);
            message.locationMessage = locationMessage;
            return message;
        }
    }
}
