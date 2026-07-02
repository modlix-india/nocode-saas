package com.modlix.saas.adzump.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.adzump.model.leadzump.Product;
import com.modlix.saas.adzump.model.leadzump.ProductTemplatePipeline;
import com.modlix.saas.adzump.service.leadzump.LeadzumpClient;

@RestController
@RequestMapping("api/adzump/products")
public class ProductController {

    private final LeadzumpClient leadzumpClient;

    public ProductController(LeadzumpClient leadzumpClient) {
        this.leadzumpClient = leadzumpClient;
    }

    @GetMapping
    public ResponseEntity<List<Product>> list() {
        return ResponseEntity.ok(this.leadzumpClient.listProducts());
    }

    // P0: the path id is passed through as the product-template id (templateId == id).
    // P1 resolves the product first and uses its templateId.
    @GetMapping("/{id}/pipeline")
    public ResponseEntity<ProductTemplatePipeline> pipeline(@PathVariable String id) {
        return ResponseEntity.ok(this.leadzumpClient.getPipeline(id));
    }
}
