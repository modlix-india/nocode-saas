package com.fincity.saas.commons.core.service.connection.email;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.model.ProcessedEmailDetails;
import com.fincity.saas.commons.core.service.AbstractTemplateService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractEmailService extends AbstractTemplateService {

    protected Mono<ProcessedEmailDetails> getProcessedEmailDetails(
            List<String> toAddresses, Template template, Map<String, Object> templateData) {
        return FlatMapUtil.flatMapMono(
                        () -> this.getToAddresses(toAddresses, template, templateData),
                        to -> this.getFromAddress(template, templateData),
                        (to, from) -> this.getLanguage(template, templateData),
                        (to, from, language) -> this.getProcessedTemplate(language, template, templateData),
                        (to, from, language, temp) -> Mono.just(new ProcessedEmailDetails()
                                .setTo(to)
                                .setFrom(from)
                                .setSubject(temp.getOrDefault("subject", ""))
                                .setBody(temp.getOrDefault("body", ""))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractEmailService.getProcessedEmailDetails"));
    }

    protected Mono<String> getFromAddress(Template template, Map<String, Object> templateData) {
        boolean isBlankExpression = StringUtil.safeIsBlank(template.getFromExpression());

        if (isBlankExpression) return Mono.just("");

        return processFreeMarker("fromExpression", template.getFromExpression(), templateData);
    }

    protected Mono<List<String>> getToAddresses(
            List<String> toAddresses, Template template, Map<String, Object> templateData) {
        List<String> addresses = new ArrayList<>();

        boolean isBlankExpression = StringUtil.safeIsBlank(template.getToExpression());

        if (toAddresses != null && !toAddresses.isEmpty()) {
            if (isBlankExpression) return Mono.just(toAddresses);
            addresses.addAll(toAddresses);
        }

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> Mono.just(addresses),
                        addrList -> {
                            if (isBlankExpression) return Mono.just(addrList);

                            return processFreeMarker("toExpression", template.getToExpression(), templateData)
                                    .map(e -> {
                                        String[] addrs = e.split(";");

                                        for (String addr : addrs) {
                                            if (StringUtil.safeIsBlank(addr)) continue;
                                            addrList.add(addr);
                                        }
                                        return addrList;
                                    });
                        },
                        (addrList, finList) -> {
                            if (finList.isEmpty()) {
                                return this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                        CoreMessageResourceService.MAIL_SEND_ERROR,
                                        "No Send Addresses Found.");
                            }

                            return Mono.just(finList);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractEmailService.getToAddress"));
    }
}
