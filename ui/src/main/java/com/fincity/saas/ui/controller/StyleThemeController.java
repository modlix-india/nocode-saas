package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.StyleTheme;
import com.fincity.saas.ui.repository.StyleThemeRepository;
import com.fincity.saas.ui.service.StyleThemeService;

@RestController
@RequestMapping("api/ui/themes")
public class StyleThemeController extends AbstractOverridableDataController<StyleTheme, StyleThemeRepository, StyleThemeService> {

}
