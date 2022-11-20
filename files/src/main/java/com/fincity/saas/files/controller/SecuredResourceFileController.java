package com.fincity.saas.files.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.files.service.SecuredFileResourceService;

@RestController
@RequestMapping("api/files/secured")
public class SecuredResourceFileController extends AbstractResourceFileController<SecuredFileResourceService>{

}
