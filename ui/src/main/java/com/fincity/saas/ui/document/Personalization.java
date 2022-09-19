package com.fincity.saas.ui.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'applicationName': 1, 'name': 1, 'id': 1}", name = "themeFilteringIndex")
@Accessors(chain = true)
public class Personalization extends AbstractUIDTO {

	private static final long serialVersionUID = 4797291119009554778L;

	private Map<String, Object> personalization; // NOSONAR
}
