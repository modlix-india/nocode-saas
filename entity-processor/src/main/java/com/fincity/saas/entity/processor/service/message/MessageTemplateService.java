package com.fincity.saas.entity.processor.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.message.MessageTemplateDAO;
import com.fincity.saas.entity.processor.dto.message.MessageTemplate;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.message.MessageTemplateChannel;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorMessageTemplatesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The reusable message library.
 *
 * <p>What replaced Meta-approved templates. The word "template" survives in the table name and the
 * class name because the column it is referenced by does, but nothing here is submitted anywhere or
 * waits for approval: a message is created and is immediately usable. That difference is the whole
 * point of the pivot, and it is worth stating in the one place someone will read before assuming the
 * old workflow still applies.
 *
 * <p>What carried over from templates is the part that was always useful: a named message with
 * variables in it, and now several interchangeable phrasings of it, because a rule that sends one
 * body to every matching lead is the pattern most likely to get a number blocked.
 */
@Service
public class MessageTemplateService
        extends BaseUpdatableService<EntityProcessorMessageTemplatesRecord, MessageTemplate, MessageTemplateDAO> {

    /**
     * Variables the substitution can fill, and therefore what the editor offers.
     *
     * <p>Deliberately a short, closed list. Every name here resolves from the deal itself with no
     * extra query, so a body cannot be authored against a field that turns out to cost a join per
     * recipient at send time.
     */
    public static final List<String> SUPPORTED_VARIABLES =
            List.of("name", "firstname", "email", "phonenumber", "ticketcode", "productname", "username");

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.MESSAGE_TEMPLATE;
    }

    /**
     * Rejects a message that cannot send, and only that.
     *
     * <p>Single-variant messages pass. They are a risk rather than an error, the editor warns about
     * them, and refusing to save one would stop somebody writing a genuinely one-off announcement.
     * An unknown variable also passes: it interpolates to an empty string, which is a sentence with
     * a hole in it rather than a failed send, and blocking the save would make the library unusable
     * the moment a new field is wanted.
     */
    @Override
    protected Mono<MessageTemplate> checkEntity(MessageTemplate entity, ProcessorAccess access) {

        if (entity.getName() == null || entity.getName().isBlank())
            return this.throwMissingParam(MessageTemplate.Fields.name);

        List<String> variants = entity.getBodyVariants() == null
                ? List.of()
                : entity.getBodyVariants().stream()
                        .filter(body -> body != null && !body.isBlank())
                        .toList();

        if (variants.isEmpty()) return this.throwMissingParam(MessageTemplate.Fields.bodyVariants);

        entity.setBodyVariants(new ArrayList<>(variants));
        if (entity.getChannel() == null) entity.setChannel(MessageTemplateChannel.WHATSAPP);

        // Declared variables are derived from the bodies rather than trusted from the payload. The
        // two drift the moment somebody edits a body without touching the list, and the derived
        // set is the one that actually matters at send time.
        entity.setVariables(new ArrayList<>(entity.usedVariables()));

        return this.dao
                .nameExists(access.getAppCode(), access.getClientCode(), entity.getName(), entity.getId())
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? this.msgService.<MessageTemplate>throwMessage(
                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                entity.getName())
                        : Mono.just(entity))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageTemplateService.checkEntity"));
    }

    /** The library for a channel, for the picker in a rule popup and for the library screen. */
    public Mono<List<MessageTemplate>> readLibrary(MessageTemplateChannel channel) {
        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.dao.readByChannel(access.getAppCode(), access.getClientCode(), channel))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "MessageTemplateService.readLibrary"));
    }

    /**
     * Fills a body's placeholders.
     *
     * <p>Ours end to end, which is the quiet win of dropping templates. Meta's default templates use
     * positional {@code {{1}}} tokens that have to be reconciled against a per-config ordering, and
     * getting that mapping wrong renders a blank where a name should be. Here the body is a string
     * we own and the variables are named, so there is nothing to reconcile.
     *
     * <p>An unknown or null variable interpolates to an empty string rather than leaving the raw
     * {@code {{token}}} in place. Sending a customer a message containing literal braces is worse
     * than sending one with a gap in it.
     */
    public static String interpolate(String body, Map<String, Object> variables) {

        if (body == null || body.isBlank()) return body;

        return MessageTemplate.VARIABLE_PATTERN
                .matcher(body)
                .replaceAll(match -> {
                    Object value = variables == null
                            ? null
                            : variables.get(match.group(1).toLowerCase(Locale.ROOT));
                    // Quoted, because a lead named with a dollar sign or a backslash would
                    // otherwise be read as a replacement reference and throw mid-send.
                    return java.util.regex.Matcher.quoteReplacement(value == null ? "" : value.toString());
                });
    }

    /**
     * The values a body may reference, drawn from the deal.
     *
     * <p>Lowercase keys so {@code {{Name}}} and {@code {{name}}} behave the same. Somebody writing
     * prose will capitalise a sentence-leading placeholder without thinking about it, and having
     * that silently produce an empty string would be a genuinely baffling bug to report.
     */
    public static Map<String, Object> variablesFor(
            String ticketName, String email, String phoneNumber, String ticketCode, String productName, String userName) {

        String firstName = ticketName == null || ticketName.isBlank() ? null : ticketName.trim().split("\\s+")[0];

        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("name", ticketName);
        variables.put("firstname", firstName);
        variables.put("email", email);
        variables.put("phonenumber", phoneNumber);
        variables.put("ticketcode", ticketCode);
        variables.put("productname", productName);
        variables.put("username", userName);
        return variables;
    }

    /** One message by id, scoped to the caller's tenant by the base read. */
    public Mono<MessageTemplate> readForSend(ULong id) {
        return this.dao.readById(id);
    }
}
