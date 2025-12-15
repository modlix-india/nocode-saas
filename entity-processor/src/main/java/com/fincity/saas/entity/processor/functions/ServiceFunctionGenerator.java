package com.fincity.saas.entity.processor.functions;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.functions.anntations.IgnoreServerFunc;
import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceFunctionGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceFunctionGenerator.class);

    private static final Set<String> EXCLUDED_METHOD_NAMES =
            Set.of("equals", "hashCode", "toString", "getClass", "wait", "notify", "notifyAll");

    private static final String SERVICE_SUFFIX = "Service";
    private static final String NAMESPACE_PREFIX = "EntityProcessor.";
    private static final String ENTITY_PROCESSOR_SERVICE_PACKAGE = "entity.processor.service";
    private static final String COMMONS_PACKAGE_PREFIX = "com.fincity.saas.commons";

    private static final Map<Class<?>, String> TYPE_ABBREV_CACHE = new HashMap<>();

    static {
        TYPE_ABBREV_CACHE.put(int.class, "Integer");
        TYPE_ABBREV_CACHE.put(long.class, "Long");
        TYPE_ABBREV_CACHE.put(double.class, "Double");
        TYPE_ABBREV_CACHE.put(float.class, "Float");
        TYPE_ABBREV_CACHE.put(boolean.class, "Boolean");
        TYPE_ABBREV_CACHE.put(byte.class, "Byte");
        TYPE_ABBREV_CACHE.put(short.class, "Short");
        TYPE_ABBREV_CACHE.put(char.class, "Character");
        TYPE_ABBREV_CACHE.put(Integer.class, "Integer");
        TYPE_ABBREV_CACHE.put(Long.class, "Long");
        TYPE_ABBREV_CACHE.put(Double.class, "Double");
        TYPE_ABBREV_CACHE.put(Float.class, "Float");
        TYPE_ABBREV_CACHE.put(Boolean.class, "Boolean");
    }

    private final Gson gson;
    private final Map<String, Schema> schemaMap;

    public ServiceFunctionGenerator(Gson gson) {
        this.gson = gson;
        this.schemaMap = null;
    }

    public ServiceFunctionGenerator(Gson gson, Map<String, Schema> schemaMap) {
        this.gson = gson;
        this.schemaMap = schemaMap;
    }

    public List<ReactiveFunction> generateFunctions(Object serviceInstance) {
        Class<?> serviceClass = serviceInstance.getClass();

        if (serviceClass.isAnnotationPresent(IgnoreServerFunc.class)) return new ArrayList<>();

        String namespace = this.getNamespace(serviceInstance, serviceClass);
        Method[] methods = serviceClass.getMethods();

        Map<String, List<Method>> methodsByName = new HashMap<>();
        int validMethodCount = 0;

        for (Method method : methods) {
            if (this.isValidMethod(method, serviceClass))
                methodsByName
                        .computeIfAbsent(method.getName(), k -> new ArrayList<>())
                        .add(method);
            validMethodCount++;
        }

        List<ReactiveFunction> functions = new ArrayList<>(validMethodCount);

        for (List<Method> overloadedMethods : methodsByName.values()) {
            boolean isOverloaded = overloadedMethods.size() > 1;

            for (Method method : overloadedMethods) {
                try {
                    String functionName = this.generateFunctionName(method, isOverloaded, overloadedMethods);
                    functions.add(new DynamicServiceFunction(
                            serviceInstance, method, namespace, functionName, this.gson, this.schemaMap));
                } catch (Exception e) {
                    LOGGER.error(
                            "Failed to create function for method {} in class {}: {}",
                            method.getName(),
                            serviceClass.getSimpleName(),
                            e.getMessage(),
                            e);
                }
            }
        }

        return functions;
    }

    private boolean isValidMethod(Method method, Class<?> serviceClass) {
        if (method.isAnnotationPresent(IgnoreServerFunc.class)) return false;

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

    private String getNamespace(Object serviceInstance, Class<?> serviceClass) {
        if (serviceInstance instanceof IEntitySeries entitySeriesService) {
            EntitySeries entitySeries = entitySeriesService.getEntitySeries();
            if (entitySeries != null && entitySeries != EntitySeries.XXX)
                return NAMESPACE_PREFIX + entitySeries.getPrefix();
        }

        Package pkg = serviceClass.getPackage();
        if (pkg != null && pkg.getName().contains(ENTITY_PROCESSOR_SERVICE_PACKAGE))
            return NAMESPACE_PREFIX + this.getServiceName(serviceClass);

        return "EntityProcessor";
    }

    private String getServiceName(Class<?> serviceClass) {
        String className = serviceClass.getSimpleName();
        if (className.endsWith(SERVICE_SUFFIX))
            return className.substring(0, className.length() - SERVICE_SUFFIX.length());
        return className;
    }

    private String generateFunctionName(Method method, boolean isOverloaded, List<Method> overloadedMethods) {
        String baseName = this.capitalizeFirst(method.getName());

        if (!isOverloaded) return baseName;

        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) return baseName;

        Set<Integer> differingPositions = this.findDifferingParameterPositions(method, overloadedMethods, paramTypes);

        if (differingPositions.isEmpty()) return baseName;

        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < paramTypes.length; i++) {
            if (differingPositions.contains(i)) suffix.append(this.getTypeAbbreviation(paramTypes[i]));
        }

        return baseName + suffix;
    }

    private Set<Integer> findDifferingParameterPositions(
            Method currentMethod, List<Method> overloadedMethods, Class<?>[] currentParamTypes) {
        Set<Integer> differingPositions = new HashSet<>();

        for (Method otherMethod : overloadedMethods) {
            if (otherMethod == currentMethod) continue;

            Class<?>[] otherParamTypes = otherMethod.getParameterTypes();
            int maxLen = Math.max(currentParamTypes.length, otherParamTypes.length);

            for (int i = 0; i < maxLen; i++) {
                if (differingPositions.contains(i)) continue;

                boolean existsInCurrent = i < currentParamTypes.length;
                boolean existsInOther = i < otherParamTypes.length;

                if (existsInCurrent != existsInOther || !currentParamTypes[i].equals(otherParamTypes[i]))
                    differingPositions.add(i);
            }
        }

        return differingPositions;
    }

    private String getTypeAbbreviation(Class<?> type) {
        return TYPE_ABBREV_CACHE.computeIfAbsent(type, Class::getSimpleName);
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        char first = str.charAt(0);
        if (Character.isUpperCase(first)) return str;

        return Character.toUpperCase(first) + str.substring(1);
    }
}
