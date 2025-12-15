package com.fincity.saas.entity.processor.functions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.functions.anntations.IgnoreServerFunc;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ServiceSchemaGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceSchemaGenerator.class);

    private static final Set<String> EXCLUDED_METHOD_NAMES = Set.of("equals", "hashCode", "toString", "getClass",
            "wait", "notify", "notifyAll");

    private static final String SERVICE_SUFFIX = "Service";
    private static final String NAMESPACE_PREFIX = "EntityProcessor.";
    private static final String ENTITY_PROCESSOR_SERVICE_PACKAGE = "entity.processor.service";
    private static final String COMMONS_PACKAGE_PREFIX = "com.fincity.saas.commons";

    private static final Map<Class<?>, Schema> PRIMITIVE_SCHEMA_CACHE = new ConcurrentHashMap<>();

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
    }

    private final Gson gson;
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

    public ServiceSchemaGenerator(Gson gson) {
        this.gson = gson;
    }

    public Map<String, Schema> generateSchemas(List<Object> services) {
        Set<Class<?>> pojoClasses = new HashSet<>();

        // Collect all POJO classes from service methods
        for (Object service : services) {
            if (service == null)
                continue;

            Class<?> serviceClass = service.getClass();
            if (serviceClass.isAnnotationPresent(IgnoreServerFunc.class))
                continue;

            Method[] methods = serviceClass.getMethods();
            for (Method method : methods) {
                if (!isValidMethod(method, serviceClass))
                    continue;

                // Collect parameter types
                for (Class<?> paramType : method.getParameterTypes()) {
                    collectPojoClasses(paramType, pojoClasses);
                }

                // Collect return type
                Class<?> returnType = method.getReturnType();
                if (Mono.class.isAssignableFrom(returnType)) {
                    java.lang.reflect.Type genericReturnType = method.getGenericReturnType();
                    if (genericReturnType instanceof ParameterizedType paramType) {
                        java.lang.reflect.Type monoType = paramType.getActualTypeArguments()[0];
                        if (monoType instanceof Class<?> clazz) {
                            collectPojoClasses(clazz, pojoClasses);
                        }
                    }
                } else if (Flux.class.isAssignableFrom(returnType)) {
                    java.lang.reflect.Type genericReturnType = method.getGenericReturnType();
                    if (genericReturnType instanceof ParameterizedType paramType) {
                        java.lang.reflect.Type fluxType = paramType.getActualTypeArguments()[0];
                        if (fluxType instanceof Class<?> clazz) {
                            collectPojoClasses(clazz, pojoClasses);
                        }
                    }
                } else {
                    collectPojoClasses(returnType, pojoClasses);
                }
            }
        }

        // Generate schemas for all collected POJOs
        Map<String, Schema> schemas = new HashMap<>();
        for (Class<?> pojoClass : pojoClasses) {
            try {
                String namespace = getNamespaceForClass(pojoClass);
                String name = pojoClass.getSimpleName();
                Schema schema = generateSchemaForClass(pojoClass, new HashSet<>(), namespace, name);
                schemas.put(namespace + "." + name, schema);
            } catch (Exception e) {
                LOGGER.error(
                        "Failed to generate schema for class {}: {}",
                        pojoClass.getName(),
                        e.getMessage(),
                        e);
            }
        }

        return schemas;
    }

    private void collectPojoClasses(Class<?> type, Set<Class<?>> pojoClasses) {
        if (type == null)
            return;

        // Skip primitives, wrappers, and common types
        if (PRIMITIVE_SCHEMA_CACHE.containsKey(type))
            return;
        if (type.isPrimitive())
            return;
        if (type.isArray()) {
            collectPojoClasses(type.getComponentType(), pojoClasses);
            return;
        }
        if (Mono.class.isAssignableFrom(type) || Flux.class.isAssignableFrom(type))
            return;
        if (List.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))
            return;
        if (type.getPackage() == null)
            return;

        String packageName = type.getPackage().getName();

        // Only collect classes from entity.processor or commons packages
        if (packageName.contains("entity.processor")
                || packageName.startsWith(COMMONS_PACKAGE_PREFIX)
                || packageName.startsWith("com.modlix.saas.commons2")) {
            if (!pojoClasses.contains(type)) {
                pojoClasses.add(type);
                // Recursively collect nested types
                collectNestedTypes(type, pojoClasses);
            }
        }
    }

    private void collectNestedTypes(Class<?> type, Set<Class<?>> pojoClasses) {
        // Collect field types
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;
            collectPojoClasses(field.getType(), pojoClasses);

            java.lang.reflect.Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType paramType) {
                for (java.lang.reflect.Type argType : paramType.getActualTypeArguments()) {
                    if (argType instanceof Class<?> clazz) {
                        collectPojoClasses(clazz, pojoClasses);
                    }
                }
            }
        }
    }

    private Schema generateSchemaForClass(Class<?> clazz, Set<Class<?>> visited) {
        String namespace = getNamespaceForClass(clazz);
        String name = clazz.getSimpleName();
        return generateSchemaForClass(clazz, visited, namespace, name);
    }

    private Schema generateSchemaForClass(Class<?> clazz, Set<Class<?>> visited, String namespace, String name) {
        if (visited.contains(clazz)) {
            // Circular reference - return object schema with namespace
            Schema schema = Schema.ofObject(name);
            schema.setNamespace(namespace);
            return schema;
        }

        // Check cache first
        String cacheKey = clazz.getName();
        if (schemaCache.containsKey(cacheKey)) {
            return schemaCache.get(cacheKey);
        }

        // Check primitive cache
        if (PRIMITIVE_SCHEMA_CACHE.containsKey(clazz)) {
            return PRIMITIVE_SCHEMA_CACHE.get(clazz);
        }

        // Enums should be referenced, not expanded inline
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
        Map<String, Schema> properties = new HashMap<>();

        // Build a map of generic type parameters from the class hierarchy
        Map<String, java.lang.reflect.Type> typeVariableMap = buildTypeVariableMap(clazz);

        // Get all fields including inherited ones
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                if (Modifier.isTransient(field.getModifiers()))
                    continue;

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

    private Map<String, java.lang.reflect.Type> buildTypeVariableMap(Class<?> clazz) {
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

    private java.lang.reflect.Type resolveType(java.lang.reflect.Type type,
            Map<String, java.lang.reflect.Type> typeVariableMap) {
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

    private Class<?> getRawType(java.lang.reflect.Type type) {
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

    private Schema getSchemaForType(Class<?> type, java.lang.reflect.Type genericType, Set<Class<?>> visited) {
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
                    itemSchema = generateSchemaForClass(itemClass, visited);
                }
            }
            return Schema.ofArray(type.getSimpleName(), itemSchema);
        }

        // Handle Mono
        if (Mono.class.isAssignableFrom(type)) {
            if (genericType instanceof ParameterizedType paramType) {
                java.lang.reflect.Type[] args = paramType.getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class<?> monoClass) {
                    return generateSchemaForClass(monoClass, visited);
                }
            }
            return Schema.ofObject("result");
        }

        // Handle Map
        if (Map.class.isAssignableFrom(type)) {
            return Schema.ofObject("Map");
        }

        // For other types, generate object schema
        return generateSchemaForClass(type, visited);
    }

    private String getNamespaceForClass(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null)
            return NAMESPACE_PREFIX + "Common";

        String packageName = pkg.getName();
        if (packageName.contains("entity.processor.dto")) {
            return NAMESPACE_PREFIX + "DTO";
        }
        if (packageName.contains("entity.processor.model")) {
            return NAMESPACE_PREFIX + "Model";
        }
        if (packageName.startsWith(COMMONS_PACKAGE_PREFIX)) {
            return NAMESPACE_PREFIX + "Commons";
        }
        if (packageName.startsWith("com.modlix.saas.commons2")) {
            return NAMESPACE_PREFIX + "Commons2";
        }

        return NAMESPACE_PREFIX + "Common";
    }

    private boolean isValidMethod(Method method, Class<?> serviceClass) {
        if (method.isAnnotationPresent(IgnoreServerFunc.class))
            return false;
        if (!Modifier.isPublic(method.getModifiers()))
            return false;
        if (method.isBridge() || method.isSynthetic())
            return false;

        String methodName = method.getName();
        if (EXCLUDED_METHOD_NAMES.contains(methodName) || methodName.charAt(0) == '_')
            return false;

        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Object.class)
            return false;

        if (declaringClass == serviceClass)
            return true;

        Package declaringPackage = declaringClass.getPackage();
        if (declaringPackage == null)
            return false;

        String packageName = declaringPackage.getName();
        return packageName.contains(ENTITY_PROCESSOR_SERVICE_PACKAGE)
                || packageName.startsWith(COMMONS_PACKAGE_PREFIX);
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
}
