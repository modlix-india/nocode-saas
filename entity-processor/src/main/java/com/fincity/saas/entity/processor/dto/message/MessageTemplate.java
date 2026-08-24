package com.fincity.saas.entity.processor.dto.message;

import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.message.MessageTemplateChannel;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * A reusable message, with several interchangeable phrasings.
 *
 * <p>What replaced Meta-approved templates when WhatsApp moved to the linked-device protocol. There
 * is no approval, no status and no component tree here, because none of those concepts exist any
 * more; what is left is the part that was always useful, which is a named message with variables in
 * it that somebody can pick off a list.
 *
 * <p>The variants are the point rather than a nicety. A stage rule sends one message to every
 * matching lead, and identical text to more than roughly fifteen recipients an hour is a documented
 * trigger for exactly the enforcement this whole design avoids.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
@IgnoreGeneration
public class MessageTemplate extends BaseUpdatableDto<MessageTemplate> {

    @Serial
    private static final long serialVersionUID = 4471553288845163928L;

    /**
     * Matches {@code {{name}}} placeholders.
     *
     * <p>Double braces rather than a single {@code $name} because a WhatsApp message is prose that
     * routinely contains currency, and a single-character sigil turns "costs $50" into a broken
     * substitution.
     */
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    private String name;
    private String description;

    private MessageTemplateChannel channel = MessageTemplateChannel.WHATSAPP;

    /** Interchangeable bodies. Never empty; a message with one variant is allowed but warned about. */
    private List<String> bodyVariants = new ArrayList<>();

    /** Variable names the bodies may reference, for the editor's insertion list and its warnings. */
    private List<String> variables = new ArrayList<>();

    private FileDetail assetFileDetail;
    private String caption;

    public MessageTemplate() {
        super();
    }

    public MessageTemplate(MessageTemplate other) {
        super(other);
        this.name = other.name;
        this.description = other.description;
        this.channel = other.channel;
        this.bodyVariants = other.bodyVariants == null ? new ArrayList<>() : new ArrayList<>(other.bodyVariants);
        this.variables = other.variables == null ? new ArrayList<>() : new ArrayList<>(other.variables);
        this.assetFileDetail = other.assetFileDetail;
        this.caption = other.caption;
    }

    /**
     * Picks the variant for a given recipient.
     *
     * <p>Rotated by a caller-supplied index rather than at random, so a packet of several messages
     * to one lead does not accidentally draw the same phrasing twice and, more importantly, so the
     * choice is reproducible when reading back what was sent.
     */
    public String variantFor(long rotation) {
        if (this.bodyVariants == null || this.bodyVariants.isEmpty()) return null;
        int index = (int) Math.floorMod(rotation, this.bodyVariants.size());
        return this.bodyVariants.get(index);
    }

    /** Whether this message will send identical text to every recipient. */
    public boolean isSingleVariant() {
        return this.bodyVariants == null || this.bodyVariants.size() <= 1;
    }

    /**
     * Variable names actually used across the bodies, as opposed to those declared.
     *
     * <p>The two drift constantly while somebody is editing, and a body referencing an undeclared
     * variable interpolates to nothing and sends a sentence with a hole in it.
     */
    public List<String> usedVariables() {
        List<String> found = new ArrayList<>();
        if (this.bodyVariants == null) return found;

        for (String body : this.bodyVariants) {
            if (body == null) continue;
            Matcher matcher = VARIABLE_PATTERN.matcher(body);
            while (matcher.find()) {
                String name = matcher.group(1).toLowerCase(Locale.ROOT);
                if (!found.contains(name)) found.add(name);
            }
        }
        return found;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.MESSAGE_TEMPLATE;
    }
}
