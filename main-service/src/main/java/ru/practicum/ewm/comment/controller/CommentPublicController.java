package ru.practicum.ewm.comment.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.service.CommentService;

import java.util.Collection;

@Slf4j
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Validated
public class CommentPublicController {

    private final CommentService commentService;

    @GetMapping
    public Collection<CommentDto> getAllComments(
            @RequestParam(defaultValue = "0") @PositiveOrZero int from,
            @RequestParam(defaultValue = "10") @Positive int size) {
        log.info("Public GET /comments: from={}, size={}", from, size);

        return commentService.getAllCommentsPaged(from, size);
    }

    @GetMapping("/{commentId}")
    public CommentDto getComment(@PathVariable @Positive Long commentId) {
        log.info("Public GET /comments/{}", commentId);

        return commentService.getById(commentId);
    }
}
