package com.strangequark.trasck.customfield;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "screen_fields")
public class ScreenField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "screen_id")
    private UUID screenId;

    @Column(name = "custom_field_id")
    private UUID customFieldId;

    @Column(name = "system_field_key")
    private String systemFieldKey;

    @Column(name = "position")
    private Integer position;

    @Column(name = "required")
    private Boolean required;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getScreenId() {
        return screenId;
    }

    public void setScreenId(UUID screenId) {
        this.screenId = screenId;
    }

    public UUID getCustomFieldId() {
        return customFieldId;
    }

    public void setCustomFieldId(UUID customFieldId) {
        this.customFieldId = customFieldId;
    }

    public String getSystemFieldKey() {
        return systemFieldKey;
    }

    public void setSystemFieldKey(String systemFieldKey) {
        this.systemFieldKey = systemFieldKey;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }
}
