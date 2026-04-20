package com.strangequark.trasck.board;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/projects/{projectId}/boards")
    public List<BoardResponse> listProjectBoards(@PathVariable UUID projectId) {
        return boardService.listProjectBoards(projectId);
    }

    @PostMapping("/projects/{projectId}/boards")
    public ResponseEntity<BoardResponse> createBoard(@PathVariable UUID projectId, @RequestBody BoardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.createBoard(projectId, request));
    }

    @GetMapping("/boards/{boardId}")
    public BoardResponse getBoard(@PathVariable UUID boardId) {
        return boardService.getBoard(boardId);
    }

    @PatchMapping("/boards/{boardId}")
    public BoardResponse updateBoard(@PathVariable UUID boardId, @RequestBody BoardRequest request) {
        return boardService.updateBoard(boardId, request);
    }

    @DeleteMapping("/boards/{boardId}")
    public ResponseEntity<Void> archiveBoard(@PathVariable UUID boardId) {
        boardService.archiveBoard(boardId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/boards/{boardId}/columns")
    public List<BoardColumnResponse> listColumns(@PathVariable UUID boardId) {
        return boardService.listColumns(boardId);
    }

    @GetMapping("/boards/{boardId}/work-items")
    public BoardWorkItemsResponse listBoardWorkItems(
            @PathVariable UUID boardId,
            @RequestParam(required = false) Integer limitPerColumn
    ) {
        return boardService.listBoardWorkItems(boardId, limitPerColumn);
    }

    @PostMapping("/boards/{boardId}/columns")
    public ResponseEntity<BoardColumnResponse> createColumn(
            @PathVariable UUID boardId,
            @RequestBody BoardColumnRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.createColumn(boardId, request));
    }

    @PatchMapping("/boards/{boardId}/columns/{columnId}")
    public BoardColumnResponse updateColumn(
            @PathVariable UUID boardId,
            @PathVariable UUID columnId,
            @RequestBody BoardColumnRequest request
    ) {
        return boardService.updateColumn(boardId, columnId, request);
    }

    @DeleteMapping("/boards/{boardId}/columns/{columnId}")
    public ResponseEntity<Void> deleteColumn(@PathVariable UUID boardId, @PathVariable UUID columnId) {
        boardService.deleteColumn(boardId, columnId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/boards/{boardId}/swimlanes")
    public List<BoardSwimlaneResponse> listSwimlanes(@PathVariable UUID boardId) {
        return boardService.listSwimlanes(boardId);
    }

    @PostMapping("/boards/{boardId}/swimlanes")
    public ResponseEntity<BoardSwimlaneResponse> createSwimlane(
            @PathVariable UUID boardId,
            @RequestBody BoardSwimlaneRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(boardService.createSwimlane(boardId, request));
    }

    @PatchMapping("/boards/{boardId}/swimlanes/{swimlaneId}")
    public BoardSwimlaneResponse updateSwimlane(
            @PathVariable UUID boardId,
            @PathVariable UUID swimlaneId,
            @RequestBody BoardSwimlaneRequest request
    ) {
        return boardService.updateSwimlane(boardId, swimlaneId, request);
    }

    @DeleteMapping("/boards/{boardId}/swimlanes/{swimlaneId}")
    public ResponseEntity<Void> deleteSwimlane(@PathVariable UUID boardId, @PathVariable UUID swimlaneId) {
        boardService.deleteSwimlane(boardId, swimlaneId);
        return ResponseEntity.noContent().build();
    }
}
