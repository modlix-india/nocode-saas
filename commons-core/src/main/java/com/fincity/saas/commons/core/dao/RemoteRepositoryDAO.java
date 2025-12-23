package com.fincity.saas.commons.core.dao;

import static com.fincity.saas.commons.core.jooq.tables.CoreRemoteRepositories.CORE_REMOTE_REPOSITORIES;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.core.dto.RemoteRepository;
import com.fincity.saas.commons.core.jooq.enums.CoreRemoteRepositoriesRepoName;
import com.fincity.saas.commons.core.jooq.tables.records.CoreRemoteRepositoriesRecord;
import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class RemoteRepositoryDAO extends AbstractUpdatableDAO<CoreRemoteRepositoriesRecord, ULong, RemoteRepository> {

    protected RemoteRepositoryDAO() {
        super(RemoteRepository.class, CORE_REMOTE_REPOSITORIES, CORE_REMOTE_REPOSITORIES.ID);
    }

    public Mono<RemoteRepository> findByAppCodeAndRepoName(String appCode, CoreRemoteRepositoriesRepoName repoName) {
        return Mono.from(this.dslContext
                .selectFrom(CORE_REMOTE_REPOSITORIES)
                .where(CORE_REMOTE_REPOSITORIES.APP_CODE.eq(appCode))
                .and(CORE_REMOTE_REPOSITORIES.REPO_NAME.eq(repoName))
                .limit(1))
                .map(e -> e.into(RemoteRepository.class));
    }

    public Mono<List<CoreRemoteRepositoriesRepoName>> findByAppCode(String appCode) {
        return Flux.from(this.dslContext
                .selectFrom(CORE_REMOTE_REPOSITORIES)
                .where(CORE_REMOTE_REPOSITORIES.APP_CODE.eq(appCode)))
                .map(e -> e.get(CORE_REMOTE_REPOSITORIES.REPO_NAME))
                .collectList();
    }
}
