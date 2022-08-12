package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.TokenDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.jooq.tables.records.SecurityUserTokenRecord;

@Service
public class TokenService extends AbstractDataService<SecurityUserTokenRecord, ULong, TokenObject, TokenDAO> {

}
