package com.fincity.saas.ui.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.repository.PageRepository;
import com.fincity.saas.ui.service.PageService;

@RestController
@RequestMapping("api/ui/pages")
public class PageController extends AbstractUIController<Page, PageRepository, PageService> {

}
