package com.strangequark.trasck.identity;

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
@Table(name = "user_invitations")
public class UserInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "email")
    private String email;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "project_role_id")
    private UUID projectRoleId;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "status")
    private String status;

    @Column(name = "invited_by_id")
    private UUID invitedById;

    @Column(name = "accepted_by_id")
    private UUID acceptedById;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getProjectRoleId() {
        return projectRoleId;
    }

    public void setProjectRoleId(UUID projectRoleId) {
        this.projectRoleId = projectRoleId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getInvitedById() {
        return invitedById;
    }

    public void setInvitedById(UUID invitedById) {
        this.invitedById = invitedById;
    }

    public UUID getAcceptedById() {
        return acceptedById;
    }

    public void setAcceptedById(UUID acceptedById) {
        this.acceptedById = acceptedById;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(OffsetDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
