package com.fincity.saas.entity.processor.service.message;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.feign.IFeignMessageService;
import com.fincity.saas.entity.processor.model.common.Identity;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.IProcessorAccessService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Read side of the WhatsApp sending configuration: which templates may be sent, and which business
 * numbers they may be sent from.
 *
 * <p>This exists so the UI has one service to talk to. The message service owns these rows and
 * still serves them on its own routes, but those routes are about to be denied at the edge along
 * with the rest of {@code /api/message/**}. Reading them through here keeps the deal profile
 * working after that, and leaves the message service reachable only by services.
 *
 * <p>Not gated on {@code ROLE_Owner}, deliberately, even though every write against these rows is.
 * A salesperson opening a deal has to see the approved templates and the numbers to send one at
 * all, and that is deal work rather than settings administration. Gating the read on the
 * administrative authority would leave the composer empty for exactly the people who use it. What
 * is gated is that the caller is a logged-in user of the tenant, which is the same first step the
 * conversation endpoints take; the difference there is that a conversation also belongs to a deal
 * and so carries a second, deal-level check. These rows belong to the tenant, not to a deal, so
 * there is nothing further to check and inventing something would only be theatre.
 *
 * <p>Nothing is proxied on the write side. Creating a template, syncing numbers from Meta and
 * choosing a default all stay in the message service behind {@code ROLE_Owner}.
 */
@Service
public class WhatsappSendOptionsService implements IProcessorAccessService {

    private final IFeignMessageService feignMessageService;
    private final ProcessorMessageResourceService msgService;
    private final IFeignSecurityService securityService;

    public WhatsappSendOptionsService(
            IFeignMessageService feignMessageService,
            ProcessorMessageResourceService msgService,
            IFeignSecurityService securityService) {
        this.feignMessageService = feignMessageService;
        this.msgService = msgService;
        this.securityService = securityService;
    }

    @Override
    public ProcessorMessageResourceService getMsgService() {
        return this.msgService;
    }

    @Override
    public IFeignSecurityService getSecurityService() {
        return this.securityService;
    }

    /**
     * The tenant's templates, optionally narrowed to a set of Meta statuses.
     *
     * <p>The status list is the whole point of the parameter: the composer offers {@code APPROVED}
     * and {@code PENDING} and nothing else, because a rejected or paused template will be refused
     * by Meta at send time and showing it only produces a failure the agent cannot act on.
     *
     * @param statuses empty or null for every status
     */
    public Mono<Map<String, Object>> readTemplates(List<String> statuses, String templateName, Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService.getWhatsappTemplates(
                                access.getAppCode(),
                                access.getClientCode(),
                                joinStatuses(statuses),
                                templateName == null ? "" : templateName,
                                pageable.getPageNumber(),
                                pageable.getPageSize()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readTemplates"));
    }

    /**
     * One template, for the preview the agent sees before sending.
     *
     * <p>The tenant travels with the call rather than the id being trusted on its own, so a
     * template id guessed from another tenant reads as not found instead of leaking a competitor's
     * message copy.
     */
    public Mono<Map<String, Object>> readTemplate(Identity templateId) {

        if (templateId == null || templateId.isNull())
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    ProcessorMessageResourceService.IDENTITY_MISSING,
                    "WhatsApp template");

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService.getWhatsappTemplate(
                                access.getAppCode(), access.getClientCode(), templateId.toString()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readTemplate"));
    }

    /** The tenant's business numbers, which is what the composer's "from" list is built out of. */
    public Mono<Map<String, Object>> readPhoneNumbers(Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService.getWhatsappPhoneNumbers(
                                access.getAppCode(),
                                access.getClientCode(),
                                pageable.getPageNumber(),
                                pageable.getPageSize()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readPhoneNumbers"));
    }

    /**
     * The number the composer preselects, when the tenant has named one.
     *
     * <p>Answers with an empty object rather than an empty body when there is no default. A tenant
     * with one number, or with a number pinned per product, never needs a default, so this is an
     * ordinary state and not a 404. The empty object matters on the wire: an empty body arrives at
     * the UI as an empty string, and a binding expecting an object then indexes into it and fails
     * on every field.
     */
    public Mono<Map<String, Object>> readDefaultPhoneNumber() {

        return FlatMapUtil.flatMapMono(
                        this::hasAccess,
                        access -> this.feignMessageService
                                .getDefaultWhatsappPhoneNumber(access.getAppCode(), access.getClientCode())
                                .defaultIfEmpty(Map.of()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "WhatsappSendOptionsService.readDefaultPhoneNumber"));
    }

    /**
     * Packs the repeated {@code status} parameters into the one comma-separated value the message
     * service reads an {@code IN} out of. Status names are enum constants and cannot contain a
     * comma, so nothing needs escaping here.
     */
    private static String joinStatuses(List<String> statuses) {

        if (statuses == null || statuses.isEmpty()) return "";

        return String.join(
                ",", statuses.stream().filter(s -> s != null && !s.isBlank()).toList());
    }
}
