package com.fincity.saas.core.document;

import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Document
@CompoundIndex(def = "{'appCode': 1, 'name': 1, 'clientCode': 1}", name = "connectionFilteringIndex")
@Accessors(chain = true)
@NoArgsConstructor
public class Connection extends AbstractUpdatableDTO<String, String> {
	
	private static final long serialVersionUID = 2103048070747418809L;
	
	private String name;
	private String clientCode;
	private String appCode;
	
	private Map<String, Object> connectionDetails; // NOSONAR
}
