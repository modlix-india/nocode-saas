package com.fincity.sass.worker.configuration;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.SchedulerRepository;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.util.Assert;

import java.util.Properties;

@Configuration
@Slf4j
public class QuartzConfiguration {

    // Quartz property prefixes
    private static final String QUARTZ_PREFIX = "org.quartz.";
    private static final String JOBSTORE_PREFIX = QUARTZ_PREFIX + "jobStore.";
    private static final String DATASOURCE_PREFIX = QUARTZ_PREFIX + "dataSource.quartzDS.";
    private static final String THREADPOOL_PREFIX = QUARTZ_PREFIX + "threadPool.";

    // Default values
    private static final String DEFAULT_SCHEDULER_NAME = "defaultScheduler";
    private static final int DEFAULT_THREAD_COUNT = 5;
    private static final boolean DEFAULT_CLUSTERED = false;
    private static final long DEFAULT_MISFIRE_THRESHOLD = 60000;
    private static final String DEFAULT_MAX_CONNECTIONS = "5";

    // JobStore class names
    private static final String JOBSTORE_CLASS = "org.quartz.impl.jdbcjobstore.JobStoreTX";
    private static final String JOBSTORE_DELEGATE_CLASS = "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
    private static final String THREADPOOL_CLASS = "org.quartz.simpl.SimpleThreadPool";

    // DataSource properties
    private static final String DATASOURCE_PROVIDER = "hikaricp";
    private static final String DATASOURCE_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DATASOURCE_NAME = "quartzDS";

    @Value("${worker.qdb.url}")
    private String dbURL;

    @Value("${worker.qdb.username}")
    private String userName;

    @Value("${worker.qdb.password}")
    private String password;


    @Bean
    public SchedulerRepository schedulerRepository() {
        return SchedulerRepository.getInstance();
    }

    /**
     * Creates the default scheduler factory bean with predefined configuration.
     * 
     * @param applicationContext the Spring application context
     * @return the default scheduler factory bean
     */
    @Primary
    @Bean(name = "defaultSchedulerFactory")
    public SchedulerFactoryBean defaultSchedulerFactoryBean(ApplicationContext applicationContext) {
        log.debug("Creating default scheduler factory with name: {}", DEFAULT_SCHEDULER_NAME);
        return createSchedulerFactory(applicationContext, DEFAULT_SCHEDULER_NAME);
    }

    /**
     * Creates the default Quartz scheduler from the factory.
     * 
     * @param factory the scheduler factory bean
     * @return the Quartz scheduler
     * @throws SchedulerException if an error occurs while getting the scheduler
     */
    @Bean(name = "defaultQuartzScheduler")
    public Scheduler defaultScheduler(@Qualifier("defaultSchedulerFactory") SchedulerFactoryBean factory) throws SchedulerException {
        log.debug("Getting default Quartz scheduler from factory");
        return factory.getScheduler();
    }

    /**
     * Creates a Quartz scheduler factory with the specified configuration.
     *
     * @param applicationContext the Spring application context
     * @param schedulerName the name of the scheduler
     * @return a configured SchedulerFactoryBean
     */
    public SchedulerFactoryBean createSchedulerFactory(ApplicationContext applicationContext, String schedulerName) {

        // Validate parameters
        Assert.notNull(applicationContext, "ApplicationContext must not be null");
        Assert.hasText(schedulerName, "Scheduler name must not be empty");

        log.debug("Creating scheduler factory: name={}", schedulerName);

        SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        Properties quartzProperties = new Properties();

        // JobStore properties
        quartzProperties.setProperty(JOBSTORE_PREFIX + "class", JOBSTORE_CLASS);
        quartzProperties.setProperty(JOBSTORE_PREFIX + "driverDelegateClass", JOBSTORE_DELEGATE_CLASS);
        quartzProperties.setProperty(JOBSTORE_PREFIX + "useProperties", "true");
        quartzProperties.setProperty(JOBSTORE_PREFIX + "tablePrefix", "QRTZ_");
        quartzProperties.setProperty(JOBSTORE_PREFIX + "isClustered", String.valueOf(DEFAULT_CLUSTERED));
        quartzProperties.setProperty(JOBSTORE_PREFIX + "misfireThreshold", String.valueOf(DEFAULT_MISFIRE_THRESHOLD));
        quartzProperties.setProperty(JOBSTORE_PREFIX + "dataSource", DATASOURCE_NAME);

        // DataSource properties
        quartzProperties.setProperty(DATASOURCE_PREFIX + "provider", DATASOURCE_PROVIDER);
        quartzProperties.setProperty(DATASOURCE_PREFIX + "driver", DATASOURCE_DRIVER);
        quartzProperties.setProperty(DATASOURCE_PREFIX + "URL", dbURL);
        quartzProperties.setProperty(DATASOURCE_PREFIX + "user", userName);
        quartzProperties.setProperty(DATASOURCE_PREFIX + "password", password);
        quartzProperties.setProperty(DATASOURCE_PREFIX + "maxConnections", DEFAULT_MAX_CONNECTIONS);

        // ThreadPool properties
        quartzProperties.setProperty(THREADPOOL_PREFIX + "class", THREADPOOL_CLASS);
        quartzProperties.setProperty(THREADPOOL_PREFIX + "threadCount", String.valueOf(DEFAULT_THREAD_COUNT));

        schedulerFactory.setQuartzProperties(quartzProperties);
        schedulerFactory.setSchedulerName(schedulerName);

        // Create and configure job factory
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        schedulerFactory.setJobFactory(jobFactory);

        log.debug("Scheduler factory created successfully: {}", schedulerName);
        return schedulerFactory;
    }

    /**
     * Custom JobFactory that autowires job instances with Spring beans.
     * This allows Quartz jobs to use Spring dependency injection.
     */
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
