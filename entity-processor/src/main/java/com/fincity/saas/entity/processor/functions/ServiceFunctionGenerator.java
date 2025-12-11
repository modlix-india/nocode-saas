package com.fincity.saas.entity.processor.functions;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
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
    private static final Set<String> EXCLUDED_METHOD_NAMES = Set.of("equals", "hashCode", "toString", "getClass");
    private static final String SERVICE_SUFFIX = "Service";
    private static final String NAMESPACE_PREFIX = "EntityProcessor.";
    private static final String ENTITY_PROCESSOR_SERVICE_PACKAGE = "entity.processor.service";
    private static final String COMMONS_PACKAGE_PREFIX = "com.fincity.saas.commons";

    private final Gson gson;

    public ServiceFunctionGenerator(Gson gson, ProcessorMessageResourceService messageService) {
        this.gson = gson;
        // messageService kept in constructor signature for API compatibility but not used for performance
    }

    public List<ReactiveFunction> generateFunctions(Object serviceInstance) {
        Class<?> serviceClass = serviceInstance.getClass();
        String namespace = getNamespace(serviceInstance, serviceClass);

        Method[] methods = serviceClass.getMethods();
        List<ReactiveFunction> functions = new ArrayList<>(Math.max(16, methods.length / 2));
        Set<String> processedSignatures = HashSet.newHashSet(Math.max(16, methods.length / 2));

        List<Method> validMethods = new ArrayList<>();
        Map<String, Integer> methodNameCounts = new HashMap<>();
        for (Method method : methods) {
            if (isValidMethod(method, serviceClass)) {
                String signature = createMethodSignature(method);
                if (processedSignatures.add(signature)) {
                    validMethods.add(method);
                    methodNameCounts.merge(method.getName(), 1, Integer::sum);
                }
            }
        }

        for (Method method : validMethods) {
            boolean isOverloaded = methodNameCounts.get(method.getName()) > 1;
            String functionName = generateFunctionName(method, isOverloaded);
            try {
                ReactiveFunction function =
                        new DynamicServiceFunction(serviceInstance, method, namespace, functionName, gson);
                functions.add(function);
            } catch (Exception e) {
                String errorDetails = String.format(
                        "Failed to create function for method %s in class %s: %s",
                        method.getName(), serviceClass.getSimpleName(), e.getMessage());
                String messageKey = ProcessorMessageResourceService.INVALID_PARAMETERS;
                LOGGER.error("[{}] Error creating function: {}", messageKey, errorDetails, e);
            }
        }

        return functions;
    }

    private boolean isValidMethod(Method method, Class<?> serviceClass) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            return false;
        }

        String methodName = method.getName();
        if (EXCLUDED_METHOD_NAMES.contains(methodName) || methodName.charAt(0) == '_') {
            return false;
        }

        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Object.class) {
            return false;
        }

        if (declaringClass == serviceClass) {
            return true;
        }

        Package declaringPackage = declaringClass.getPackage();
        if (declaringPackage == null) {
            return false;
        }

        String packageName = declaringPackage.getName();
        return packageName.contains(ENTITY_PROCESSOR_SERVICE_PACKAGE) || packageName.startsWith(COMMONS_PACKAGE_PREFIX);
    }

    private String createMethodSignature(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return method.getName() + "()";
        }

        StringBuilder signature = new StringBuilder(method.getName().length() + 2 + paramTypes.length * 20);
        signature.append(method.getName()).append('(');
        signature.append(paramTypes[0].getName());
        for (int i = 1; i < paramTypes.length; i++) {
            signature.append(',').append(paramTypes[i].getName());
        }
        signature.append(')');
        return signature.toString();
    }

    private String getNamespace(Object serviceInstance, Class<?> serviceClass) {
        if (serviceInstance instanceof IEntitySeries entitySeriesService) {
            EntitySeries entitySeries = entitySeriesService.getEntitySeries();
            if (entitySeries != null && entitySeries != EntitySeries.XXX) {
                return NAMESPACE_PREFIX + entitySeries.getPrefix();
            }
        }

        String packageName =
                serviceClass.getPackage() != null ? serviceClass.getPackage().getName() : "";
        if (packageName.contains("entity.processor.service")) {
            return NAMESPACE_PREFIX + getServiceName(serviceClass);
        }
        return "EntityProcessor";
    }

    private String getServiceName(Class<?> serviceClass) {
        String className = serviceClass.getSimpleName();
        if (className.endsWith(SERVICE_SUFFIX)) {
            return className.substring(0, className.length() - SERVICE_SUFFIX.length());
        }
        return className;
    }

    private String generateFunctionName(Method method, boolean isOverloaded) {
        String baseName = capitalizeFirst(method.getName());

        if (!isOverloaded) {
            return baseName;
        }

        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return baseName;
        }

        StringBuilder suffix = new StringBuilder(paramTypes.length * 15);
        for (Class<?> type : paramTypes) {
            suffix.append(getTypeAbbreviation(type));
        }

        return baseName + suffix;
    }

    private String getTypeAbbreviation(Class<?> type) {
        if (type == int.class) return "Integer";
        if (type == long.class) return "Long";
        if (type == double.class) return "Double";
        if (type == float.class) return "Float";
        if (type == boolean.class) return "Boolean";
        if (type == byte.class) return "Byte";
        if (type == short.class) return "Short";
        if (type == char.class) return "Character";

        return type.getSimpleName();
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (str.length() == 1) {
            return str.toUpperCase();
        }
        char[] chars = str.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }
}
