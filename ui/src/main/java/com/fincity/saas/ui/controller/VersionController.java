package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractVersionController;

@RestController
@RequestMapping("api/ui/versions")
public class VersionController extends AbstractVersionController{

}
