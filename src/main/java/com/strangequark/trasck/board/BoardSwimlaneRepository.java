package com.strangequark.trasck.board;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardSwimlaneRepository extends JpaRepository<BoardSwimlane, UUID> {
    List<BoardSwimlane> findByBoardIdOrderByPositionAsc(UUID boardId);

    Optional<BoardSwimlane> findByIdAndBoardId(UUID id, UUID boardId);
}
