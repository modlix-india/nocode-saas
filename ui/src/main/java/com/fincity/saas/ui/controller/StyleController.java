package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Style;
import com.fincity.saas.ui.repository.StyleRepository;
import com.fincity.saas.ui.service.StyleService;

@RestController
@RequestMapping("api/ui/styles")
public class StyleController extends AbstractUIController<Style, StyleRepository, StyleService> {

}
