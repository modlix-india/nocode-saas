package com.fincity.saas.files;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(order = Ordered.HIGHEST_PRECEDENCE)
@EnableCaching
@ComponentScan(basePackages = "com.fincity")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class FilesApplication {

	public static void main(String[] args) {
		SpringApplication.run(FilesApplication.class, args);
	}
	
}
