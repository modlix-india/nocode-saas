package com.fincity.saas.notification.service.template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.TemplateDao;
import com.fincity.saas.notification.dto.Template;
import com.fincity.saas.notification.jooq.tables.records.NotificationTemplateRecord;

import reactor.core.publisher.Mono;

@Service
public class TemplateService extends AbstractJOOQUpdatableDataService<NotificationTemplateRecord, ULong, Template, TemplateDao> {

	private static final String CACHE_NAME_TEMPLATE = "notificationTemplate";
	private final CacheService cacheService;

	public TemplateService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	@Override
	protected Mono<Template> updatableEntity(Template entity) {
		return super.read(entity.getId())
				.map(e -> {
					e.setName(entity.getName());
					e.setDescription(entity.getDescription());
					e.setTemplateParts(entity.getTemplateParts());
					e.setResources(entity.getResources());
					e.setVariables(entity.getVariables());
					e.setTemplateType(entity.getTemplateType());
					e.setDefaultLanguage(entity.getDefaultLanguage());
					e.setLanguageExpression(entity.getLanguageExpression());
					e.setUpdatedAt(entity.getUpdatedAt());
					e.setUpdatedBy(entity.getUpdatedBy());

					return e;
				});
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<>());

		fields.keySet().retainAll(List.of("name", "description", "templateParts", "resources", "variables",
				"templateType", "defaultLanguage", "languageExpression", "updatedAt", "updatedBy"));

		return Mono.just(fields);
	}

	public Mono<Template> getTemplate(ULong templateId) {
		return this.cacheService.cacheValueOrGet(this.getCacheName(),
				() -> super.read(templateId), templateId);
	}

	public Mono<Template> getTemplate(ULong clientId, ULong appId, String templateCode) {
		return this.cacheService.cacheValueOrGet(this.getCacheName(),
				() -> this.dao.getTemplate(clientId, appId, templateCode),
				this.getCacheKeys(clientId, appId, templateCode));
	}

	private String getCacheName() {
		return CACHE_NAME_TEMPLATE;
	}

	private String getCacheKeys(ULong clientId, ULong appId, String templateCode) {
		return clientId + ":" + appId + ":" + templateCode;
	}

}
