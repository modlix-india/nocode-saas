package com.fincity.saas.entity.processor.functions;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.functions.annotations.IgnoreGeneration;
import com.fincity.saas.entity.processor.util.SchemaUtil;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ServiceSchemaGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceSchemaGenerator.class);

    private static final Set<String> EXCLUDED_METHOD_NAMES =
            Set.of("equals", "hashCode", "toString", "getClass", "wait", "notify", "notifyAll");

    private static final String ENTITY_PROCESSOR_SERVICE_PACKAGE = "entity.processor.service";
    private static final String COMMONS_PACKAGE_PREFIX = "com.fincity.saas.commons";

    public ServiceSchemaGenerator() {}

    public Map<String, Schema> generateSchemas(List<Object> services) {
        Set<Class<?>> pojoClasses = new HashSet<>();

        // Collect all POJO classes from service methods
        for (Object service : services) {
            if (service == null) continue;

            Class<?> serviceClass = service.getClass();
            if (serviceClass.isAnnotationPresent(IgnoreGeneration.class)) continue;

            Method[] methods = serviceClass.getMethods();
            for (Method method : methods) {
                if (!isValidMethod(method, serviceClass)) continue;

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

        // Explicitly add FilterCondition and ComplexCondition to ensure they're always
        // included
        try {
            Class<?> filterConditionClass = Class.forName("com.fincity.saas.commons.model.condition.FilterCondition");
            collectPojoClasses(filterConditionClass, pojoClasses);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("FilterCondition class not found: {}", e.getMessage());
        }

        try {
            Class<?> complexConditionClass = Class.forName("com.fincity.saas.commons.model.condition.ComplexCondition");
            collectPojoClasses(complexConditionClass, pojoClasses);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("ComplexCondition class not found: {}", e.getMessage());
        }

        // Generate schemas for all collected POJOs (excluding abstract classes and
        // ignored classes)
        Map<String, Schema> schemas = new HashMap<>();
        for (Class<?> pojoClass : pojoClasses) {
            // Skip abstract classes - they should not have standalone schemas
            if (Modifier.isAbstract(pojoClass.getModifiers())) {
                continue;
            }
            // Skip classes marked with @IgnoreGeneration
            if (!SchemaUtil.shouldIncludeClass(pojoClass)) {
                continue;
            }
            try {
                String namespace = SchemaUtil.getNamespaceForClass(pojoClass);
                String name = pojoClass.getSimpleName();
                Schema schema = SchemaUtil.generateSchemaForClass(pojoClass, new HashSet<>(), namespace, name);
                schemas.put(namespace + "." + name, schema);
            } catch (Exception e) {
                LOGGER.error("Failed to generate schema for class {}: {}", pojoClass.getName(), e.getMessage(), e);
            }
        }

        // Explicitly add AbstractCondition schema to the repository
        try {
            Class<?> abstractConditionClass =
                    Class.forName("com.fincity.saas.commons.model.condition.AbstractCondition");
            Schema abstractConditionSchema = SchemaUtil.generateSchemaForClass(abstractConditionClass);
            if (abstractConditionSchema != null) {
                String namespace = SchemaUtil.getNamespaceForClass(abstractConditionClass);
                schemas.put(namespace + "." + abstractConditionClass.getSimpleName(), abstractConditionSchema);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.warn("AbstractCondition class not found: {}", e.getMessage());
        }

        // Explicitly add Query schema to the repository
        try {
            Class<?> queryClass = Class.forName("com.fincity.saas.commons.model.Query");
            Schema querySchema = SchemaUtil.generateSchemaForClass(queryClass);
            if (querySchema != null) {
                String namespace = SchemaUtil.getNamespaceForClass(queryClass);
                schemas.put(namespace + "." + queryClass.getSimpleName(), querySchema);
            }
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Query class not found: {}", e.getMessage());
        }

        // Explicitly add Pageable schema to the repository
        // Pageable is an interface, so we create a schema based on how PageableTypeAdapter serializes it
        try {
            // Verify Pageable class exists
            Class.forName("org.springframework.data.domain.Pageable");
            // Create a schema that matches the PageableTypeAdapter structure: page, size, sort
            Schema pageableSchema = Schema.ofObject("Pageable")
                    .setNamespace("Commons")
                    .setProperties(Map.of(
                            "page", Schema.ofInteger("page").setDescription("Page number (0-indexed)"),
                            "size", Schema.ofInteger("size").setDescription("Page size"),
                            "sort",
                                    Schema.ofObject("Sort")
                                            .setProperties(Map.of(
                                                    "property",
                                                            Schema.ofString("property")
                                                                    .setDescription("Sort property name"),
                                                    "direction",
                                                            Schema.ofString("direction")
                                                                    .setEnums(List.of(
                                                                            new JsonPrimitive("ASC"),
                                                                            new JsonPrimitive("DESC")))
                                                                    .setDescription("Sort direction"),
                                                    "ignoreCase",
                                                            Schema.ofBoolean("ignoreCase")
                                                                    .setDescription(
                                                                            "Whether to ignore case when sorting")))
                                            .setDescription("Sort configuration")));
            schemas.put("Commons.Pageable", pageableSchema);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Pageable class not found: {}", e.getMessage());
        }

        return schemas;
    }

    private void collectPojoClasses(Class<?> type, Set<Class<?>> pojoClasses) {
        if (type == null) return;

        // Skip primitives, wrappers, and common types
        // Use SchemaUtil to check if it's a primitive type
        if (SchemaUtil.isPrimitiveType(type)) return;
        if (type.isPrimitive()) return;
        if (type.isArray()) {
            collectPojoClasses(type.getComponentType(), pojoClasses);
            return;
        }
        if (Mono.class.isAssignableFrom(type) || Flux.class.isAssignableFrom(type)) return;
        if (List.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) return;
        if (type.getPackage() == null) return;

        // Skip classes marked with @IgnoreGeneration
        if (!SchemaUtil.shouldIncludeClass(type)) {
            return;
        }

        // Only collect classes from relevant packages
        if (SchemaUtil.isRelevantPackage(type)) {
            if (!pojoClasses.contains(type)) {
                pojoClasses.add(type);
                // Recursively collect nested types using utility
                SchemaUtil.collectNestedTypes(type, pojoClasses);
            }
        }
    }

    private boolean isValidMethod(Method method, Class<?> serviceClass) {
        if (method.isAnnotationPresent(IgnoreGeneration.class)) return false;
        if (!Modifier.isPublic(method.getModifiers())) return false;
        if (method.isBridge() || method.isSynthetic()) return false;

        String methodName = method.getName();
        if (EXCLUDED_METHOD_NAMES.contains(methodName) || methodName.charAt(0) == '_') return false;

        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Object.class) return false;

        if (declaringClass == serviceClass) return true;

        Package declaringPackage = declaringClass.getPackage();
        if (declaringPackage == null) return false;

        String packageName = declaringPackage.getName();
        return packageName.contains(ENTITY_PROCESSOR_SERVICE_PACKAGE) || packageName.startsWith(COMMONS_PACKAGE_PREFIX);
    }
}
