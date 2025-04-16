package com.fincity.sass.worker.configuration;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
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

import java.util.Properties;

@Configuration
@Slf4j
public class QuartzConfiguration {

    @Value("${worker.qdb.url}")
    private String dbURL;

    @Value("${worker.qdb.username}")
    private String userName;

    @Value("${worker.qdb.password}")
    private String password;

    @Primary
    @Bean(name = "defaultSchedulerFactory")
    public SchedulerFactoryBean defaultSchedulerFactoryBean(ApplicationContext applicationContext) {
        return createSchedulerFactory(applicationContext, "defaultScheduler", 5, true, 60000);
    }

    @Bean(name = "defaultQuartzScheduler")
    public Scheduler defaultScheduler(@Qualifier("defaultSchedulerFactory") SchedulerFactoryBean factory) throws SchedulerException {
        return factory.getScheduler();
    }

    public SchedulerFactoryBean createSchedulerFactory(
            ApplicationContext applicationContext, 
            String schedulerName, 
            int threadCount,
            boolean isClustered,
            long misfireThreshold) {
        
        SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        Properties quartzProperties = new Properties();

        quartzProperties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        quartzProperties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        quartzProperties.setProperty("org.quartz.jobStore.useProperties", "true");
        quartzProperties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        quartzProperties.setProperty("org.quartz.jobStore.isClustered", String.valueOf(isClustered));
        quartzProperties.setProperty("org.quartz.jobStore.misfireThreshold", String.valueOf(misfireThreshold));
        quartzProperties.setProperty("org.quartz.jobStore.dataSource", "quartzDS");
        
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.provider", "hikaricp");
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.driver", "com.mysql.cj.jdbc.Driver");
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.URL", dbURL);
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.user", userName);
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.password", password);
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.maxConnections", "5");
        
        quartzProperties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        quartzProperties.setProperty("org.quartz.threadPool.threadCount", String.valueOf(threadCount));
        
        schedulerFactory.setQuartzProperties(quartzProperties);
        schedulerFactory.setSchedulerName(schedulerName);
        
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        schedulerFactory.setJobFactory(jobFactory);

        return schedulerFactory;
    }

    private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
        private AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(final ApplicationContext context) {
            beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
            final Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        }
    }
}