
package com.example.inventory.service;

import com.example.inventory.model.Inventory;
import com.example.inventory.model.InventoryAudit;
import com.example.inventory.repository.InventoryAuditRepository;
import com.example.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryAuditRepository auditRepository;

    public InventoryService(InventoryRepository inventoryRepository, InventoryAuditRepository auditRepository) {
        this.inventoryRepository = inventoryRepository;
        this.auditRepository = auditRepository;
    }

    public List<Inventory> listAll() {
        return inventoryRepository.findByDeletedFalse();
    }

    public Optional<Inventory> get(Long id) {
        return inventoryRepository.findById(id).filter(i->!i.getDeleted());
    }

    @Transactional
    public Inventory create(Inventory inventory, String reason) {
        Inventory saved = inventoryRepository.save(inventory);
        writeAudit(saved.getId(), "CREATE", null, saved, reason);
        return saved;
    }

    @Transactional
    public Inventory update(Long id, Inventory updated, String reason) {
        Inventory before = inventoryRepository.findById(id).orElseThrow();
        Inventory backup = copy(before);
        // apply fields if non-null (simple approach)
        if (updated.getName() != null) before.setName(updated.getName());
        if (updated.getSku() != null) before.setSku(updated.getSku());
        if (updated.getCategory() != null) before.setCategory(updated.getCategory());
        if (updated.getQuantity() != null) before.setQuantity(updated.getQuantity());
        if (updated.getUnitPrice() != null) before.setUnitPrice(updated.getUnitPrice());
        if (updated.getLocation() != null) before.setLocation(updated.getLocation());
        if (updated.getMinStock() != null) before.setMinStock(updated.getMinStock());
        if (updated.getNotes() != null) before.setNotes(updated.getNotes());
        if (updated.getDeleted() != null) before.setDeleted(updated.getDeleted());
        Inventory after = inventoryRepository.save(before);
        writeAudit(id, "UPDATE", backup, after, reason);
        return after;
    }

    @Transactional
    public Inventory adjustQuantity(Long id, int delta, String reason) {
        Inventory before = inventoryRepository.findById(id).orElseThrow();
        Inventory backup = copy(before);
        before.setQuantity(before.getQuantity() + delta);
        Inventory after = inventoryRepository.save(before);
        writeAudit(id, "QTY_ADJUST", backup, after, reason);
        return after;
    }

    @Transactional
    public void softDelete(Long id, String reason) {
        Inventory before = inventoryRepository.findById(id).orElseThrow();
        Inventory backup = copy(before);
        before.setDeleted(true);
        inventoryRepository.save(before);
        writeAudit(id, "SOFT_DELETE", backup, null, reason);
    }

    @Transactional
    public void hardDelete(Long id, String reason) {
        Inventory before = inventoryRepository.findById(id).orElseThrow();
        Inventory backup = copy(before);
        inventoryRepository.deleteById(id);
        writeAudit(id, "DELETE", backup, null, reason);
    }

    private void writeAudit(Long inventoryId, String action, Object before, Object after, String reason){
        InventoryAudit a = new InventoryAudit();
        a.setInventoryId(inventoryId);
        a.setAction(action);
        a.setBeforeState(before==null?null:before.toString());
        a.setAfterState(after==null?null:after.toString());
        a.setReason(reason);
        auditRepository.save(a);
    }

    private Inventory copy(Inventory src){
        Inventory c = new Inventory();
        c.setId(src.getId());
        c.setName(src.getName());
        c.setSku(src.getSku());
        c.setCategory(src.getCategory());
        c.setQuantity(src.getQuantity());
        c.setUnitPrice(src.getUnitPrice());
        c.setLocation(src.getLocation());
        c.setMinStock(src.getMinStock());
        c.setNotes(src.getNotes());
        c.setDeleted(src.getDeleted());
        return c;
    }
}
