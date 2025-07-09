package com.fincity.saas.entity.processor.util;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextAccessor implements ApplicationContextAware {

    private static final AtomicReference<ApplicationContext> contextRef = new AtomicReference<>();

    public static <T> T getBean(Class<T> clazz) {
        ApplicationContext context = contextRef.get();
        if (context == null) throw new IllegalStateException("ApplicationContext not initialized");

        return context.getBean(clazz);
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        contextRef.set(ctx);
    }
}
