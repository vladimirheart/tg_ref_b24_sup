package com.example.panel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "incident_relations")
public class IncidentRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Column(name = "relation_type")
    private String relationType;

    @Column(name = "relation_key")
    private String relationKey;

    @Column(name = "relation_label")
    private String relationLabel;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "primary_relation")
    private Boolean primaryRelation;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Incident getIncident() {
        return incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getRelationKey() {
        return relationKey;
    }

    public void setRelationKey(String relationKey) {
        this.relationKey = relationKey;
    }

    public String getRelationLabel() {
        return relationLabel;
    }

    public void setRelationLabel(String relationLabel) {
        this.relationLabel = relationLabel;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Boolean getPrimaryRelation() {
        return primaryRelation;
    }

    public void setPrimaryRelation(Boolean primaryRelation) {
        this.primaryRelation = primaryRelation;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
