package com.fincity.saas.message.enums.call;

public interface ICallStatus {

    default CallStatus toCallStatus() {
        return CallStatus.UNKNOWN;
    }
}
