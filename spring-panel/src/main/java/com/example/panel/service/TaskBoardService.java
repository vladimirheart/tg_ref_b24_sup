package com.example.panel.service;

import com.example.panel.entity.BoardColumn;
import com.example.panel.entity.BoardTaskPlacement;
import com.example.panel.entity.Project;
import com.example.panel.entity.ProjectBoard;
import com.example.panel.entity.Task;
import com.example.panel.entity.TaskEvent;
import com.example.panel.entity.TaskPerson;
import com.example.panel.entity.TaskProjectMembership;
import com.example.panel.repository.BoardColumnRepository;
import com.example.panel.repository.BoardTaskPlacementRepository;
import com.example.panel.repository.ProjectBoardRepository;
import com.example.panel.repository.ProjectRepository;
import com.example.panel.repository.TaskEventRepository;
import com.example.panel.repository.TaskPersonRepository;
import com.example.panel.repository.TaskProjectMembershipRepository;
import com.example.panel.repository.TaskRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskBoardService {

    private static final String SCOPE_PROJECT = "PROJECT";
    private static final String SCOPE_PERSONAL = "PERSONAL";
    private static final long CARD_POSITION_STEP = 1000L;
    private static final int COLUMN_POSITION_STEP = 100;

    private static final List<ColumnSeed> DEFAULT_COLUMNS = List.of(
        new ColumnSeed("NEW", "Новая"),
        new ColumnSeed("IN_PROGRESS", "В работе"),
        new ColumnSeed("WAITING", "Ожидание"),
        new ColumnSeed("DONE", "Завершена"),
        new ColumnSeed("CANCELLED", "Отменена")
    );

    private static final Set<String> PERSONAL_ROLES = Set.of("assignee", "co");

    private final ProjectBoardRepository projectBoardRepository;
    private final BoardColumnRepository boardColumnRepository;
    private final BoardTaskPlacementRepository boardTaskPlacementRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskPersonRepository taskPersonRepository;
    private final TaskProjectMembershipRepository taskProjectMembershipRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskDomainFoundationService taskDomainFoundationService;

    public TaskBoardService(ProjectBoardRepository projectBoardRepository,
                            BoardColumnRepository boardColumnRepository,
                            BoardTaskPlacementRepository boardTaskPlacementRepository,
                            ProjectRepository projectRepository,
                            TaskRepository taskRepository,
                            TaskPersonRepository taskPersonRepository,
                            TaskProjectMembershipRepository taskProjectMembershipRepository,
                            TaskEventRepository taskEventRepository,
                            TaskDomainFoundationService taskDomainFoundationService) {
        this.projectBoardRepository = projectBoardRepository;
        this.boardColumnRepository = boardColumnRepository;
        this.boardTaskPlacementRepository = boardTaskPlacementRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.taskPersonRepository = taskPersonRepository;
        this.taskProjectMembershipRepository = taskProjectMembershipRepository;
        this.taskEventRepository = taskEventRepository;
        this.taskDomainFoundationService = taskDomainFoundationService;
    }

    @Transactional
    public Map<String, Object> ensureProjectBoard(Long projectId, String actor) {
        Project project = requireProject(projectId);
        ProjectBoard board = ensureBoard(
            SCOPE_PROJECT,
            "PROJECT:" + project.getId(),
            project,
            null,
            "Проект: " + project.getName()
        );
        List<BoardColumn> columns = ensureDefaultColumns(board);
        syncEligibleTasks(board, columns, actor);
        return toDto(board, columns);
    }

    @Transactional
    public Map<String, Object> ensurePersonalBoard(String actor) {
        String owner = requireActor(actor);
        ProjectBoard board = ensureBoard(
            SCOPE_PERSONAL,
            "PERSONAL:" + owner.toLowerCase(Locale.ROOT),
            null,
            owner,
            "Мои задачи"
        );
        List<BoardColumn> columns = ensureDefaultColumns(board);
        syncEligibleTasks(board, columns, owner);
        return toDto(board, columns);
    }

    @Transactional
    public Map<String, Object> getBoard(Long boardId, String actor) {
        ProjectBoard board = requireBoard(boardId);
        authorizeBoard(board, actor);
        List<BoardColumn> columns = ensureDefaultColumns(board);
        syncEligibleTasks(board, columns, actor);
        return toDto(board, columns);
    }

    @Transactional
    public Map<String, Object> createColumn(Long boardId, String rawName, Integer targetIndex, String actor) {
        ProjectBoard board = requireBoard(boardId);
        authorizeBoard(board, actor);
        String name = requireColumnName(rawName);
        List<BoardColumn> columns = boardColumnRepository.findByBoard_IdOrderByPositionAscIdAsc(boardId);
        ensureUniqueColumnName(columns, name, null);

        OffsetDateTime now = OffsetDateTime.now();
        BoardColumn column = new BoardColumn();
        column.setBoard(board);
        column.setColumnKey("COL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        column.setName(name);
        column.setPosition(Integer.MIN_VALUE + columns.size());
        column.setCreatedAt(now);
        column.setUpdatedAt(now);
        column = boardColumnRepository.saveAndFlush(column);

        int index = clampIndex(targetIndex, columns.size());
        columns.add(index, column);
        persistColumnOrder(columns);
        touchBoard(board);
        return getBoard(boardId, actor);
    }

    @Transactional
    public Map<String, Object> updateColumn(Long boardId,
                                            Long columnId,
                                            String rawName,
                                            Integer targetIndex,
                                            String actor) {
        ProjectBoard board = requireBoard(boardId);
        authorizeBoard(board, actor);
        BoardColumn column = requireColumn(board, columnId);
        List<BoardColumn> columns = boardColumnRepository.findByBoard_IdOrderByPositionAscIdAsc(boardId);

        if (rawName != null) {
            String name = requireColumnName(rawName);
            ensureUniqueColumnName(columns, name, columnId);
            column.setName(name);
            column.setUpdatedAt(OffsetDateTime.now());
            boardColumnRepository.save(column);
        }

        if (targetIndex != null) {
            columns.removeIf(item -> Objects.equals(item.getId(), columnId));
            int index = clampIndex(targetIndex, columns.size());
            columns.add(index, column);
            persistColumnOrder(columns);
        }

        touchBoard(board);
        return getBoard(boardId, actor);
    }

    @Transactional
    public Map<String, Object> deleteColumn(Long boardId, Long columnId, String actor) {
        ProjectBoard board = requireBoard(boardId);
        authorizeBoard(board, actor);
        BoardColumn column = requireColumn(board, columnId);
        List<BoardColumn> columns = boardColumnRepository.findByBoard_IdOrderByPositionAscIdAsc(boardId);
        if (columns.size() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "На доске должна остаться хотя бы одна колонка.");
        }
        if (boardTaskPlacementRepository.countByColumn_Id(columnId) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала переместите задачи из удаляемой колонки.");
        }
        boardColumnRepository.delete(column);
        boardColumnRepository.flush();
        columns.removeIf(item -> Objects.equals(item.getId(), columnId));
        persistColumnOrder(columns);
        touchBoard(board);
        return getBoard(boardId, actor);
    }

    @Transactional
    public Map<String, Object> moveCard(Long boardId,
                                       Long taskId,
                                       Long targetColumnId,
                                       Integer targetIndex,
                                       String actor) {
        ProjectBoard board = requireBoard(boardId);
        authorizeBoard(board, actor);
        List<BoardColumn> columns = ensureDefaultColumns(board);
        syncEligibleTasks(board, columns, actor);

        BoardColumn targetColumn = requireColumn(board, targetColumnId);
        BoardTaskPlacement placement = boardTaskPlacementRepository.findByBoard_IdAndTask_Id(boardId, taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Задача не входит в текущую область доски."));

        BoardColumn sourceColumn = placement.getColumn();
        Long sourceColumnId = sourceColumn != null ? sourceColumn.getId() : null;
        String oldValue = sourceColumn != null ? sourceColumn.getId() + ":" + sourceColumn.getName() : null;

        if (sourceColumnId != null && !Objects.equals(sourceColumnId, targetColumnId)) {
            List<BoardTaskPlacement> sourceItems =
                boardTaskPlacementRepository.findByColumn_IdOrderByPositionAscIdAsc(sourceColumnId);
            sourceItems.removeIf(item -> Objects.equals(item.getId(), placement.getId()));
            reindexPlacements(sourceItems);
        }

        List<BoardTaskPlacement> targetItems =
            boardTaskPlacementRepository.findByColumn_IdOrderByPositionAscIdAsc(targetColumnId);
        targetItems.removeIf(item -> Objects.equals(item.getId(), placement.getId()));
        placement.setColumn(targetColumn);
        placement.setUpdatedAt(OffsetDateTime.now());
        placement.setUpdatedBy(clean(actor));
        int index = clampIndex(targetIndex, targetItems.size());
        targetItems.add(index, placement);
        reindexPlacements(targetItems);

        appendBoardMoveEvent(
            placement.getTask(),
            board,
            targetColumn,
            oldValue,
            targetColumn.getId() + ":" + targetColumn.getName(),
            actor,
            index
        );
        touchBoard(board);
        return getBoard(boardId, actor);
    }

    private ProjectBoard ensureBoard(String scopeType,
                                     String scopeKey,
                                     Project project,
                                     String ownerIdentity,
                                     String name) {
        OffsetDateTime now = OffsetDateTime.now();
        ProjectBoard board = projectBoardRepository.findByScopeKeyIgnoreCase(scopeKey).orElse(null);
        if (board == null) {
            board = new ProjectBoard();
            board.setScopeType(scopeType);
            board.setScopeKey(scopeKey);
            board.setProject(project);
            board.setOwnerIdentity(ownerIdentity);
            board.setName(name);
            board.setCreatedAt(now);
            board.setUpdatedAt(now);
            return projectBoardRepository.saveAndFlush(board);
        }
        if (!scopeType.equals(board.getScopeType())) {
            throw new IllegalStateException("Board scope collision for " + scopeKey);
        }
        board.setProject(project);
        board.setOwnerIdentity(ownerIdentity);
        board.setName(name);
        board.setUpdatedAt(now);
        return projectBoardRepository.save(board);
    }

    private List<BoardColumn> ensureDefaultColumns(ProjectBoard board) {
        List<BoardColumn> columns = boardColumnRepository.findByBoard_IdOrderByPositionAscIdAsc(board.getId());
        if (!columns.isEmpty()) {
            return columns;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<BoardColumn> created = new ArrayList<>();
        int position = COLUMN_POSITION_STEP;
        for (ColumnSeed seed : DEFAULT_COLUMNS) {
            BoardColumn column = new BoardColumn();
            column.setBoard(board);
            column.setColumnKey(seed.key());
            column.setName(seed.name());
            column.setPosition(position);
            column.setCreatedAt(now);
            column.setUpdatedAt(now);
            created.add(boardColumnRepository.save(column));
            position += COLUMN_POSITION_STEP;
        }
        boardColumnRepository.flush();
        return boardColumnRepository.findByBoard_IdOrderByPositionAscIdAsc(board.getId());
    }

    private void syncEligibleTasks(ProjectBoard board, List<BoardColumn> columns, String actor) {
        LinkedHashMap<Long, Task> eligible = eligibleTasks(board);
        List<BoardTaskPlacement> placements =
            boardTaskPlacementRepository.findByBoard_IdOrderByColumn_PositionAscPositionAscIdAsc(board.getId());
        LinkedHashMap<Long, BoardTaskPlacement> byTask = new LinkedHashMap<>();
        List<BoardTaskPlacement> stale = new ArrayList<>();

        for (BoardTaskPlacement placement : placements) {
            Task task = placement.getTask();
            Long taskId = task != null ? task.getId() : null;
            if (taskId == null || !eligible.containsKey(taskId)) {
                stale.add(placement);
            } else {
                byTask.put(taskId, placement);
            }
        }

        if (!stale.isEmpty()) {
            boardTaskPlacementRepository.deleteAll(stale);
            boardTaskPlacementRepository.flush();
        }

        Map<Long, Long> nextPosition = new LinkedHashMap<>();
        for (BoardColumn column : columns) {
            long max = boardTaskPlacementRepository.findByColumn_IdOrderByPositionAscIdAsc(column.getId()).stream()
                .map(BoardTaskPlacement::getPosition)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
            nextPosition.put(column.getId(), max + CARD_POSITION_STEP);
        }

        for (Task task : eligible.values()) {
            if (task.getId() == null || byTask.containsKey(task.getId())) {
                continue;
            }
            BoardColumn column = defaultColumnForTask(columns, task);
            BoardTaskPlacement placement = new BoardTaskPlacement();
            placement.setBoard(board);
            placement.setColumn(column);
            placement.setTask(task);
            placement.setPosition(nextPosition.getOrDefault(column.getId(), CARD_POSITION_STEP));
            placement.setUpdatedAt(OffsetDateTime.now());
            placement.setUpdatedBy(clean(actor));
            boardTaskPlacementRepository.save(placement);
            nextPosition.put(column.getId(), placement.getPosition() + CARD_POSITION_STEP);
        }
        boardTaskPlacementRepository.flush();
    }

    private LinkedHashMap<Long, Task> eligibleTasks(ProjectBoard board) {
        LinkedHashMap<Long, Task> tasks = new LinkedHashMap<>();
        if (SCOPE_PROJECT.equals(board.getScopeType())) {
            Project project = board.getProject();
            if (project == null || project.getId() == null) {
                return tasks;
            }
            for (TaskProjectMembership membership :
                    taskProjectMembershipRepository.findByProject_IdOrderByTask_IdAsc(project.getId())) {
                putTask(tasks, membership.getTask());
            }
            return tasks;
        }

        if (SCOPE_PERSONAL.equals(board.getScopeType())) {
            String owner = clean(board.getOwnerIdentity());
            if (owner == null) {
                return tasks;
            }
            for (Task task : taskRepository.findByAssigneeIgnoreCaseOrderByIdAsc(owner)) {
                putTask(tasks, task);
            }
            for (TaskPerson person : taskPersonRepository.findByIdentityIgnoreCaseAndRoleIn(owner, PERSONAL_ROLES)) {
                putTask(tasks, person.getTask());
            }
        }
        return tasks;
    }

    private void putTask(Map<Long, Task> tasks, Task task) {
        if (task != null && task.getId() != null) {
            tasks.putIfAbsent(task.getId(), task);
        }
    }

    private BoardColumn defaultColumnForTask(List<BoardColumn> columns, Task task) {
        String status = clean(task != null ? task.getStatus() : null);
        if (status != null) {
            for (BoardColumn column : columns) {
                if (status.equalsIgnoreCase(column.getName())) {
                    return column;
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("Board has no columns");
        }
        return columns.get(0);
    }

    private void persistColumnOrder(List<BoardColumn> columns) {
        int temp = -1000000;
        for (BoardColumn column : columns) {
            column.setPosition(temp++);
            column.setUpdatedAt(OffsetDateTime.now());
        }
        boardColumnRepository.saveAll(columns);
        boardColumnRepository.flush();

        int position = COLUMN_POSITION_STEP;
        for (BoardColumn column : columns) {
            column.setPosition(position);
            column.setUpdatedAt(OffsetDateTime.now());
            position += COLUMN_POSITION_STEP;
        }
        boardColumnRepository.saveAll(columns);
        boardColumnRepository.flush();
    }

    private void reindexPlacements(List<BoardTaskPlacement> placements) {
        long position = CARD_POSITION_STEP;
        OffsetDateTime now = OffsetDateTime.now();
        for (BoardTaskPlacement placement : placements) {
            placement.setPosition(position);
            placement.setUpdatedAt(now);
            position += CARD_POSITION_STEP;
        }
        boardTaskPlacementRepository.saveAll(placements);
        boardTaskPlacementRepository.flush();
    }

    private void appendBoardMoveEvent(Task task,
                                      ProjectBoard board,
                                      BoardColumn targetColumn,
                                      String oldValue,
                                      String newValue,
                                      String actor,
                                      int targetIndex) {
        if (task == null || task.getId() == null) {
            return;
        }
        TaskEvent event = new TaskEvent();
        event.setTask(task);
        event.setProjectId(board.getProject() != null ? board.getProject().getId() : null);
        event.setEventType("BOARD_CARD_MOVED");
        event.setActor(clean(actor));
        event.setOccurredAt(OffsetDateTime.now());
        event.setFieldName("board_column");
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setMetadataJson(
            "{\"board_id\":" + board.getId()
                + ",\"column_id\":" + targetColumn.getId()
                + ",\"target_index\":" + targetIndex
                + ",\"scope_type\":\"" + board.getScopeType() + "\"}"
        );
        taskEventRepository.save(event);
    }

    private Map<String, Object> toDto(ProjectBoard board, List<BoardColumn> columns) {
        List<BoardTaskPlacement> placements =
            boardTaskPlacementRepository.findByBoard_IdOrderByColumn_PositionAscPositionAscIdAsc(board.getId());
        Map<Long, List<Map<String, Object>>> cardsByColumn = new LinkedHashMap<>();
        for (BoardColumn column : columns) {
            cardsByColumn.put(column.getId(), new ArrayList<>());
        }
        for (BoardTaskPlacement placement : placements) {
            BoardColumn column = placement.getColumn();
            Task task = placement.getTask();
            if (column == null || task == null || !cardsByColumn.containsKey(column.getId())) {
                continue;
            }
            Map<String, Object> card = taskSummary(task);
            card.put("placement_id", placement.getId());
            card.put("position", placement.getPosition());
            cardsByColumn.get(column.getId()).add(card);
        }

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", board.getId());
        dto.put("scope_type", board.getScopeType());
        dto.put("scope_key", board.getScopeKey());
        dto.put("name", board.getName());
        dto.put("project_id", board.getProject() != null ? board.getProject().getId() : null);
        dto.put("owner_identity", board.getOwnerIdentity());
        dto.put("updated_at", board.getUpdatedAt() != null ? board.getUpdatedAt().toString() : null);
        dto.put("columns", columns.stream().map(column -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", column.getId());
            item.put("column_key", column.getColumnKey());
            item.put("name", column.getName());
            item.put("position", column.getPosition());
            item.put("cards", cardsByColumn.getOrDefault(column.getId(), List.of()));
            return item;
        }).toList());
        return dto;
    }

    private Map<String, Object> taskSummary(Task task) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", task.getId());
        dto.put("seq", task.getSeq());
        dto.put("title", task.getTitle());
        dto.put("assignee", task.getAssignee());
        dto.put("status", task.getStatus());
        dto.put("due_at", task.getDueAt() != null ? task.getDueAt().toString() : null);
        dto.put("last_activity_at", task.getLastActivityAt() != null ? task.getLastActivityAt().toString() : null);
        dto.put("projects", taskDomainFoundationService.listProjects(task.getId()));
        dto.put("tags", taskDomainFoundationService.listTags(task.getId()));
        return dto;
    }

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project id is required.");
        }
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private ProjectBoard requireBoard(Long boardId) {
        if (boardId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Board id is required.");
        }
        return projectBoardRepository.findById(boardId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Board not found"));
    }

    private BoardColumn requireColumn(ProjectBoard board, Long columnId) {
        if (columnId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column id is required.");
        }
        BoardColumn column = boardColumnRepository.findById(columnId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Board column not found"));
        if (column.getBoard() == null || !Objects.equals(column.getBoard().getId(), board.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column does not belong to this board.");
        }
        return column;
    }

    private void authorizeBoard(ProjectBoard board, String actor) {
        if (!SCOPE_PERSONAL.equals(board.getScopeType())) {
            return;
        }
        String current = requireActor(actor);
        String owner = clean(board.getOwnerIdentity());
        if (owner == null || !owner.equalsIgnoreCase(current)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Personal board belongs to another user.");
        }
    }

    private void ensureUniqueColumnName(List<BoardColumn> columns, String name, Long currentId) {
        for (BoardColumn column : columns) {
            if (Objects.equals(column.getId(), currentId)) {
                continue;
            }
            if (column.getName() != null && column.getName().equalsIgnoreCase(name)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Колонка с таким названием уже существует.");
            }
        }
    }

    private String requireColumnName(String raw) {
        String name = clean(raw);
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите название колонки.");
        }
        if (name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Название колонки слишком длинное.");
        }
        return name;
    }

    private int clampIndex(Integer requested, int size) {
        if (requested == null) {
            return size;
        }
        return Math.max(0, Math.min(requested, size));
    }

    private String requireActor(String actor) {
        String value = clean(actor);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return value;
    }

    private String clean(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private void touchBoard(ProjectBoard board) {
        board.setUpdatedAt(OffsetDateTime.now());
        projectBoardRepository.save(board);
    }

    private record ColumnSeed(String key, String name) {
    }
}
