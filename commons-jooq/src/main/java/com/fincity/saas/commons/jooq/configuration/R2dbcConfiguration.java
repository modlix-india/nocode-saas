package com.fincity.saas.commons.jooq.configuration;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

public class R2dbcConfiguration extends AbstractR2dbcConfiguration {

	private final String url;
	private final String username;
	private final String password;

	public R2dbcConfiguration(String url, String username, String password) {
		this.url = url;
		this.username = username;
		this.password = password;
	}

	@Override
	public ConnectionFactory connectionFactory() {

		ConnectionFactoryOptions.Builder props = ConnectionFactoryOptions.parse(url)
				.mutate();

		return ConnectionFactories.get(props
				.option(ConnectionFactoryOptions.DRIVER, this.getDriver())
				.option(ConnectionFactoryOptions.PROTOCOL, this.getProtocol())
				.option(ConnectionFactoryOptions.USER, username)
				.option(ConnectionFactoryOptions.PASSWORD, password)
				.build());
	}

	public DSLContext context() {
		return DSL.using(
				new ConnectionPool(
						ConnectionPoolConfiguration.builder(connectionFactory()).build()));
	}

	private String getProtocol() {
		if (url != null && url.startsWith("r2dbc:")) {
			String[] parts = url.split(":");
			return parts[1];
		}

		return "mysql";
	}

	private String getDriver() {
		return "pool";
	}
}
