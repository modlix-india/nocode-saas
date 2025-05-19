package com.fincity.saas.commons.core.controller;

import com.fincity.saas.commons.core.configuration.CoreBaseConfiguration;
import com.fincity.saas.commons.core.controller.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core")
@Import({
    FunctionController.class,
    SchemaController.class,
    ConnectionController.class,
    ActionController.class,
    WorkflowController.class,
    TemplateController.class,
    VersionController.class,
    StorageController.class,
    EventActionController.class,
    EventDefinitionController.class,
    CoreFillerController.class,
    TransportController.class,
    DeletionController.class,
    FunctionExecutionController.class
})
public class CoreBaseController {

    @Autowired
    protected CoreBaseConfiguration coreConfig;

    // The controllers will be automatically wired by Spring
    @Autowired
    protected FunctionController functionController;

    @Autowired
    protected SchemaController schemaController;

    @Autowired
    protected ConnectionController connectionController;

    @Autowired
    protected ActionController actionController;

    @Autowired
    protected WorkflowController workflowController;

    @Autowired
    protected TemplateController templateController;

    @Autowired
    protected VersionController versionController;

    @Autowired
    protected StorageController storageController;

    @Autowired
    protected EventActionController eventActionController;

    @Autowired
    protected EventDefinitionController eventDefinitionController;

    @Autowired
    protected CoreFillerController coreFillerController;

    @Autowired
    protected TransportController transportController;

    @Autowired
    protected DeletionController deletionController;

    @Autowired
    protected FunctionExecutionController functionExecutionController;
}