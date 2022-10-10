package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Theme;
import com.fincity.saas.ui.repository.ThemeRepository;
import com.fincity.saas.ui.service.ThemeService;

@RestController
@RequestMapping("api/ui/themes")
public class ThemeController extends AbstractUIController<Theme, ThemeRepository, ThemeService> {

}
