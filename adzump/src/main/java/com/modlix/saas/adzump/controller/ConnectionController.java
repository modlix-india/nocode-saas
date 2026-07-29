package com.modlix.saas.adzump.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.adzump.model.connection.ConnectionSummary;
import com.modlix.saas.adzump.service.connection.ConnectionService;

@RestController
@RequestMapping("api/adzump/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public ResponseEntity<List<ConnectionSummary>> list() {
        return ResponseEntity.ok(this.connectionService.listConnections());
    }
}
