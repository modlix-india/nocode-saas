package com.modlix.saas.commons2.mongo.repository;

import org.springframework.data.repository.CrudRepository;

import com.modlix.saas.commons2.mongo.document.Version;

public interface VersionRepository extends CrudRepository<Version, String> {

    public long deleteByObjectAppCodeAndClientCodeAndObjectType(String appCode, String clientCode, String objectType);
}

