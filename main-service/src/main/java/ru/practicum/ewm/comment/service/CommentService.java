package ru.practicum.ewm.comment.service;

import ru.practicum.ewm.comment.dto.CommentDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CommentService {
    Collection<CommentDto> getAllCommentsPaged(int from, int size);

    Collection<CommentDto> getAllCommentsOfUserPaged(long userId, int from, int size);

    void deleteComment(long commentId);

    void deleteCommentByUser(long userId, long commentId);

    CommentDto createComment(long userId, long eventId, String text);

    CommentDto updateComment(long userId, long commentId, String text);

    CommentDto getById(Long commentId);

    Long countCommentsByEventId(Long eventId);

    List<CommentDto> getRecentCommentsByEventId(Long eventId, int limit);

    Map<Long, Long> countCommentsForEvents(List<Long> eventIds);

    Collection<CommentDto> getCommentsByEventId(Long eventId, int from, int size);
}
