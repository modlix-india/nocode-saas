package com.fincity.saas.entity.processor.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.entity.processor.functions.ServiceSchemaGenerator;
import com.google.gson.Gson;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ProcessorSchemaService
        implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private final Map<String, ReactiveRepository<Schema>> schemaRepositoryCache = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private ApplicationContext applicationContext;
    private Map<String, Schema> generatedSchemas = new HashMap<>();

    @Autowired
    private Gson gson;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Only initialize once, and only if this is the root application context
        if (event.getApplicationContext().getParent() == null && initialized.compareAndSet(false, true)) {
            init();
        }
    }

    private void init() {
        // Collect all service beans from the entity.processor.service package
        List<Object> services = new ArrayList<>();
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();

            // Only process beans that:
            // 1. Are not ProcessorMessageResourceService or ProcessorFunctionService or
            // ProcessorSchemaService
            // 2. Are annotated with @Service
            // 3. Are in entity.processor.service package but not in base subpackage
            if (!(bean instanceof ProcessorMessageResourceService
                    || bean instanceof ProcessorFunctionService
                    || bean instanceof ProcessorSchemaService)
                    && beanClass.isAnnotationPresent(Service.class)) {
                String packageName = beanClass.getPackage() != null ? beanClass.getPackage().getName() : "";
                if (packageName.contains("entity.processor.service") && !packageName.contains("base")) {
                    services.add(bean);
                }
            }
        }

        // Generate schemas from all service POJOs
        ServiceSchemaGenerator generator = new ServiceSchemaGenerator(gson);
        this.generatedSchemas = generator.generateSchemas(services);
    }

    public Map<String, Schema> getGeneratedSchemas() {
        return new HashMap<>(generatedSchemas);
    }

    public Mono<ReactiveRepository<Schema>> getSchemaRepository(String appCode, String clientCode) {
        String cacheKey = appCode + " - " + clientCode;

        ReactiveRepository<Schema> cachedRepo = schemaRepositoryCache.get(cacheKey);
        if (cachedRepo != null) {
            return Mono.just(cachedRepo);
        }

        // Create a new repository for this appCode/clientCode combination
        ProcessorSchemaRepository processorRepo = new ProcessorSchemaRepository(generatedSchemas);
        schemaRepositoryCache.put(cacheKey, processorRepo);
        return Mono.just(processorRepo);
    }

    private static class ProcessorSchemaRepository implements ReactiveRepository<Schema> {

        private final Map<String, Schema> schemaMap;
        private final List<String> filterableNames;

        public ProcessorSchemaRepository(Map<String, Schema> schemas) {
            this.schemaMap = new HashMap<>(schemas);
            this.filterableNames = new ArrayList<>(schemaMap.keySet());
        }

        @Override
        public Mono<Schema> find(String namespace, String name) {
            String fullName = namespace + "." + name;
            return Mono.justOrEmpty(schemaMap.get(fullName));
        }

        @Override
        public Flux<String> filter(String name) {
            final String filterName = name == null ? "" : name.toLowerCase();
            return Flux.fromIterable(filterableNames)
                    .filter(fullName -> fullName.toLowerCase().contains(filterName));
        }
    }
}
