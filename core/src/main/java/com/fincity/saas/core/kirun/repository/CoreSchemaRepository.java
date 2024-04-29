package com.fincity.saas.core.kirun.repository;

import static java.util.Map.entry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CoreSchemaRepository extends ReactiveHybridRepository<Schema> {

	public static final String SCHEMA_NAMESPACE_SECURITY_CONTEXT = "Security.Context";

	public static final String SCHEMA_NAME_CONTEXT_USER = "ContextUser";

	public static final String SCHEMA_NAME_CONTEXT_AUTHENTICATION = "ContextAuthentication";

	private final Map<String, Schema> repoMap = new HashMap<>();

	private final List<String> filterableNames;

	public CoreSchemaRepository() {

		repoMap.put(SCHEMA_NAMESPACE_SECURITY_CONTEXT + "." + SCHEMA_NAME_CONTEXT_USER,
				new Schema()
						.setNamespace(SCHEMA_NAMESPACE_SECURITY_CONTEXT)
						.setName(SCHEMA_NAME_CONTEXT_USER)
						.setType(Type.of(SchemaType.OBJECT))
						.setProperties(Map.ofEntries(
								entry("id", Schema.ofLong("id")),
								entry("createdBy", Schema.ofLong("createdBy")),
								entry("updatedBy", Schema.ofLong("updatedBy")),
								entry("createdAt", Schema.ofString("createdAt").setFormat(StringFormat.DATETIME)),
								entry("updatedAt", Schema.ofString("updatedAt").setFormat(StringFormat.DATETIME)),
								entry("clientId", Schema.ofLong("clientId")),
								entry("userName", Schema.ofString("userName")),
								entry("emailId", Schema.ofString("emailId").setFormat(StringFormat.EMAIL)),
								entry("phoneNumber", Schema.ofString("phoneNumber")),
								entry("firstName", Schema.ofString("firstName")),
								entry("lastName", Schema.ofString("lastName")),
								entry("middleName", Schema.ofString("middleName")),
								entry("localeCode", Schema.ofString("localeCode")),
								entry("accountNonExpired", Schema.ofBoolean("accountNonExpired")),
								entry("accountNonLocked", Schema.ofBoolean("accountNonLocked")),
								entry("credentialsNonExpired", Schema.ofBoolean("credentialsNonExpired")),
								entry("noFailedAttempt", Schema.ofInteger("noFailedAttempt")),
								entry("statusCode", Schema.ofString("statusCode")),
								entry("stringAuthorities",
										Schema.ofArray("stringAuthorities", Schema.ofString("authority"))))));

		repoMap.put(SCHEMA_NAMESPACE_SECURITY_CONTEXT + "." + SCHEMA_NAME_CONTEXT_AUTHENTICATION,
				new Schema()
						.setNamespace(SCHEMA_NAMESPACE_SECURITY_CONTEXT)
						.setName(SCHEMA_NAME_CONTEXT_AUTHENTICATION)
						.setType(Type.of(SchemaType.OBJECT))
						.setProperties(Map.ofEntries(
								entry("user",
										Schema.ofRef(
												SCHEMA_NAMESPACE_SECURITY_CONTEXT + "." + SCHEMA_NAME_CONTEXT_USER)),
								entry("isAuthenticated", Schema.ofBoolean("isAuthenticated")),
								entry("loggedInFromClientId", Schema.ofLong("loggedInFromClientId")),
								entry("loggedInFromClientCode", Schema.ofString("loggedInFromClientCode")),
								entry("clientTypeCode", Schema.ofString("clientTypeCode")),
								entry("clientCode", Schema.ofString("clientCode")),
								entry("urlClientCode", Schema.ofString("urlClientCode")),
								entry("urlAppCode", Schema.ofString("urlAppCode")))));

		this.filterableNames = repoMap.values().stream().map(Schema::getFullName).toList();
	}

	@Override
	public Flux<String> filter(String name) {
		final String filterName = name == null ? "" : name;
		return Flux.fromStream(filterableNames.stream())
				.filter(e -> e.toLowerCase().contains(filterName.toLowerCase()));
	}

	@Override
	public Mono<Schema> find(String namespace, String name) {
		return Mono.justOrEmpty(repoMap.get(namespace + "." + name));
	}
}
