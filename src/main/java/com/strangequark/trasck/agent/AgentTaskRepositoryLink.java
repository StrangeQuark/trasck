package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "agent_task_repositories")
public class AgentTaskRepositoryLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "agent_task_id")
    private UUID agentTaskId;

    @Column(name = "repository_connection_id")
    private UUID repositoryConnectionId;

    @Column(name = "base_branch")
    private String baseBranch;

    @Column(name = "working_branch")
    private String workingBranch;

    @Column(name = "pull_request_url")
    private String pullRequestUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private JsonNode metadata;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAgentTaskId() {
        return agentTaskId;
    }

    public void setAgentTaskId(UUID agentTaskId) {
        this.agentTaskId = agentTaskId;
    }

    public UUID getRepositoryConnectionId() {
        return repositoryConnectionId;
    }

    public void setRepositoryConnectionId(UUID repositoryConnectionId) {
        this.repositoryConnectionId = repositoryConnectionId;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public String getWorkingBranch() {
        return workingBranch;
    }

    public void setWorkingBranch(String workingBranch) {
        this.workingBranch = workingBranch;
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    public void setPullRequestUrl(String pullRequestUrl) {
        this.pullRequestUrl = pullRequestUrl;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
