package com.fincity.saas.commons.core.service.connection.email;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.model.ProcessedEmailDetails;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class SendGridService extends AbstractEmailService implements IAppEmailService {

    @Override
    public Mono<Boolean> sendMail(
            List<String> toAddresses, Template template, Map<String, Object> templateData, Connection connection) {
        if (connection.getConnectionDetails() == null
                || StringUtil.safeIsBlank(connection.getConnectionDetails().get("apiKey")))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    CoreMessageResourceService.MAIL_SEND_ERROR,
                    "SENDGRID api key is not found");

        String apiKey = connection.getConnectionDetails().get("apiKey").toString();

        return FlatMapUtil.flatMapMono(
                        () -> Mono.just(
                                Tuples.of(toAddresses == null ? List.of() : toAddresses, template, templateData)),
                        tup -> this.getProcessedEmailDetails(connection, toAddresses, template, templateData),
                        (tup, details) -> WebClient.create()
                                .post()
                                .uri("https://api.sendgrid.com/v3/mail/send")
                                .header("Authorization", "Bearer " + apiKey)
                                .header("Content-Type", "application/json")
                                .bodyValue(this.getSendGridBody(details))
                                .retrieve()
                                .bodyToMono(String.class)
                                .onErrorResume(WebClientResponseException.class, e -> {
                                    logger.error(
                                            "Error while sending it to send grid :" + " {}",
                                            e.getResponseBodyAsString(),
                                            e);

                                    return this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                            CoreMessageResourceService.MAIL_SEND_ERROR,
                                            "Error with body : " + e.getResponseBodyAsString(),
                                            e);
                                })
                                .map(e -> true))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SendGridService.sendMail"));
    }

    private Map<String, Object> getSendGridBody(ProcessedEmailDetails details) {
        return Map.of(
                "personalizations",
                List.of(Map.of(
                        "to",
                        details.getTo().stream().map(e -> Map.of("email", e)).toList())),
                "from",
                Map.of("email", details.getFrom()),
                "subject",
                details.getSubject(),
                "content",
                List.of(Map.of("type", "text/html", "value", details.getBody())));
    }
}
