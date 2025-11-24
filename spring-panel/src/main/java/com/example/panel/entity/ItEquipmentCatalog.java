package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "it_equipment_catalog")
@Getter
@Setter
public class ItEquipmentCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String equipmentType;

    private String equipmentVendor;

    private String equipmentModel;

    private String photoUrl;

    private String serialNumber;

    private String accessories;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}