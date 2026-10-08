package com.modlix.saas.files.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;

/**
 * The first folder after the client code decides special access (_withInClient and friends).
 * It was searched for from (client code length + 1) in a string that already starts at that
 * folder, so a client code longer than the folder name skipped the folder's closing '/', and
 * the special folder was never recognised.
 */
@ExtendWith(MockitoExtension.class)
class SecuredFirstFolderAccessTest {

	private static final String LONG_CODE = "AVERYLONGCLIENTCODE01";

	@Mock
	private IFeignSecurityService securityService;
	@Mock
	private FilesAccessPathService accessPathService;
	@Mock
	private FilesMessageResourceService msgService;

	private MockedStatic<SecurityContextUtil> securityContext;

	private SecuredFileResourceService service;

	@BeforeEach
	void setUp() {
		securityContext = Mockito.mockStatic(SecurityContextUtil.class);
		service = new SecuredFileResourceService(null, accessPathService, msgService, null, null, null, null, null,
				securityService, msgService);
		// The path-access table would refuse: a pass must come from the special folder.
		lenient().when(accessPathService.hasReadAccess(anyString(), anyString(), any())).thenReturn(false);
	}

	@AfterEach
	void tearDown() {
		securityContext.close();
	}

	private void signIn(String clientCode) {
		ContextUser user = new ContextUser();
		user.setId(BigInteger.ONE);
		user.setClientId(BigInteger.ONE);
		user.setStringAuthorities(List.of());

		ContextAuthentication ca = new ContextAuthentication();
		ca.setUser(user);
		ca.setAuthenticated(true);
		ca.setClientCode(clientCode);
		securityContext.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(ca);
	}

	@Test
	void withInClientIsFoundForALongClientCode() {
		signIn(LONG_CODE);

		assertTrue(service.checkReadAccessWithClientCode(LONG_CODE + "/_withInClient/report.pdf"));
	}

	@Test
	void withInClientIsFoundBeneathASubFolder() {
		signIn(LONG_CODE);

		assertTrue(service.checkReadAccessWithClientCode(LONG_CODE + "/_withInClient/2026/report.pdf"));
	}

	@Test
	void withInClientStillRefusesAnotherClient() {
		signIn("OTHERCL1");

		assertFalse(service.checkReadAccessWithClientCode(LONG_CODE + "/_withInClient/report.pdf"));
	}

	@Test
	void shortClientCodeStillWorks() {
		signIn("CLIENT01");

		assertTrue(service.checkReadAccessWithClientCode("CLIENT01/_withInClient/2026/report.pdf"));
	}
}
