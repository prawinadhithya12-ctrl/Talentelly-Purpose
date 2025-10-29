
package com.example.inventory.controller;

import com.example.inventory.model.Inventory;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService service;
    private final InventoryRepository repo;

    public InventoryController(InventoryService service, InventoryRepository repo) {
        this.service = service;
        this.repo = repo;
    }

    @GetMapping
    public List<Inventory> list(@RequestParam(required = false) String category,
                                @RequestParam(required = false) String location,
                                @RequestParam(required = false) String q) {
        if (category != null) return repo.findByCategoryAndDeletedFalse(category);
        if (location != null) return repo.findByLocationAndDeletedFalse(location);
        return service.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Inventory> get(@PathVariable Long id){
        return service.get(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Inventory inventory){
        try {
            Inventory created = service.create(inventory, null);
            return ResponseEntity.status(201).body(created);
        } catch (Exception ex){
            if (ex.getCause()!=null && ex.getCause().getMessage().contains("Duplicate")) {
                return ResponseEntity.status(409).body("SKU_DUPLICATE");
            }
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Inventory inventory){
        try {
            Inventory updated = service.update(id, inventory, null);
            return ResponseEntity.ok(updated);
        } catch (Exception ex){
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @PatchMapping("/{id}/quantity")
    public ResponseEntity<?> adjust(@PathVariable Long id, @RequestBody QuantityDelta d){
        try {
            Inventory after = service.adjustQuantity(id, d.delta, d.reason);
            return ResponseEntity.ok(after);
        } catch (Exception ex){
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam(defaultValue = "soft") String mode){
        try {
            if ("hard".equalsIgnoreCase(mode)) service.hardDelete(id, null);
            else service.softDelete(id, null);
            return ResponseEntity.ok().body(java.util.Map.of("deleted", true));
        } catch (Exception ex){
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    static class QuantityDelta { public int delta; public String reason; }
}
