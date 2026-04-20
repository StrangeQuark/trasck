package com.strangequark.trasck.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AutomationWorkerSettingsRepository extends JpaRepository<AutomationWorkerSettings, UUID> {

    @Query("""
            select settings
            from AutomationWorkerSettings settings
            where settings.automationJobsEnabled = true
               or settings.webhookDeliveriesEnabled = true
               or settings.emailDeliveriesEnabled = true
            """)
    List<AutomationWorkerSettings> findEnabledSettings();
}
