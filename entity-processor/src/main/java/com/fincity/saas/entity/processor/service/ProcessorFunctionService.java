package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.entity.processor.functions.ProcessorFunctionRepository;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProcessorFunctionService
        implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private final AtomicReference<ReactiveHybridRepository<ReactiveFunction>> processorFunctionRepository =
            new AtomicReference<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private ApplicationContext applicationContext;

    @Autowired
    private Gson gson;

    @Autowired
    private ProcessorMessageResourceService messageService;

    /**
     * Called by Spring during bean initialization to inject the ApplicationContext.
     * This happens early, but we don't use it until onApplicationEvent() is called.
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Called by Spring after the application context is fully refreshed and all beans are created.
     * This is the safe time to iterate through all beans and build the function repository.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Only initialize once, and only if this is the root application context
        // (to avoid double initialization in web apps with parent/child contexts)
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
            // 1. Are not ProcessorMessageResourceService or ProcessorFunctionService
            // 2. Are annotated with @Service
            // 3. Are in entity.processor.service package but not in base subpackage
            if (!(bean instanceof ProcessorMessageResourceService || bean instanceof ProcessorFunctionService)
                    && beanClass.isAnnotationPresent(Service.class)) {
                String packageName = beanClass.getPackage() != null ? beanClass.getPackage().getName() : "";
                if (packageName.contains("entity.processor.service") && !packageName.contains("base")) {
                    services.add(bean);
                }
            }
        }

        ReactiveHybridRepository<ReactiveFunction> repository = new ReactiveHybridRepository<>(
                new ProcessorFunctionRepository(
                        new ProcessorFunctionRepository.ProcessorFunctionRepositoryBuilder()
                                .setServices(services)
                                .setGson(gson)
                                .setMessageService(messageService)));
        this.processorFunctionRepository.set(repository);
    }

    public ReactiveRepository<ReactiveFunction> getFunctionRepository() {
        ReactiveHybridRepository<ReactiveFunction> repository = processorFunctionRepository.get();
        if (repository == null) {
            throw new IllegalStateException("ProcessorFunctionRepository has not been initialized yet");
        }
        return repository;
    }

    public Mono<FunctionOutput> execute(
            String namespace,
            String name,
            Map<String, JsonElement> arguments) {
        ReactiveHybridRepository<ReactiveFunction> repository = processorFunctionRepository.get();
        if (repository == null) {
            return Mono.error(new IllegalStateException("ProcessorFunctionRepository has not been initialized yet"));
        }
        return repository
                .find(namespace, name)
                .flatMap(function -> function.execute(
                        new ReactiveFunctionExecutionParameters(repository, null)
                                .setArguments(arguments)));
    }
}

