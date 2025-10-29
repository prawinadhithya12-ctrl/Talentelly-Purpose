
package com.example.inventory.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory", indexes = {
        @Index(name = "idx_inventory_sku", columnList = "sku"),
        @Index(name = "idx_inventory_location", columnList = "location")
})
@Data
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String sku;

    private String category;

    private Integer quantity = 0;

    private Double unitPrice;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    private String location;

    private Integer minStock = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Boolean deleted = false;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
