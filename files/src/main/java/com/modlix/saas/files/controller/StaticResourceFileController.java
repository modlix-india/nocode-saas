package com.modlix.saas.files.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.CommonsUtil;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.service.StaticFileResourceService;

@RestController
@RequestMapping("api/files/static")
public class StaticResourceFileController extends AbstractResourceFileController<StaticFileResourceService> {

    public StaticResourceFileController(StaticFileResourceService service) {
        super(service);
    }

    @PostMapping("/copyToClientPage")
    public ResponseEntity<List<FileDetail>> copyToClientPage(
            @RequestBody List<String> fileUrls,
            @RequestParam(required = false) String clientCode,
            @RequestParam String pageName) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        String cc = CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode());

        List<FileDetail> copiedFiles = this.service.copyToClientPage(cc, pageName, fileUrls);

        return ResponseEntity.ok(copiedFiles);
    }
}
