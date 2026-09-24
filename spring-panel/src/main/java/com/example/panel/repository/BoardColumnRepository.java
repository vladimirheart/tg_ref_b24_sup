package com.example.panel.repository;

import com.example.panel.entity.BoardColumn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {

    List<BoardColumn> findByBoard_IdOrderByPositionAscIdAsc(Long boardId);

    long countByBoard_Id(Long boardId);
}
