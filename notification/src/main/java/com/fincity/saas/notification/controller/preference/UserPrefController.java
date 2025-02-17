package com.fincity.saas.notification.controller.preference;

import java.io.Serializable;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.notification.controller.AbstractCodeController;
import com.fincity.saas.notification.dao.preferences.UserPrefDao;
import com.fincity.saas.notification.dto.preference.UserPref;
import com.fincity.saas.notification.service.preferences.UserPrefService;

public class UserPrefController<R extends UpdatableRecord<R>, T extends Serializable, D extends UserPref<T, D>,
		O extends UserPrefDao<R, T, D>, S extends UserPrefService<R, T, D, O>>
		extends AbstractCodeController<R, ULong, D, O, S> {

}
