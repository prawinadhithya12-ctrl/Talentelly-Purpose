
package com.example.inventory.controller;

import com.example.inventory.model.InventoryAudit;
import com.example.inventory.repository.InventoryAuditRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuditController {
    private final InventoryAuditRepository repo;
    public AuditController(InventoryAuditRepository repo){ this.repo = repo; }

    @GetMapping("/api/v1/audit")
    public List<InventoryAudit> recent(){ return repo.findAll().stream().sorted((a,b)->b.getPerformedAt().compareTo(a.getPerformedAt())).limit(200).toList(); }
}
