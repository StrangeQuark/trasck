package com.strangequark.trasck.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "import_mapping_status_translations")
public class ImportMappingStatusTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "mapping_template_id")
    private UUID mappingTemplateId;

    @Column(name = "source_status_key")
    private String sourceStatusKey;

    @Column(name = "target_status_key")
    private String targetStatusKey;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public UUID getMappingTemplateId() {
        return mappingTemplateId;
    }

    public void setMappingTemplateId(UUID mappingTemplateId) {
        this.mappingTemplateId = mappingTemplateId;
    }

    public String getSourceStatusKey() {
        return sourceStatusKey;
    }

    public void setSourceStatusKey(String sourceStatusKey) {
        this.sourceStatusKey = sourceStatusKey;
    }

    public String getTargetStatusKey() {
        return targetStatusKey;
    }

    public void setTargetStatusKey(String targetStatusKey) {
        this.targetStatusKey = targetStatusKey;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
