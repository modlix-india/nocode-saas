package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.service.UserService;

@RestController
@RequestMapping("api/security/users")
public class UserController extends AbstractJOOQDataController<SecurityUserRecord, ULong, User, UserDAO, UserService> {

}
