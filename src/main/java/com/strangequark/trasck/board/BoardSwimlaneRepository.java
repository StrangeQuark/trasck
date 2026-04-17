package com.strangequark.trasck.board;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardSwimlaneRepository extends JpaRepository<BoardSwimlane, UUID> {
}
