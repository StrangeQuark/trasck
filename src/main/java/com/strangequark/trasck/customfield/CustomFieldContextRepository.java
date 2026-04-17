package com.strangequark.trasck.customfield;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldContextRepository extends JpaRepository<CustomFieldContext, UUID> {
}
