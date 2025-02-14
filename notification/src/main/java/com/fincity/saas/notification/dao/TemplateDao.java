package com.fincity.saas.notification.dao;

import static com.fincity.saas.notification.jooq.Tables.NOTIFICATION_TEMPLATE;

import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.notification.dto.Template;
import com.fincity.saas.notification.jooq.tables.records.NotificationTemplateRecord;

import reactor.core.publisher.Mono;

public class TemplateDao extends AbstractCodeDao<NotificationTemplateRecord, ULong, Template> {

	protected TemplateDao() {
		super(Template.class, NOTIFICATION_TEMPLATE, NOTIFICATION_TEMPLATE.ID, NOTIFICATION_TEMPLATE.CODE);
	}

	public Mono<Template> getTemplate(ULong clientId, ULong appId, String templateCode) {
		return Mono.from(this.dslContext.selectFrom(NOTIFICATION_TEMPLATE)
						.where(NOTIFICATION_TEMPLATE.CLIENT_ID.eq(clientId))
						.and(NOTIFICATION_TEMPLATE.APP_ID.eq(appId))
						.and(NOTIFICATION_TEMPLATE.CODE.eq(templateCode)))
				.map(result -> result.into(Template.class));
	}

}
