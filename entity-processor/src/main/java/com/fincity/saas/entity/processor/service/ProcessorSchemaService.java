package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.functions.ClassSchema;
import com.fincity.saas.commons.functions.IRepositoryProvider;
import com.fincity.saas.commons.functions.ServiceSchemaGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ProcessorSchemaService implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private final Map<String, ReactiveRepository<Schema>> schemaRepositoryCache = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ClassSchema classSchema = ClassSchema.getInstance(ClassSchema.PackageConfig.forEntityProcessor());
    private ApplicationContext applicationContext;
    private Map<String, Schema> generatedSchemas = new HashMap<>();

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
                String packageName =
                        beanClass.getPackage() != null ? beanClass.getPackage().getName() : "";
                if (packageName.contains("entity.processor.service") && !packageName.contains("base")) {
                    services.add(bean);
                }
            }
        }

        // Generate schemas from all service POJOs
        ServiceSchemaGenerator generator = new ServiceSchemaGenerator(classSchema);
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

        // Lazy lookup of IRepositoryProvider beans to avoid circular dependency
        Map<String, IRepositoryProvider> repositoryProviders =
                applicationContext.getBeansOfType(IRepositoryProvider.class);

        return Flux.fromIterable(repositoryProviders.values())
                .flatMap(provider -> provider.getSchemaRepository(processorRepo, appCode, clientCode))
                .collectList()
                .map(repos -> {
                    @SuppressWarnings("unchecked")
                    ReactiveRepository<Schema>[] reposArray = new ReactiveRepository[repos.size() + 1];
                    for (int i = 0; i < repos.size(); i++) {
                        reposArray[i] = repos.get(i);
                    }
                    reposArray[repos.size()] = processorRepo;
                    ReactiveRepository<Schema> finRepo = new ReactiveHybridRepository<>(reposArray);
                    schemaRepositoryCache.put(cacheKey, finRepo);
                    return finRepo;
                });
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
