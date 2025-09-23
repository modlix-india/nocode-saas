package com.fincity.saas.commons.core.service.connection.ai;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;
import java.util.Map;


@Service
public class OpenAIService implements IAIService {

    private final CoreMessageResourceService msgService;

    public OpenAIService(CoreMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Override
    public Mono<String> chat(Connection connection, String prompt) {

        if (connection == null || connection.getConnectionDetails() == null
                || connection.getConnectionDetails().isEmpty()
                || StringUtil.safeIsBlank(connection.getConnectionDetails().get("apiKey")))
            return this.msgService.throwMessage(
                    msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                    CoreMessageResourceService.UNABLE_AI_CONNECTION, connection == null ? "Unknown Connection" : connection.getName());
        
        return FlatMapUtil.flatMapMono(

                () -> {

                    OpenAIClientAsync client = OpenAIOkHttpClientAsync.builder()
                            .apiKey(connection.getConnectionDetails().get("apiKey").toString())
                            .build();

                    ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                            .addUserMessage(prompt)
                            .model(ChatModel.GPT_4O_MINI)
                            .build();

                    return Mono.fromFuture(client.chat().completions().create(params));
                },

                (chatCompletion) -> {
                    if (chatCompletion.choices().isEmpty()
                            || chatCompletion.choices().getFirst().message().content().isEmpty()
                    )
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                                CoreMessageResourceService.EMPTY_AI_RESPONSE);

                    return Mono.just(chatCompletion.choices().getFirst().message().content().get());
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "OpenAIService.chat"));
    }


}
