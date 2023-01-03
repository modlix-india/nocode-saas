package com.fincity.saas.data.dao;

import static com.fincity.saas.data.jooq.tables.DataConnection.DATA_CONNECTION;

import org.jooq.types.UByte;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.data.dto.Connection;
import com.fincity.saas.data.jooq.tables.records.DataConnectionRecord;

import reactor.core.publisher.Mono;

@Service
public class ConnectionDAO extends AbstractUpdatableDAO<DataConnectionRecord, ULong, Connection> {

	protected ConnectionDAO() {
		super(Connection.class, DATA_CONNECTION, DATA_CONNECTION.ID);
	}

	public Mono<Integer> makeOtherDBsNotDefault(ULong id, String clientCode) {

		return Mono.from(this.dslContext.update(DATA_CONNECTION)
		        .set(DATA_CONNECTION.DEFAULT_DB, UByte.valueOf(0))
		        .where(DATA_CONNECTION.CLIENT_CODE.eq(clientCode)
		                .and(DATA_CONNECTION.ID.ne(id))));
	}

}
