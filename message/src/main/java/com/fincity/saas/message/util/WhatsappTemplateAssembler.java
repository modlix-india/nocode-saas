package com.fincity.saas.message.util;

import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappTemplate;
import com.fincity.saas.message.model.message.whatsapp.messages.BodyComponent;
import com.fincity.saas.message.model.message.whatsapp.messages.Language;
import com.fincity.saas.message.model.message.whatsapp.messages.TemplateMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.TextParameter;
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

        TemplateMessage templateMessage =
                new TemplateMessage().setName(template.getTemplateName()).setLanguage(toLanguage(template));

        String bodyText = findBodyText(template);
        if (bodyText == null) return templateMessage;

        List<String> tokens = extractTokens(bodyText);
        if (tokens.isEmpty()) return templateMessage;

        BodyComponent body = new BodyComponent();
        for (String token : tokens) body.addParameter(new TextParameter(resolve(variables, token)));

        return templateMessage.addComponent(body);
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
