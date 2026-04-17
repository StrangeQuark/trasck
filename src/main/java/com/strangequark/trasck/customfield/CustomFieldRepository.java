package com.strangequark.trasck.customfield;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {
}
