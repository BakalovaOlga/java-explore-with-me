package ru.practicum.ewm.comment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.service.CommentService;

import java.util.Collection;

@Slf4j
@RestController
@RequestMapping("/users/{userId}/comments")
@RequiredArgsConstructor
@Validated
public class CommentPrivateController {

    private final CommentService commentService;

    @GetMapping
    public Collection<CommentDto> getUserComments(
            @PathVariable long userId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        log.info("Private GET /users/{}/comments: from={}, size={}", userId, from, size);

        return commentService.getAllCommentsOfUserPaged(userId, from, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto createComment(
            @PathVariable @Positive long userId,
            @RequestBody @Valid NewCommentDto commentDto) {
        log.info("Private POST /users/{}/comments: eventId={}", userId, commentDto.getEventId());

        return commentService.createComment(userId, commentDto.getEventId(), commentDto.getText());
    }

    @PatchMapping("/{commentId}")
    public CommentDto updateComment(
            @PathVariable @Positive long userId,
            @PathVariable @Positive long commentId,
            @RequestBody @Valid UpdateCommentDto commentDto) {
        log.info("Private PATCH /users/{}/comments/{}", userId, commentId);

        return commentService.updateComment(userId, commentId, commentDto.getText());
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @PathVariable @Positive long userId,
            @PathVariable @Positive long commentId) {
        log.info("Private DELETE /users/{}/comments/{}", userId, commentId);

        commentService.deleteCommentByUser(userId, commentId);
    }
}

