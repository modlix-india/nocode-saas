package com.modlix.saas.commons2.jooq.service;

import java.io.Serializable;
import java.util.List;

import org.jooq.UpdatableRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.modlix.saas.commons2.jooq.dao.AbstractDAO;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.dto.AbstractDTO;

public abstract class AbstractJOOQDataService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>, O extends AbstractDAO<R, I, D>> {

	@Autowired
	protected O dao;

	protected final Logger logger;

	protected AbstractJOOQDataService() {
		this.logger = LoggerFactory.getLogger(this.getClass());
	}

	public D create(D entity) {

		entity.setCreatedBy(null);
		I loggedInUserId = getLoggedInUserId();
		if (loggedInUserId != null) {
			entity.setCreatedBy(loggedInUserId);
		}

		return this.dao.create(entity);
	}

	protected I getLoggedInUserId() {
		return null;
	}

	public D read(I id) {
		return this.dao.readById(id);
	}

	public Page<D> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return this.dao.readPageFilter(pageable, condition);
	}

	public List<D> readAllFilter(AbstractCondition condition) {
		return this.dao.readAll(condition);
	}

	public Integer delete(I id) {
		return this.dao.delete(id);
	}
}
