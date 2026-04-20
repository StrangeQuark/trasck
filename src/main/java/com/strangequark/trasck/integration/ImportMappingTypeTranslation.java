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
@Table(name = "import_mapping_type_translations")
public class ImportMappingTypeTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "mapping_template_id")
    private UUID mappingTemplateId;

    @Column(name = "source_type_key")
    private String sourceTypeKey;

    @Column(name = "target_type_key")
    private String targetTypeKey;

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

    public String getSourceTypeKey() {
        return sourceTypeKey;
    }

    public void setSourceTypeKey(String sourceTypeKey) {
        this.sourceTypeKey = sourceTypeKey;
    }

    public String getTargetTypeKey() {
        return targetTypeKey;
    }

    public void setTargetTypeKey(String targetTypeKey) {
        this.targetTypeKey = targetTypeKey;
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
