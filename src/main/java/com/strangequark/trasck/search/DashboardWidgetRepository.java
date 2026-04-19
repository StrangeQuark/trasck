package com.strangequark.trasck.search;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, UUID> {
    List<DashboardWidget> findByDashboardIdOrderByPositionYAscPositionXAsc(UUID dashboardId);

    void deleteByIdAndDashboardId(UUID id, UUID dashboardId);
}
