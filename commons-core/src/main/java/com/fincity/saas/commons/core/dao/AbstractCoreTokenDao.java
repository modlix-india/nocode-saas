package com.fincity.saas.commons.core.dao;

import com.fincity.saas.commons.core.dto.CoreToken;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.LocalDateTime;

@Component
public abstract class AbstractCoreTokenDao<R extends UpdatableRecord<R>> extends AbstractUpdatableDAO<R, ULong, CoreToken> {

  protected AbstractCoreTokenDao(Class<CoreToken> pojoClass, Table<R> table, Field<ULong> idField) {
    super(pojoClass, table, idField);
  }

  public abstract Mono<Tuple2<String, LocalDateTime>> getActiveAccessTokenTuple(String clientCode, String appCode,
      String connectionName);

  public abstract Mono<String> getActiveAccessToken(String clientCode, String appCode, String connectionName);

  public abstract Mono<CoreToken> getCoreTokenByState(String state);

  public abstract Mono<Boolean> revokeToken(String clientCode, String appCode, String connectionName);
}
