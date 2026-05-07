package com.fincity.saas.entity.processor.model.response;

import java.time.LocalDateTime;

public record DenormSyncResult(int partnersUpdated, LocalDateTime nextSince) {
}
