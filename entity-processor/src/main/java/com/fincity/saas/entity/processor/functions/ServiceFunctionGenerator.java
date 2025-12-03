package com.fincity.saas.entity.processor.functions;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.enums.IEntitySeries;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.google.gson.Gson;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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
        String serviceName = getServiceName(serviceClass);

        // Get all public methods including inherited ones
        Method[] methods = serviceClass.getMethods();
        // Pre-size list with estimated capacity (most services have 10-30 public methods)
        List<ReactiveFunction> functions = new ArrayList<>(Math.max(16, methods.length / 2));
        // Use Set to track method signatures to avoid duplicates (overridden methods)
        Set<String> processedSignatures = new HashSet<>(Math.max(16, methods.length / 2));

        for (Method method : methods) {
            if (isValidMethod(method, serviceClass)) {
                // Create a unique signature: methodName + parameter types
                String signature = createMethodSignature(method);
                if (processedSignatures.add(signature)) {
                    String functionName = serviceName + "_" + capitalizeFirst(method.getName());
                    try {
                        ReactiveFunction function =
                                new DynamicServiceFunction(serviceInstance, method, namespace, functionName, gson);
                        functions.add(function);
                    } catch (Exception e) {
                        // Log error directly (non-reactive) for better performance
                        String errorDetails = String.format(
                                "Failed to create function for method %s in class %s: %s",
                                method.getName(),
                                serviceClass.getSimpleName(),
                                e.getMessage());
                        // Get localized message key for context, but log synchronously
                        String messageKey = ProcessorMessageResourceService.INVALID_PARAMETERS;
                        LOGGER.error("[{}] Error creating function: {}", messageKey, errorDetails, e);
                    }
                }
            }
        }

        return functions;
    }

    private boolean isValidMethod(Method method, Class<?> serviceClass) {
        // Fast path: check modifiers first (most common rejection)
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            return false;
        }

        // Fast path: check method name early
        String methodName = method.getName();
        if (EXCLUDED_METHOD_NAMES.contains(methodName) || methodName.charAt(0) == '_') {
            return false;
        }

        // Exclude Object class methods
        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Object.class) {
            return false;
        }

        // Fast path: if declared in service class itself, accept immediately
        if (declaringClass == serviceClass) {
            return true;
        }

        // Check package for parent class methods
        Package declaringPackage = declaringClass.getPackage();
        if (declaringPackage == null) {
            return false; // No package means framework class
        }

        String packageName = declaringPackage.getName();
        // Include methods from entity.processor.service or commons packages
        return packageName.contains(ENTITY_PROCESSOR_SERVICE_PACKAGE)
                || packageName.startsWith(COMMONS_PACKAGE_PREFIX);
    }

    private String createMethodSignature(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            return method.getName() + "()";
        }

        // Estimate capacity: method name + "()" + average class name length * param count
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

        String packageName = serviceClass.getPackage() != null ? serviceClass.getPackage().getName() : "";
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

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // Fast path for single character
        if (str.length() == 1) {
            return str.toUpperCase();
        }
        // Use char array manipulation for better performance
        char[] chars = str.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    public Map.Entry<String, ReactiveFunction> generateFunctionEntry(Object serviceInstance, Method method) {
        Class<?> serviceClass = serviceInstance.getClass();
        String namespace = getNamespace(serviceInstance, serviceClass);
        String serviceName = getServiceName(serviceClass);
        String functionName = serviceName + "_" + capitalizeFirst(method.getName());

        ReactiveFunction function =
                new DynamicServiceFunction(serviceInstance, method, namespace, functionName, gson);
        return Map.entry(namespace + "." + functionName, function);
    }
}
