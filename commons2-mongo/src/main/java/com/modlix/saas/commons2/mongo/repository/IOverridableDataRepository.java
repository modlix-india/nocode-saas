package com.modlix.saas.commons2.mongo.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.modlix.saas.commons2.model.dto.AbstractOverridableDTO;

public interface IOverridableDataRepository<D extends AbstractOverridableDTO<D>>
        extends CrudRepository<D, String> {

    public D findOneByNameAndAppCodeAndClientCode(String name, String appCode, String clientCode);

    public List<D> findByNameAndAppCodeAndClientCodeIn(String name, String appCode, List<String> clientCodes);

    public List<D> findByNameAndAppCodeAndBaseClientCode(String name, String appCode, String baseClientCode);

    public long countByNameAndAppCodeAndBaseClientCode(String name, String appCode, String clientCode);

    public List<D> findByAppCodeAndClientCode(String appCode, String clientCode);

    public long deleteByAppCodeAndClientCode(String appCode, String clientCode);
}

