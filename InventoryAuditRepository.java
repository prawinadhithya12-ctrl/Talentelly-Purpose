
package com.example.inventory.repository;

import com.example.inventory.model.InventoryAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, Long> {
}
