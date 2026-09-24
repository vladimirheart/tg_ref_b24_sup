package com.example.panel.repository;

import com.example.panel.entity.BoardTaskPlacement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardTaskPlacementRepository extends JpaRepository<BoardTaskPlacement, Long> {

    List<BoardTaskPlacement> findByBoard_IdOrderByColumn_PositionAscPositionAscIdAsc(Long boardId);

    List<BoardTaskPlacement> findByColumn_IdOrderByPositionAscIdAsc(Long columnId);

    Optional<BoardTaskPlacement> findByBoard_IdAndTask_Id(Long boardId, Long taskId);

    long countByColumn_Id(Long columnId);
}
