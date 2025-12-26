package com.fincity.saas.commons.functions;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.domain.Pageable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ClassSchema {

	public static final String PAGEABLE_SCHEMA_REF = "Commons.Pageable";
	public static final String QUERY_SCHEMA_REF = "Commons.Query";
	public static final String CONDITION_SCHEMA_REF = "Commons.AbstractCondition";
	private static final Map<String, ClassSchema> INSTANCES = new ConcurrentHashMap<>();
	private static final Map<Class<?>, Schema> PRIMITIVE_SCHEMA_CACHE = new ConcurrentHashMap<>();

	static {
		registerPrimitive(String.class, Schema.ofString("String"));
		registerPrimitive(Integer.class, Schema.ofInteger("Integer"));
		registerPrimitive(int.class, Schema.ofInteger("int"));
		registerPrimitive(Long.class, Schema.ofLong("Long"));
		registerPrimitive(long.class, Schema.ofLong("long"));
		registerPrimitive(Double.class, Schema.ofDouble("Double"));
		registerPrimitive(double.class, Schema.ofDouble("double"));
		registerPrimitive(Float.class, Schema.ofFloat("Float"));
		registerPrimitive(float.class, Schema.ofFloat("float"));
		registerPrimitive(Boolean.class, Schema.ofBoolean("Boolean"));
		registerPrimitive(boolean.class, Schema.ofBoolean("boolean"));
		registerPrimitive(java.time.LocalDateTime.class, Schema.ofRef("System.Date.Timestamp"));
		registerPrimitive(java.time.LocalDate.class, Schema.ofRef("System.Date.Timestamp"));
		registerPrimitive(JsonObject.class, Schema.ofObject("JsonObject"));
		registerPrimitive(BigInteger.class, Schema.ofInteger("BigInteger"));

		Schema abstractConditionSchema = new Schema()
				.setName("AbstractCondition")
				.setNamespace("Commons")
				.setAnyOf(List.of(
						Schema.ofRef("Commons.FilterCondition"),
						Schema.ofRef("Commons.ComplexCondition")));

		registerPrimitive(AbstractCondition.class, abstractConditionSchema);
	}

	private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
	private final Map<Class<?>, String> namespaceCache = new ConcurrentHashMap<>();


	@Getter
	private final PackageConfig packageConfig;

	private ClassSchema(PackageConfig packageConfig) {
		this.packageConfig = packageConfig;
	}

	private static void registerPrimitive(Class<?> clazz, Schema schema) {
		PRIMITIVE_SCHEMA_CACHE.put(clazz, schema);
	}

	public static ClassSchema getInstance() {
		return getInstance(PackageConfig.forCommons());
	}

	public static ClassSchema getInstance(PackageConfig config) {
		PackageConfig effectiveConfig = config != null ? config : PackageConfig.forCommons();
		String key = effectiveConfig.getServiceName();
		if (key == null) key = "DEFAULT";
		return INSTANCES.computeIfAbsent(key, k -> new ClassSchema(effectiveConfig));
	}

	public void clearCache() {
		schemaCache.clear();
		namespaceCache.clear();
	}

	public boolean isPrimitiveType(Class<?> type) {
		if (type == null) return false;
		return PRIMITIVE_SCHEMA_CACHE.containsKey(type) || type.isPrimitive();
	}

	public boolean shouldIncludeClass(Class<?> clazz) {
		if (clazz == null) return false;
		return !clazz.isAnnotationPresent(IgnoreGeneration.class);
	}

	public boolean isRelevantPackage(Class<?> type) {
		if (type == null || type.getPackage() == null) return false;

		String packageName = type.getPackage().getName();

		if (packageName.startsWith(packageConfig.getCommonsPackagePrefix())) {
			return true;
		}

		for (PackageMapping mapping : packageConfig.getPackageMappings()) {
			if (packageName.startsWith(mapping.getPackagePrefix())) {
				return true;
			}
		}
		return false;
	}

	public void collectNestedTypes(Class<?> type, Set<Class<?>> pojoClasses) {
		if (type == null) return;

		Class<?> currentClass = type;
		while (currentClass != null && currentClass != Object.class) {
			for (Field field : currentClass.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
					continue;
				}

				collectType(field.getType(), pojoClasses);

				Type genericType = field.getGenericType();
				if (genericType instanceof ParameterizedType paramType) {
					for (Type argType : paramType.getActualTypeArguments()) {
						if (argType instanceof Class<?> clazz) {
							collectType(clazz, pojoClasses);
						} else if (argType instanceof ParameterizedType nestedParamType) {
							Type rawType = nestedParamType.getRawType();
							if (rawType instanceof Class<?> rawClass) {
								collectType(rawClass, pojoClasses);
							}
							for (Type nestedArgType : nestedParamType.getActualTypeArguments()) {
								if (nestedArgType instanceof Class<?> nestedClass) {
									collectType(nestedClass, pojoClasses);
								}
							}
						}
					}
				}
			}
			currentClass = currentClass.getSuperclass();
		}
	}

	private void collectType(Class<?> type, Set<Class<?>> pojoClasses) {
		if (type == null) return;
		if (type.isPrimitive() || type.isArray()) return;
		if (type.getPackage() == null) return;

		if (isRelevantPackage(type) && !pojoClasses.contains(type) && shouldIncludeClass(type)) {
			pojoClasses.add(type);
			collectNestedTypes(type, pojoClasses);
		}
	}

	public Schema generateSchemaForClass(Class<?> clazz) {
		if (clazz == null) return null;
		String namespace = getNamespaceForClass(clazz);
		String name = clazz.getSimpleName();
		return generateSchemaForClass(clazz, new HashSet<>(), namespace, name);
	}

	public Schema generateSchemaForClass(Class<?> clazz, Set<Class<?>> visited, String namespace, String name) {
		if (visited.contains(clazz)) {
			return Schema.ofRef(namespace + "." + name);
		}

		String cacheKey = clazz.getName();
		if (schemaCache.containsKey(cacheKey)) return schemaCache.get(cacheKey);
		if (PRIMITIVE_SCHEMA_CACHE.containsKey(clazz)) return PRIMITIVE_SCHEMA_CACHE.get(clazz);

		if (clazz.isEnum()) {
			Schema enumSchema = buildEnumSchema(clazz, namespace, name);
			schemaCache.put(cacheKey, enumSchema);
			return enumSchema;
		}

		visited.add(clazz);
		try {
			Schema schema = buildObjectSchema(clazz, visited, namespace, name);
			schemaCache.put(cacheKey, schema);
			return schema;
		} finally {
			visited.remove(clazz);
		}
	}

	private Schema buildObjectSchema(Class<?> clazz, Set<Class<?>> visited, String namespace, String name) {
		Map<String, Schema> properties = new LinkedHashMap<>();
		Map<String, Type> typeVariableMap = buildTypeVariableMap(clazz);

		Class<?> currentClass = clazz;
		while (currentClass != null && currentClass != Object.class) {
			for (Field field : currentClass.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
					continue;
				}

				String fieldName = field.getName();
				Type fieldGenericType = field.getGenericType();
				Type resolvedType = resolveType(fieldGenericType, typeVariableMap);
				Class<?> fieldType = getRawType(resolvedType);

				Schema fieldSchema = getSchemaForType(fieldType, resolvedType, visited);
				properties.put(fieldName, fieldSchema);
			}
			currentClass = currentClass.getSuperclass();
		}

		Schema schema = Schema.ofObject(name);
		schema.setNamespace(namespace);
		if (!properties.isEmpty()) {
			schema.setProperties(properties);
		}
		return schema;
	}

	private Schema getSchemaForType(Class<?> type, Type genericType, Set<Class<?>> visited) {
		if (PRIMITIVE_SCHEMA_CACHE.containsKey(type)) {
			return PRIMITIVE_SCHEMA_CACHE.get(type);
		}

		if (type.isArray()) {
			Schema itemSchema = getSchemaForType(type.getComponentType(), null, visited);
			return Schema.ofArray(type.getSimpleName(), itemSchema);
		}

		if (type.isEnum()) {
			return Schema.ofRef(getNamespaceForClass(type) + "." + type.getSimpleName());
		}

		// Handle List / Flux
		if (List.class.isAssignableFrom(type) || Flux.class.isAssignableFrom(type)) {
			Schema itemSchema = Schema.ofObject("item");
			if (genericType instanceof ParameterizedType paramType) {
				Type[] args = paramType.getActualTypeArguments();
				if (args.length > 0 && args[0] instanceof Class<?> itemClass) {
					String itemNamespace = getNamespaceForClass(itemClass);
					String itemName = itemClass.getSimpleName();
					itemSchema = generateSchemaForClass(itemClass, visited, itemNamespace, itemName);
				}
			}
			return Schema.ofArray(type.getSimpleName(), itemSchema);
		}

		// Handle Mono (Unwrap)
		if (Mono.class.isAssignableFrom(type)) {
			if (genericType instanceof ParameterizedType paramType) {
				Type[] args = paramType.getActualTypeArguments();
				if (args.length > 0 && args[0] instanceof Class<?> monoClass) {
					String monoNamespace = getNamespaceForClass(monoClass);
					String monoName = monoClass.getSimpleName();
					return generateSchemaForClass(monoClass, visited, monoNamespace, monoName);
				}
			}
			return Schema.ofObject("result");
		}

		if (Map.class.isAssignableFrom(type)) {
			return Schema.ofObject("Map");
		}

		String namespace = getNamespaceForClass(type);
		String name = type.getSimpleName();
		return generateSchemaForClass(type, visited, namespace, name);
	}

	private Schema buildEnumSchema(Class<?> clazz, String namespace, String name) {
		List<JsonElement> enumValues = Arrays.stream(clazz.getEnumConstants())
				.filter(Enum.class::isInstance)
				.map(e -> (JsonElement) new JsonPrimitive(((Enum<?>) e).name()))
				.toList();

		Schema schema = Schema.ofString(name).setEnums(enumValues);
		schema.setNamespace(namespace);
		return schema;
	}

	private Map<String, Type> buildTypeVariableMap(Class<?> clazz) {
		Map<String, Type> typeMap = new HashMap<>();
		Class<?> currentClass = clazz;

		while (currentClass != null && currentClass != Object.class) {
			Type genericSuperclass = currentClass.getGenericSuperclass();
			if (genericSuperclass instanceof ParameterizedType paramType) {
				Type rawType = paramType.getRawType();
				if (rawType instanceof Class<?> rawClass) {
					TypeVariable<?>[] typeParams = rawClass.getTypeParameters();
					Type[] actualTypes = paramType.getActualTypeArguments();

					for (int i = 0; i < typeParams.length && i < actualTypes.length; i++) {
						String paramName = typeParams[i].getName();
						Type actualType = actualTypes[i];
						if (actualType instanceof TypeVariable<?> typeVar) {
							Type resolved = typeMap.get(typeVar.getName());
							if (resolved != null) {
								actualType = resolved;
							}
						}
						typeMap.put(paramName, actualType);
					}
				}
			}
			currentClass = currentClass.getSuperclass();
		}
		return typeMap;
	}

	private Type resolveType(Type type, Map<String, Type> typeVariableMap) {
		if (type instanceof TypeVariable<?> typeVar) {
			Type resolved = typeVariableMap.get(typeVar.getName());
			if (resolved != null) return resolved;
			Type[] bounds = typeVar.getBounds();
			if (bounds.length > 0) return resolveType(bounds[0], typeVariableMap);
			return type;
		}
		return type;
	}

	private Class<?> getRawType(Type type) {
		if (type instanceof TypeVariable<?> typeVar) {
			Type[] bounds = typeVar.getBounds();
			if (bounds.length > 0) return getRawType(bounds[0]);
			return Object.class;
		}
		if (type instanceof Class<?> clazz) return clazz;
		if (type instanceof ParameterizedType paramType) {
			Type rawType = paramType.getRawType();
			if (rawType instanceof Class<?> clazz) return clazz;
		}
		return Object.class;
	}

	public String getNamespaceForClass(Class<?> clazz) {
		return namespaceCache.computeIfAbsent(clazz, this::computeNamespaceForClass);
	}

	private String computeNamespaceForClass(Class<?> clazz) {
		Package pkg = clazz.getPackage();
		if (pkg == null) return packageConfig.getDefaultNamespace();

		String packageName = pkg.getName();
		if (packageName.startsWith(packageConfig.getCommonsPackagePrefix())) {
			return packageConfig.getCommonsNamespace();
		}

		for (PackageMapping mapping : packageConfig.getPackageMappings()) {
			if (packageName.startsWith(mapping.getPackagePrefix())) {
				if (mapping.isAppendSubpackages()) {
					return buildNamespaceFromPackage(packageName, mapping.getPackagePrefix(), mapping.getBaseNamespace());
				} else {
					return mapping.getBaseNamespace();
				}
			}
		}
		return packageConfig.getDefaultNamespace();
	}

	private String buildNamespaceFromPackage(String fullPackageName, String basePackage, String baseNamespace) {
		String subPackage = fullPackageName.substring(basePackage.length());
		if (subPackage.isEmpty()) return baseNamespace;
		if (subPackage.startsWith(".")) subPackage = subPackage.substring(1);

		return baseNamespace + Arrays.stream(subPackage.split("\\."))
				.filter(s -> !s.isEmpty())
				.map(this::capitalize)
				.map(s -> "." + s)
				.collect(Collectors.joining());
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty()) return str;
		return Character.toUpperCase(str.charAt(0)) + str.substring(1);
	}

	@Data
	@Builder
	public static class PackageConfig {
		@Builder.Default private String serviceName = "Commons";
		@Builder.Default private String commonsPackagePrefix = "com.fincity.saas.commons";
		@Builder.Default private String commonsNamespace = "Commons";
		@Builder.Default private String defaultNamespace = "Common";
		@Builder.Default private List<PackageMapping> packageMappings = List.of();

		public static PackageConfig forEntityProcessor() {
			return PackageConfig.builder()
					.serviceName("EntityProcessor")
					.commonsPackagePrefix("com.fincity.saas.commons")
					.commonsNamespace("Commons")
					.defaultNamespace("Common")
					.packageMappings(List.of(
							PackageMapping.of("com.fincity.saas.entity.processor.dto", "EntityProcessor.DTO", true),
							PackageMapping.of("com.fincity.saas.entity.processor.model", "EntityProcessor.Model", true),
							PackageMapping.of("com.fincity.saas.entity.processor", "EntityProcessor.Common", false)
					))
					.build();
		}

		public static PackageConfig forCommons() {
			return PackageConfig.builder()
					.serviceName("Commons")
					.commonsPackagePrefix("com.fincity.saas.commons")
					.commonsNamespace("Commons")
					.defaultNamespace("Common")
					.packageMappings(List.of())
					.build();
		}
	}

	@Data
	@Builder
	public static class PackageMapping {
		private String packagePrefix;
		private String baseNamespace;
		@Builder.Default private boolean appendSubpackages = true;

		public static PackageMapping of(String packagePrefix, String baseNamespace, boolean appendSubpackages) {
			return PackageMapping.builder()
					.packagePrefix(packagePrefix)
					.baseNamespace(baseNamespace)
					.appendSubpackages(appendSubpackages)
					.build();
		}
	}

	public record ArgSpec<P>(String name, Parameter parameter, BiFunction<Gson, JsonElement, P> parser) {

		public static <P> ArgSpec<P> custom(String name, Schema schema, BiFunction<Gson, JsonElement, P> parser) {
			return new ArgSpec<>(name, Parameter.of(name, schema), parser);
		}

		public static <P> ArgSpec<P> custom(String name, Parameter parameter, BiFunction<Gson, JsonElement, P> parser) {
			return new ArgSpec<>(name, parameter, parser);
		}

		public static <P> ArgSpec<P> of(String name, Schema schema, Class<P> clazz) {
			return new ArgSpec<>(
					name,
					Parameter.of(name, schema),
					(g, j) -> j == null || j.isJsonNull() ? null : g.fromJson(j, clazz));
		}

		public static <P> ArgSpec<P> ofRef(String name, String schemaRef, Class<P> clazz) {
			return of(name, Schema.ofRef(schemaRef), clazz);
		}

		public static <P> ArgSpec<P> ofRef(String name, Class<P> clazz) {
			String schemaRef = ClassSchema.getInstance().getNamespaceForClass(clazz) + "." + clazz.getSimpleName();
			return ofRef(name, schemaRef, clazz);
		}

		public static ArgSpec<String> string(String name) {
			Schema stringSchema = PRIMITIVE_SCHEMA_CACHE.get(String.class);
			return new ArgSpec<>(
					name,
					Parameter.of(name, stringSchema == null ? Schema.ofString("String") : stringSchema),
					(g, j) -> j == null || j.isJsonNull() ? null : j.getAsString());
		}

		public static ArgSpec<Boolean> bool(String name) {
			Schema boolSchema = PRIMITIVE_SCHEMA_CACHE.get(Boolean.class);
			return new ArgSpec<>(
					name,
					Parameter.of(name, boolSchema == null ? Schema.ofBoolean("Boolean") : boolSchema),
					(g, j) -> j == null || j.isJsonNull() ? null : j.getAsBoolean());
		}

		public static ArgSpec<LocalDateTime> dateTimeString(String name) {
			Schema stringSchema = PRIMITIVE_SCHEMA_CACHE.get(String.class);
			return custom(
					name,
					stringSchema == null ? Schema.ofString("String") : stringSchema,
					(g, j) -> j == null || j.isJsonNull() ? null : LocalDateTime.parse(j.getAsString()));
		}

		public static ArgSpec<Map<String, String>> stringMap(String name) {
			return custom(name, Schema.ofObject(name), (g, j) -> {
				Map<String, String> out = new LinkedHashMap<>();
				if (j == null || j.isJsonNull() || !j.isJsonObject()) return out;
				for (Map.Entry<String, JsonElement> e : j.getAsJsonObject().entrySet()) {
					if (e.getValue() == null || e.getValue().isJsonNull()) continue;
					out.put(e.getKey(), e.getValue().getAsString());
				}
				return out;
			});
		}

		public static ArgSpec<Pageable> pageable() {
			return pageable("pageable");
		}

		public static ArgSpec<Pageable> pageable(String name) {
			return custom(
					name,
					Schema.ofRef(PAGEABLE_SCHEMA_REF),
					(g, j) -> j == null || j.isJsonNull() ? null : g.fromJson(j, Pageable.class));
		}

		public static ArgSpec<Query> query() {
			return query("query");
		}

		public static ArgSpec<Query> query(String name) {
			return ofRef(name, QUERY_SCHEMA_REF, Query.class);
		}

		public static ArgSpec<AbstractCondition> condition() {
			return condition("condition");
		}

		public static ArgSpec<AbstractCondition> condition(String name) {
			return custom(
					name,
					Schema.ofRef(CONDITION_SCHEMA_REF),
					(g, j) -> j == null || j.isJsonNull() ? null : g.fromJson(j, AbstractCondition.class));
		}

		public static ArgSpec<List<String>> stringList(String name) {
			Schema stringSchema = PRIMITIVE_SCHEMA_CACHE.get(String.class);
			Schema item = stringSchema == null ? Schema.ofString("String") : stringSchema;
			return custom(name, Schema.ofArray(name, item), (g, j) -> {
				if (j == null || j.isJsonNull()) return List.of();
				if (!j.isJsonArray()) return List.of();
				List<String> out = new ArrayList<>();
				j.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
				return out;
			});
		}

		public static ArgSpec<List<String>> fields() {
			return stringList("fields");
		}

		public static ArgSpec<MultiValueMap<String, String>> queryParams() {
			return queryParams("queryParams");
		}

		public static ArgSpec<MultiValueMap<String, String>> queryParams(String name) {
			return custom(name, Schema.ofObject(name), (g, j) -> {
				MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
				if (j == null || j.isJsonNull() || !j.isJsonObject()) return queryParams;
				JsonObject obj = j.getAsJsonObject();
				obj.entrySet().forEach(e -> {
					JsonElement v = e.getValue();
					if (v == null || v.isJsonNull()) return;
					if (v.isJsonArray()) v.getAsJsonArray().forEach(x -> queryParams.add(e.getKey(), x.getAsString()));
					else queryParams.add(e.getKey(), v.getAsString());
				});
				return queryParams;
			});
		}
	}
}
