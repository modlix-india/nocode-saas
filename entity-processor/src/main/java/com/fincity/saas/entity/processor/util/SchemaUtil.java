package com.fincity.saas.entity.processor.util;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class SchemaUtil {

    private static final String ENTITY_PROCESSOR_DTO_PACKAGE = "com.fincity.saas.entity.processor.dto";
    private static final String ENTITY_PROCESSOR_MODEL_PACKAGE = "com.fincity.saas.entity.processor.model";
    private static final String COMMONS_PACKAGE_PREFIX = "com.fincity.saas.commons";

    private static final Map<Class<?>, Schema> PRIMITIVE_SCHEMA_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    static {
        PRIMITIVE_SCHEMA_CACHE.put(String.class, Schema.ofString("String"));
        PRIMITIVE_SCHEMA_CACHE.put(Integer.class, Schema.ofInteger("Integer"));
        PRIMITIVE_SCHEMA_CACHE.put(int.class, Schema.ofInteger("int"));
        PRIMITIVE_SCHEMA_CACHE.put(Long.class, Schema.ofLong("Long"));
        PRIMITIVE_SCHEMA_CACHE.put(long.class, Schema.ofLong("long"));
        PRIMITIVE_SCHEMA_CACHE.put(Double.class, Schema.ofDouble("Double"));
        PRIMITIVE_SCHEMA_CACHE.put(double.class, Schema.ofDouble("double"));
        PRIMITIVE_SCHEMA_CACHE.put(Float.class, Schema.ofFloat("Float"));
        PRIMITIVE_SCHEMA_CACHE.put(float.class, Schema.ofFloat("float"));
        PRIMITIVE_SCHEMA_CACHE.put(Boolean.class, Schema.ofBoolean("Boolean"));
        PRIMITIVE_SCHEMA_CACHE.put(boolean.class, Schema.ofBoolean("boolean"));
        PRIMITIVE_SCHEMA_CACHE.put(java.time.LocalDateTime.class, Schema.ofRef("System.Date.Timestamp"));
        PRIMITIVE_SCHEMA_CACHE.put(java.time.LocalDate.class, Schema.ofRef("System.Date.Timestamp"));
        PRIMITIVE_SCHEMA_CACHE.put(ULong.class, Schema.ofLong("ULong").setMinimum(0L));
        PRIMITIVE_SCHEMA_CACHE.put(UInteger.class, Schema.ofInteger("UInteger").setMinimum(0));
        PRIMITIVE_SCHEMA_CACHE.put(UShort.class, Schema.ofInteger("UShort").setMinimum(0));
        PRIMITIVE_SCHEMA_CACHE.put(UByte.class, Schema.ofInteger("UByte").setMinimum(0));
        PRIMITIVE_SCHEMA_CACHE.put(JsonObject.class, Schema.ofObject("JsonObject"));
        PRIMITIVE_SCHEMA_CACHE.put(BigInteger.class, Schema.ofInteger("BigInteger"));
        Schema abstractConditionSchema =
                new Schema().setName("AbstractCondition").setNamespace("Commons");
        abstractConditionSchema.setAnyOf(
                List.of(Schema.ofRef("Commons.FilterCondition"), Schema.ofRef("Commons.ComplexCondition")));

        PRIMITIVE_SCHEMA_CACHE.put(AbstractCondition.class, abstractConditionSchema);
    }

    /**
     * Generates a namespace for a class based on its package name.
     *
     * Rules:
     * - "com.fincity.saas.entity.processor.dto" -> "EntityProcessor.DTO"
     * - "com.fincity.saas.entity.processor.dto.content" ->
     * "EntityProcessor.DTO.Content"
     * - "com.fincity.saas.entity.processor.model" -> "EntityProcessor.Model"
     * - "com.fincity.saas.commons" -> "Commons"
     * - Each subpackage part is capitalized (uppercase first letter)
     */
    public static String getNamespaceForClass(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null) {
            return "Common";
        }

        String packageName = pkg.getName();

        // Handle commons package - return just "Commons"
        if (packageName.startsWith(COMMONS_PACKAGE_PREFIX)) {
            return "Commons";
        }

        // Handle entity.processor packages
        if (packageName.startsWith(ENTITY_PROCESSOR_DTO_PACKAGE)) {
            return buildNamespaceFromPackage(packageName, ENTITY_PROCESSOR_DTO_PACKAGE, "EntityProcessor.DTO");
        }
        if (packageName.startsWith(ENTITY_PROCESSOR_MODEL_PACKAGE)) {
            return buildNamespaceFromPackage(packageName, ENTITY_PROCESSOR_MODEL_PACKAGE, "EntityProcessor.Model");
        }

        // Default fallback
        if (packageName.contains("entity.processor")) {
            return "EntityProcessor.Common";
        }

        return "Common";
    }

    /**
     * Builds namespace from package name by capitalizing subpackage parts.
     * Example: "com.fincity.saas.entity.processor.dto.content" ->
     * "EntityProcessor.DTO.Content"
     */
    private static String buildNamespaceFromPackage(String fullPackageName, String basePackage, String baseNamespace) {
        // Remove the base package prefix
        String subPackage = fullPackageName.substring(basePackage.length());

        if (subPackage.isEmpty()) {
            return baseNamespace;
        }

        // Remove leading dot if present
        if (subPackage.startsWith(".")) {
            subPackage = subPackage.substring(1);
        }

        // Split by dots and capitalize each part
        String[] parts = subPackage.split("\\.");
        StringBuilder namespace = new StringBuilder(baseNamespace);

        for (String part : parts) {
            if (!part.isEmpty()) {
                namespace.append(".").append(capitalize(part));
            }
        }

        return namespace.toString();
    }

    /**
     * Capitalizes the first letter of a string.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Checks if a type is a primitive type that should be skipped during
     * collection.
     */
    public static boolean isPrimitiveType(Class<?> type) {
        if (type == null) {
            return false;
        }
        return PRIMITIVE_SCHEMA_CACHE.containsKey(type) || type.isPrimitive();
    }

    /**
     * Checks if a class should be included in schema generation.
     * Excludes classes annotated with @IgnoreGeneration.
     */
    public static boolean shouldIncludeClass(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return !clazz.isAnnotationPresent(IgnoreGeneration.class);
    }

    /**
     * Checks if a class is from a package that should be included in schema
     * generation.
     */
    public static boolean isRelevantPackage(Class<?> type) {
        if (type == null || type.getPackage() == null) {
            return false;
        }

        String packageName = type.getPackage().getName();
        return packageName.startsWith(ENTITY_PROCESSOR_DTO_PACKAGE)
                || packageName.startsWith(ENTITY_PROCESSOR_MODEL_PACKAGE)
                || packageName.startsWith(COMMONS_PACKAGE_PREFIX)
                || packageName.contains("entity.processor");
    }

    /**
     * Collects nested types from a class by examining its fields.
     * Recursively collects field types and generic type arguments.
     */
    public static void collectNestedTypes(Class<?> type, Set<Class<?>> pojoClasses) {
        if (type == null) {
            return;
        }

        // Collect field types from all levels of the class hierarchy
        Class<?> currentClass = type;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                collectType(field.getType(), pojoClasses);

                java.lang.reflect.Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType paramType) {
                    for (java.lang.reflect.Type argType : paramType.getActualTypeArguments()) {
                        if (argType instanceof Class<?> clazz) {
                            collectType(clazz, pojoClasses);
                        } else if (argType instanceof ParameterizedType nestedParamType) {
                            // Handle nested generic types
                            java.lang.reflect.Type rawType = nestedParamType.getRawType();
                            if (rawType instanceof Class<?> rawClass) {
                                collectType(rawClass, pojoClasses);
                            }
                            // Recursively collect nested type arguments
                            for (java.lang.reflect.Type nestedArgType : nestedParamType.getActualTypeArguments()) {
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

    /**
     * Collects a type if it's relevant and not already collected.
     */
    private static void collectType(Class<?> type, Set<Class<?>> pojoClasses) {
        if (type == null) {
            return;
        }

        // Skip primitives, arrays, and reactive types
        if (type.isPrimitive() || type.isArray()) {
            return;
        }
        if (type.getPackage() == null) {
            return;
        }

        // Only collect if it's a relevant package and not already collected
        if (isRelevantPackage(type) && !pojoClasses.contains(type) && shouldIncludeClass(type)) {
            pojoClasses.add(type);
            // Recursively collect nested types
            collectNestedTypes(type, pojoClasses);
        }
    }

    /**
     * Generates a schema for a single class.
     *
     * @param clazz the class to generate a schema for
     * @return the generated schema, or null if generation fails
     */
    public static Schema generateSchemaForClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        String namespace = getNamespaceForClass(clazz);
        String name = clazz.getSimpleName();
        return generateSchemaForClass(clazz, new HashSet<>(), namespace, name);
    }

    /**
     * Generates a schema for a single class with specified namespace and name.
     *
     * @param clazz     the class to generate a schema for
     * @param visited   set of already visited classes to prevent circular
     *                  references
     * @param namespace the namespace for the schema
     * @param name      the name for the schema
     * @return the generated schema
     */
    public static Schema generateSchemaForClass(Class<?> clazz, Set<Class<?>> visited, String namespace, String name) {
        if (visited.contains(clazz)) {
            // Circular reference - return object schema with namespace
            Schema schema = Schema.ofObject(name);
            schema.setNamespace(namespace);
            return schema;
        }

        // Check cache first
        String cacheKey = clazz.getName();
        if (SCHEMA_CACHE.containsKey(cacheKey)) {
            return SCHEMA_CACHE.get(cacheKey);
        }

        // Check primitive cache
        if (PRIMITIVE_SCHEMA_CACHE.containsKey(clazz)) {
            return PRIMITIVE_SCHEMA_CACHE.get(clazz);
        }

        // Enums should be referenced, not expanded inline
        if (clazz.isEnum()) {
            Schema enumSchema = buildEnumSchema(clazz, namespace, name);
            SCHEMA_CACHE.put(cacheKey, enumSchema);
            return enumSchema;
        }

        visited.add(clazz);

        try {
            Schema schema = buildObjectSchema(clazz, visited, namespace, name);
            SCHEMA_CACHE.put(cacheKey, schema);
            return schema;
        } finally {
            visited.remove(clazz);
        }
    }

    private static Schema buildObjectSchema(Class<?> clazz, Set<Class<?>> visited, String namespace, String name) {
        Map<String, Schema> properties = new HashMap<>();

        // Build a map of generic type parameters from the class hierarchy
        Map<String, java.lang.reflect.Type> typeVariableMap = buildTypeVariableMap(clazz);

        // Get all fields including inherited ones
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (Modifier.isTransient(field.getModifiers())) continue;

                String fieldName = field.getName();
                java.lang.reflect.Type fieldGenericType = field.getGenericType();

                // Resolve generic type parameters
                java.lang.reflect.Type resolvedType = resolveType(fieldGenericType, typeVariableMap);
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

    private static Map<String, java.lang.reflect.Type> buildTypeVariableMap(Class<?> clazz) {
        Map<String, java.lang.reflect.Type> typeMap = new HashMap<>();
        Class<?> currentClass = clazz;

        while (currentClass != null && currentClass != Object.class) {
            java.lang.reflect.Type genericSuperclass = currentClass.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType paramType) {
                java.lang.reflect.Type rawType = paramType.getRawType();
                if (rawType instanceof Class<?> rawClass) {
                    java.lang.reflect.TypeVariable<?>[] typeParams = rawClass.getTypeParameters();
                    java.lang.reflect.Type[] actualTypes = paramType.getActualTypeArguments();

                    for (int i = 0; i < typeParams.length && i < actualTypes.length; i++) {
                        String paramName = typeParams[i].getName();
                        java.lang.reflect.Type actualType = actualTypes[i];

                        // Resolve nested type variables
                        if (actualType instanceof java.lang.reflect.TypeVariable<?> typeVar) {
                            java.lang.reflect.Type resolved = typeMap.get(typeVar.getName());
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

    private static java.lang.reflect.Type resolveType(
            java.lang.reflect.Type type, Map<String, java.lang.reflect.Type> typeVariableMap) {
        if (type instanceof java.lang.reflect.TypeVariable<?> typeVar) {
            java.lang.reflect.Type resolved = typeVariableMap.get(typeVar.getName());
            if (resolved != null) {
                return resolved;
            }
            // If not resolved, check bounds
            java.lang.reflect.Type[] bounds = typeVar.getBounds();
            if (bounds.length > 0) {
                return resolveType(bounds[0], typeVariableMap);
            }
            return type;
        }
        return type;
    }

    private static Class<?> getRawType(java.lang.reflect.Type type) {
        // First resolve any type variables
        if (type instanceof java.lang.reflect.TypeVariable<?> typeVar) {
            // This should have been resolved by resolveType, but as fallback check bounds
            java.lang.reflect.Type[] bounds = typeVar.getBounds();
            if (bounds.length > 0) {
                return getRawType(bounds[0]);
            }
            return Object.class;
        }

        if (type instanceof Class<?> clazz) {
            return clazz;
        }

        if (type instanceof ParameterizedType paramType) {
            java.lang.reflect.Type rawType = paramType.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return clazz;
            }
        }

        return Object.class;
    }

    private static Schema getSchemaForType(Class<?> type, java.lang.reflect.Type genericType, Set<Class<?>> visited) {
        // Check primitive cache
        if (PRIMITIVE_SCHEMA_CACHE.containsKey(type)) {
            return PRIMITIVE_SCHEMA_CACHE.get(type);
        }

        // Handle arrays
        if (type.isArray()) {
            Schema itemSchema = getSchemaForType(type.getComponentType(), null, visited);
            return Schema.ofArray(type.getSimpleName(), itemSchema);
        }

        // Enums should reference the generated enum schema
        if (type.isEnum()) {
            return Schema.ofRef(getNamespaceForClass(type) + "." + type.getSimpleName());
        }

        // Handle List/Flux
        if (List.class.isAssignableFrom(type) || Flux.class.isAssignableFrom(type)) {
            Schema itemSchema = Schema.ofObject("item");
            if (genericType instanceof ParameterizedType paramType) {
                java.lang.reflect.Type[] args = paramType.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> itemClass) {
                    String itemNamespace = getNamespaceForClass(itemClass);
                    String itemName = itemClass.getSimpleName();
                    itemSchema = generateSchemaForClass(itemClass, visited, itemNamespace, itemName);
                }
            }
            return Schema.ofArray(type.getSimpleName(), itemSchema);
        }

        // Handle Mono
        if (Mono.class.isAssignableFrom(type)) {
            if (genericType instanceof ParameterizedType paramType) {
                java.lang.reflect.Type[] args = paramType.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> monoClass) {
                    String monoNamespace = getNamespaceForClass(monoClass);
                    String monoName = monoClass.getSimpleName();
                    return generateSchemaForClass(monoClass, visited, monoNamespace, monoName);
                }
            }
            return Schema.ofObject("result");
        }

        // Handle Map
        if (Map.class.isAssignableFrom(type)) {
            return Schema.ofObject("Map");
        }

        // For other types, generate object schema
        String namespace = getNamespaceForClass(type);
        String name = type.getSimpleName();
        return generateSchemaForClass(type, visited, namespace, name);
    }

    private static Schema buildEnumSchema(Class<?> clazz, String namespace, String name) {
        List<JsonElement> enumValues = Arrays.stream(clazz.getEnumConstants())
                .filter(Enum.class::isInstance)
                .map(e -> (JsonElement) new JsonPrimitive(((Enum<?>) e).name()))
                .toList();

        Schema schema = Schema.ofString(name).setEnums(enumValues);
        schema.setNamespace(namespace);
        return schema;
    }
}
