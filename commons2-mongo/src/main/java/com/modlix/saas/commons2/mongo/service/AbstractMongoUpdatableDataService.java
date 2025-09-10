package com.modlix.saas.commons2.mongo.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.data.repository.CrudRepository;

import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import com.modlix.saas.commons2.model.dto.AbstractOverridableDTO;

public abstract class AbstractMongoUpdatableDataService<I extends Serializable, D extends AbstractUpdatableDTO<I, I>, R extends CrudRepository<D, I>>
        extends AbstractMongoDataService<I, D, R> {

    protected AbstractMongoUpdatableDataService(Class<D> pojoClass) {
        super(pojoClass);
    }

    public D update(D entity) {
        D updateableEntity = updatableEntity(entity);

        if (updateableEntity instanceof AbstractOverridableDTO<?> ovd
                && entity instanceof AbstractOverridableDTO<?> evd) {
            ovd.setTitle(evd.getTitle());
            ovd.setDescription(evd.getDescription());
            ovd.setPermission(evd.getPermission());
            ovd.setMessage(evd.getMessage());
        }

        I userId = getLoggedInUserId();
        if (userId != null) {
            updateableEntity.setUpdatedBy(userId);
            updateableEntity.setUpdatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        }

        if (entity instanceof AbstractOverridableDTO<?>
                && updateableEntity instanceof AbstractOverridableDTO<?>) {
            ((AbstractOverridableDTO<?>) updateableEntity)
                    .setMessage(((AbstractOverridableDTO<?>) entity).getMessage());
        }

        return this.repo.save(updateableEntity);
    }

    protected abstract D updatableEntity(D entity);
}

