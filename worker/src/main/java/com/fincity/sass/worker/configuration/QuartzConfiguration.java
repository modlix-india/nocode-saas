package com.fincity.sass.worker.configuration;

import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
@Slf4j
public class QuartzConfiguration {

    @Value("${worker.quartz.virtual-threads:true}")
    private boolean useVirtualThreads;

    @Bean
    public AutowiringSpringBeanJobFactory quartzJobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBeanCustomizer quartzSchedulerCustomizer(AutowiringSpringBeanJobFactory quartzJobFactory) {
        return factory -> {
            factory.setJobFactory(quartzJobFactory);
            if (useVirtualThreads) {
                factory.setTaskExecutor(new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor()));
                log.info("Quartz configured to use virtual threads for job execution");
            }
        };
    }

    private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
        private AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(final ApplicationContext context) {
            this.beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
            final Object job = super.createJobInstance(bundle);
            this.beanFactory.autowireBean(job);
            return job;
        }
    }
}
