package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.exception.StorageObjectNotFoundException;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;

import reactor.test.StepVerifier;

/**
 * A missing row on the MySQL backend raises what the Mongo backend raises. It used to complete
 * empty, which AppDataService turned into FORBIDDEN_READ_STORAGE: a 403 for a 404, and one that
 * ReadStorageObject's not-found handling could not catch.
 */
class MySQLReadNotFoundTest {

	@Test
	void missingRowIsStorageObjectNotFound() {
		MySQLAppDataService service = new MySQLAppDataService(null, null, new CoreMessageResourceService(), null,
				null, new ObjectMapper(), null);

		Storage storage = new Storage();
		storage.setName("orders");

		StepVerifier.create(service.objectNotFound(storage, "42"))
				.expectErrorSatisfies(e -> {
					assertEquals(StorageObjectNotFoundException.class, e.getClass());
					assertEquals(HttpStatus.NOT_FOUND, ((StorageObjectNotFoundException) e).getStatusCode());
				})
				.verify();
	}
}
