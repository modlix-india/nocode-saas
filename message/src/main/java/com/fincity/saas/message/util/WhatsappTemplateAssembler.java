package com.fincity.saas.message.util;

import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.model.message.whatsapp.messages.BodyComponent;
import com.fincity.saas.message.model.message.whatsapp.messages.Document;
import com.fincity.saas.message.model.message.whatsapp.messages.DocumentParameter;
import com.fincity.saas.message.model.message.whatsapp.messages.HeaderComponent;
import com.fincity.saas.message.model.message.whatsapp.messages.Image;
import com.fincity.saas.message.model.message.whatsapp.messages.ImageParameter;
import com.fincity.saas.message.model.message.whatsapp.messages.Language;
import com.fincity.saas.message.model.message.whatsapp.messages.TemplateMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.TextParameter;
import com.fincity.saas.message.model.message.whatsapp.messages.Video;
import com.fincity.saas.message.model.message.whatsapp.messages.VideoParameter;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import com.fincity.saas.message.model.message.whatsapp.templates.Component;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;
import com.fincity.saas.message.model.message.whatsapp.templates.type.LanguageType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a sendable {@link TemplateMessage} from a stored {@link WhatsappTemplate} plus a map of
 * placeholder values.
 *
 * <p>Meta requires body parameters to be supplied positionally, in the order the placeholders
 * appear in the approved body text. This walks the stored body text, pulls out {@code {{...}}}
 * tokens in order, and emits one text parameter per token. A token with no matching entry in
 * {@code variables} becomes an empty string rather than failing the send, because Meta rejects the
 * whole message if the parameter count does not match the approved template.
 */
public final class WhatsappTemplateAssembler {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}\\s]+)\\s*}}");

    private WhatsappTemplateAssembler() {}

    public static TemplateMessage assemble(WhatsappTemplate template, Map<String, Object> variables) {
        return assemble(template, variables, null, null);
    }

    /**
     * Same, plus a media header for a welcome-packet asset.
     *
     * <p>An approved media template declares only its header <em>format</em>; the media itself is a
     * runtime parameter, which is what lets one approved template deliver a different brochure per
     * product. The link is passed straight through for Meta to fetch, so it must be reachable
     * without a session and must outlive the send plus Meta's retry window.
     *
     * <p>A null or blank url produces exactly the text-only message the two-argument overload does,
     * so a config with no asset is unaffected.
     */
    public static TemplateMessage assemble(
            WhatsappTemplate template, Map<String, Object> variables, String headerMediaUrl, String headerMediaType) {

        TemplateMessage templateMessage =
                new TemplateMessage().setName(template.getTemplateName()).setLanguage(toLanguage(template));

        if (headerMediaUrl != null && !headerMediaUrl.isBlank())
            templateMessage.addComponent(mediaHeader(headerMediaUrl, headerMediaType));

        String bodyText = findBodyText(template);
        if (bodyText == null) return templateMessage;

        List<String> tokens = extractTokens(bodyText);
        if (tokens.isEmpty()) return templateMessage;

        BodyComponent body = new BodyComponent();
        for (String token : tokens) body.addParameter(new TextParameter(resolve(variables, token)));

        return templateMessage.addComponent(body);
    }

    /**
     * Falls back to a document header for anything unrecognised, because Graph rejects an unknown
     * header type outright and a brochure arriving as a document beats a packet that stops dead at
     * the first unfamiliar MIME type.
     */
    private static HeaderComponent mediaHeader(String url, String mediaType) {

        HeaderComponent header = new HeaderComponent();
        String type = mediaType == null ? "" : mediaType.toLowerCase();

        // A statement switch, not an expression one. addParameter is declared on the base class and
        // returns Component<HeaderComponent> rather than HeaderComponent, so as an expression the
        // switch cannot type against this method's return. It mutates and returns this, so
        // discarding the result and returning the header is equivalent.
        switch (type) {
            case "image" -> header.addParameter(new ImageParameter(new Image().setLink(url)));
            case "video" -> header.addParameter(new VideoParameter(ParameterType.VIDEO, new Video().setLink(url)));
            default -> header.addParameter(new DocumentParameter(new Document().setLink(url)));
        }

        return header;
    }

    private static String findBodyText(WhatsappTemplate template) {

        if (template.getComponents() == null) return null;

        for (Component<?> component : template.getComponents())
            if (ComponentType.BODY.equals(component.getType())) return component.getText();

        return null;
    }

    private static List<String> extractTokens(String bodyText) {

        List<String> tokens = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(bodyText);

        while (matcher.find()) tokens.add(matcher.group(1));

        return tokens;
    }

    private static String resolve(Map<String, Object> variables, String token) {

        if (variables == null) return "";

        Object value = variables.get(token);

        return value == null ? "" : value.toString();
    }

    private static Language toLanguage(WhatsappTemplate template) {

        Language language = new Language();

        if (template.getLanguage() == null) return language;

        for (LanguageType type : LanguageType.values())
            if (type.getValue().equalsIgnoreCase(template.getLanguage())) return language.setCode(type);

        return language;
    }
}
