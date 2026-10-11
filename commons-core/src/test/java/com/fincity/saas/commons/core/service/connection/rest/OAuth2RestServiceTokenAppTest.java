package com.fincity.saas.commons.core.service.connection.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

/**
 * An OAuth2 call must look its token up under the app that owns the connection. A KIRun step in one app that
 * names another app's connection (leadzumptwo calling marketingai's META_API) used to look under the caller's
 * URL app, where the consent flow never stored a token, and failed with "Integration token unavailable".
 */
class OAuth2RestServiceTokenAppTest {

	private static ContextAuthentication ctx(String urlAppCode) {
		ContextAuthentication ca = new ContextAuthentication();
		ca.setUrlAppCode(urlAppCode);
		return ca;
	}

	private static Connection conn(String appCode) {
		Connection c = new Connection();
		c.setAppCode(appCode);
		return c;
	}

	@Test
	void otherAppsConnectionUsesThatAppsTokens() {
		assertEquals("marketingai", OAuth2RestService.tokenAppCode(conn("marketingai"), ctx("leadzumptwo")));
	}

	@Test
	void ownAppsConnectionIsUnchanged() {
		assertEquals("leadzump", OAuth2RestService.tokenAppCode(conn("leadzump"), ctx("leadzump")));
	}

	@Test
	void connectionWithoutAppFallsBackToUrlApp() {
		assertEquals("sitezump", OAuth2RestService.tokenAppCode(conn(null), ctx("sitezump")));
		assertEquals("sitezump", OAuth2RestService.tokenAppCode(conn(" "), ctx("sitezump")));
		assertEquals("sitezump", OAuth2RestService.tokenAppCode(null, ctx("sitezump")));
	}

	@Test
	void nothingKnown() {
		assertNull(OAuth2RestService.tokenAppCode(null, null));
	}
}
