package com.fincity.saas.message.service.message;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.message.MessageRequest;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import reactor.core.publisher.Mono;

public interface IMessageService<D extends BaseUpdatableDto<D>> {

    ConnectionType getConnectionType();

    ConnectionSubType getConnectionSubType();

    String getProviderUri();

    Mono<Message> toMessage(D providerObject);

    Mono<Message> sendMessage(MessageAccess access, MessageRequest messageRequest, Connection connection);
}
