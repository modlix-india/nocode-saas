package com.fincity.saas.commons.functions;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.functions.annotations.IgnoreGeneration;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
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

    private static final List<String> MANDATORY_CLASS_NAMES = List.of(
            "com.fincity.saas.commons.model.condition.FilterCondition",
            "com.fincity.saas.commons.model.condition.ComplexCondition");

    private static final List<String> EXPLICIT_SCHEMA_CLASSES = List.of(
            "com.fincity.saas.commons.model.condition.AbstractCondition", "com.fincity.saas.commons.model.Query");
    private static final Schema PAGEABLE_SCHEMA = createPageableSchema();
    private final ClassSchema classSchema;

    public ServiceSchemaGenerator() {
        this.classSchema = ClassSchema.getInstance();
    }

    public ServiceSchemaGenerator(ClassSchema classSchema) {
        this.classSchema = classSchema;
    }

    private static Schema createPageableSchema() {
        return Schema.ofObject("Pageable")
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
                                                                .setDescription("Whether to ignore case when sorting")))
                                        .setDescription("Sort configuration")));
    }

    public Map<String, Schema> generateSchemas(List<Object> services) {
        Set<Class<?>> pojoClasses = new HashSet<>();
        Set<Class<?>> processedServiceClasses = new HashSet<>();

        if (services != null) {
            for (Object service : services) {
                if (service != null) scanService(service.getClass(), pojoClasses, processedServiceClasses);
            }
        }

        this.collectMandatoryClasses(pojoClasses);

        Map<String, Schema> schemas = this.generateSchemasForPojos(pojoClasses);

        this.addExplicitSchemas(schemas);

        this.addStandardSchemas(schemas);

        return schemas;
    }

    private void scanService(Class<?> serviceClass, Set<Class<?>> pojoClasses, Set<Class<?>> processedServiceClasses) {
        if (!processedServiceClasses.add(serviceClass)) return;
        if (serviceClass.isAnnotationPresent(IgnoreGeneration.class)) return;

        for (Method method : serviceClass.getMethods()) {
            if (!isValidMethod(method, serviceClass)) continue;

            for (Class<?> paramType : method.getParameterTypes()) this.collectPojoClasses(paramType, pojoClasses);

            this.collectPojoClasses(unwrapReactiveType(method), pojoClasses);
        }
    }

    private Class<?> unwrapReactiveType(Method method) {
        Class<?> returnType = method.getReturnType();

        if (Mono.class.isAssignableFrom(returnType) || Flux.class.isAssignableFrom(returnType)) {
            Type genericReturnType = method.getGenericReturnType();
            if (genericReturnType instanceof ParameterizedType paramType) {
                Type actualType = paramType.getActualTypeArguments()[0];
                if (actualType instanceof Class<?> clazz) return clazz;
            }
            return null;
        }

        return returnType;
    }

    private void collectMandatoryClasses(Set<Class<?>> pojoClasses) {
        for (String className : MANDATORY_CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                collectPojoClasses(clazz, pojoClasses);
            } catch (ClassNotFoundException e) {
                LOGGER.warn("Mandatory class not found: {}", e.getMessage());
            }
        }
    }

    private Map<String, Schema> generateSchemasForPojos(Set<Class<?>> pojoClasses) {
        Map<String, Schema> schemas = new HashMap<>();

        for (Class<?> pojoClass : pojoClasses) {
            if (!Modifier.isAbstract(pojoClass.getModifiers()) && classSchema.shouldIncludeClass(pojoClass)) {
                try {
                    String namespace = classSchema.getNamespaceForClass(pojoClass);
                    String name = pojoClass.getSimpleName();
                    Schema schema = classSchema.generateSchemaForClass(pojoClass, new HashSet<>(), namespace, name);
                    schemas.put(namespace + "." + name, schema);
                } catch (Exception e) {
                    LOGGER.error("Failed to generate schema for class {}: {}", pojoClass.getName(), e.getMessage(), e);
                }
            }
        }
        return schemas;
    }

    private void addExplicitSchemas(Map<String, Schema> schemas) {
        for (String className : EXPLICIT_SCHEMA_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className);
                Schema schema = classSchema.generateSchemaForClass(clazz);
                if (schema != null) {
                    String namespace = classSchema.getNamespaceForClass(clazz);
                    schemas.put(namespace + "." + clazz.getSimpleName(), schema);
                }
            } catch (ClassNotFoundException e) {
                LOGGER.warn("Explicit schema class not found: {}", e.getMessage());
            }
        }
    }

    private void addStandardSchemas(Map<String, Schema> schemas) {
        try {
            Class.forName("org.springframework.data.domain.Pageable");
            schemas.put("Commons.Pageable", PAGEABLE_SCHEMA);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Pageable class not found, skipping schema generation: {}", e.getMessage());
        }
    }

    private void collectPojoClasses(Class<?> type, Set<Class<?>> pojoClasses) {
        if (type == null) return;

        if (type.isPrimitive() || classSchema.isPrimitiveType(type)) return;

        if (type.isArray()) {
            collectPojoClasses(type.getComponentType(), pojoClasses);
            return;
        }

        if (Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Mono.class.isAssignableFrom(type)
                || Flux.class.isAssignableFrom(type)) return;

        if (type.getPackage() == null) return;
        if (!classSchema.shouldIncludeClass(type)) return;

        if (classSchema.isRelevantPackage(type) && pojoClasses.add(type))
            classSchema.collectNestedTypes(type, pojoClasses);
    }

    private boolean isValidMethod(Method method, Class<?> serviceClass) {
        if (method.isAnnotationPresent(IgnoreGeneration.class)) return false;

        if (!Modifier.isPublic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) return false;

        String methodName = method.getName();
        if (methodName.charAt(0) == '_' || EXCLUDED_METHOD_NAMES.contains(methodName)) return false;

        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Object.class) return false;
        if (declaringClass == serviceClass) return true;

        Package declaringPackage = declaringClass.getPackage();
        if (declaringPackage == null) return false;

        String packageName = declaringPackage.getName();
        return packageName.contains(ENTITY_PROCESSOR_SERVICE_PACKAGE) || packageName.startsWith(COMMONS_PACKAGE_PREFIX);
    }
}
