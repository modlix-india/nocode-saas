package com.fincity.saas.message.model.message.whatsapp.messages.builder;

import com.fincity.saas.message.model.message.whatsapp.messages.Action;
import com.fincity.saas.message.model.message.whatsapp.messages.InteractiveMessage;
import com.fincity.saas.message.model.message.whatsapp.messages.type.InteractiveMessageType;

public interface IInteractiveMessageBuilder {

    interface IInteractiveAction {
        IInteractiveType setAction(Action action);
    }

    interface IInteractiveType {
        InteractiveMessage setType(InteractiveMessageType type);
    }
}
