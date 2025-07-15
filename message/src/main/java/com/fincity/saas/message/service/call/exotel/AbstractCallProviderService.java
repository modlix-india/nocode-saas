package com.fincity.saas.message.service.call.exotel;

import com.fincity.saas.message.dao.base.BaseUpdatableDAO;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.service.MessageConnectionService;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import com.fincity.saas.message.service.call.IAppCallService;
import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractCallProviderService<
                R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>, O extends BaseUpdatableDAO<R, D>>
        extends BaseUpdatableService<R, D, O> implements IAppCallService {

    protected MessageConnectionService connectionService;

    @Autowired
    private void setConnectionService(MessageConnectionService connectionService) {
        this.connectionService = connectionService;
    }
}
