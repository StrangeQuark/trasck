package com.strangequark.trasck.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "reporting_rollups")
public class ReportingRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "rollup_family")
    private String rollupFamily;
    @Column(name = "source")
    private String source;
    @Column(name = "granularity")
    private String granularity;
    @Column(name = "bucket_start_date")
    private LocalDate bucketStartDate;
    @Column(name = "bucket_end_date")
    private LocalDate bucketEndDate;
    @Column(name = "dimension_type")
    private String dimensionType;
    @Column(name = "dimension_id")
    private UUID dimensionId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics")
    private JsonNode metrics;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getRollupFamily() { return rollupFamily; }
    public void setRollupFamily(String rollupFamily) { this.rollupFamily = rollupFamily; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public LocalDate getBucketStartDate() { return bucketStartDate; }
    public void setBucketStartDate(LocalDate bucketStartDate) { this.bucketStartDate = bucketStartDate; }
    public LocalDate getBucketEndDate() { return bucketEndDate; }
    public void setBucketEndDate(LocalDate bucketEndDate) { this.bucketEndDate = bucketEndDate; }
    public String getDimensionType() { return dimensionType; }
    public void setDimensionType(String dimensionType) { this.dimensionType = dimensionType; }
    public UUID getDimensionId() { return dimensionId; }
    public void setDimensionId(UUID dimensionId) { this.dimensionId = dimensionId; }
    public JsonNode getMetrics() { return metrics; }
    public void setMetrics(JsonNode metrics) { this.metrics = metrics; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
