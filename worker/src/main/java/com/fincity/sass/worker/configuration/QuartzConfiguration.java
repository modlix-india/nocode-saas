package com.fincity.sass.worker.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class QuartzConfiguration {

    @Value("${worker.qdb.url}")
    private String dbURL;

    @Value("${worker.qdb.username}")
    private String userName;

    @Value("${worker.qdb.password}")
    private String password;

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(ApplicationContext applicationContext) {  // Removed DataSource parameter
        SchedulerFactoryBean schedulerFactory = new SchedulerFactoryBean();
        
        Properties quartzProperties = new Properties();
        // JobStore configuration
        quartzProperties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        quartzProperties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        quartzProperties.setProperty("org.quartz.jobStore.useProperties", "true");
        quartzProperties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        quartzProperties.setProperty("org.quartz.jobStore.isClustered", "true");
        quartzProperties.setProperty("org.quartz.jobStore.misfireThreshold", "60000");
        quartzProperties.setProperty("org.quartz.jobStore.dataSource", "quartzDS");
        
        // Add non-managed DataSource configuration
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.provider", "hikaricp");
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.driver", "com.mysql.cj.jdbc.Driver");
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.URL", dbURL);
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.user", userName);
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.password", password);
        quartzProperties.setProperty("org.quartz.dataSource.quartzDS.maxConnections", "5");
        
        // Thread Pool Configuration
        quartzProperties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        quartzProperties.setProperty("org.quartz.threadPool.threadCount", "5");
        
        // Remove DataSource injection
        // schedulerFactory.setDataSource(quartzDataSource);
        schedulerFactory.setQuartzProperties(quartzProperties);
        schedulerFactory.setSchedulerName("quartzScheduler");
        
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        schedulerFactory.setJobFactory(jobFactory);

        return schedulerFactory;
    }

    @Bean
    public Scheduler scheduler(SchedulerFactoryBean factory) throws SchedulerException {
        return factory.getScheduler();
    }
}