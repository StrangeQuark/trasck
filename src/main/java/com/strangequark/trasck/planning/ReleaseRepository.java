package com.strangequark.trasck.planning;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRepository extends JpaRepository<Release, UUID> {
}
