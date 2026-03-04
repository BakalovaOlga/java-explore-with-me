package ru.practicum.ewm.comment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.mapper.CommentMapper;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.comment.repository.CommentRepository;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ForbiddenException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.service.UserService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private static final int TIME_TO_UPDATE_COMMENT = 30;

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final UserService userService;
    private final EventRepository eventRepository;

    @Override
    @Transactional(readOnly = true)
    public Collection<CommentDto> getAllCommentsPaged(int from, int size) {
        log.info("Получение всех комментариев с пагинацией: from={}, size={}", from, size);

        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);

        Page<Comment> commentsPage = commentRepository.findAll(pageable);

        return commentsPage.stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<CommentDto> getAllCommentsOfUserPaged(long userId, int from, int size) {
        log.info("Получение комментариев пользователя {}: from={}, size={}", userId, from, size);

        User user = userService.getUserById(userId);

        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);

        List<Comment> comments = commentRepository.findAllByAuthorId(user.getId(), pageable);

        return comments.stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteComment(long commentId) {
        log.info("Удаление комментария с id: {} (администратором)", commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден: " + commentId));

        commentRepository.delete(comment);
    }

    @Override
    @Transactional
    public void deleteCommentByUser(long userId, long commentId) {
        log.info("Пользователь {} удаляет свой комментарий {}", userId, commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден: " + commentId));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Вы не можете удалить чужой комментарий");
        }

        commentRepository.delete(comment);
    }

    @Override
    @Transactional
    public CommentDto createComment(long userId, long eventId, String text) {
        log.info("Создание комментария пользователем {} к событию {}", userId, eventId);

        User author = userService.getUserById(userId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие не найдено: " + eventId));

        Comment comment = Comment.builder()
                .text(text)
                .author(author)
                .event(event)
                .created(LocalDateTime.now())
                .edited(false)
                .build();

        Comment savedComment = commentRepository.save(comment);
        log.info("Комментарий создан с id: {}", savedComment.getId());

        return commentMapper.toDto(savedComment);
    }

    @Override
    @Transactional
    public CommentDto updateComment(long userId, long commentId, String text) {
        log.info("Обновление комментария {} пользователем {}", commentId, userId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден: " + commentId));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Вы можете редактировать только свои комментарии");
        }

        if (comment.getCreated().plusMinutes(TIME_TO_UPDATE_COMMENT).isBefore(LocalDateTime.now())) {
            throw new ValidationException("Время редактирования истекло: прошло более " +
                    TIME_TO_UPDATE_COMMENT + " минут)");
        }

        String oldText = comment.getText();

        comment.setText(text);

        if (!oldText.equals(text)) {
            comment.setEdited(true);
        }

        Comment updatedComment = commentRepository.save(comment);
        log.info("Комментарий {} обновлен", updatedComment.getId());

        return commentMapper.toDto(updatedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentDto getById(Long commentId) {
        log.info("Получение комментария по id: {}", commentId);

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден: " + commentId));

        return commentMapper.toDto(comment);
    }

    @Override
    public Long countCommentsByEventId(Long eventId) {
        log.debug("Подсчет комментариев для события: eventId={}", eventId);

        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        return commentRepository.countByEventId(eventId);
    }

    @Override
    public List<CommentDto> getRecentCommentsByEventId(Long eventId, int limit) {
        log.debug("Получение последних {} комментариев для события: eventId={}", limit, eventId);

        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        Pageable pageable = PageRequest.of(0, limit, Sort.by("created").descending());
        List<Comment> comments = commentRepository.findByEventId(eventId, pageable);

        return comments.stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, Long> countCommentsForEvents(List<Long> eventIds) {
        log.debug("Подсчет комментариев для списка событий: {} событий", eventIds.size());

        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> results = commentRepository.countCommentsAsMap(eventIds);

        Map<Long, Long> counts = new HashMap<>();
        for (Long eventId : eventIds) {
            counts.put(eventId, 0L);
        }

        for (Map<String, Object> row : results) {
            Long eventId = ((Number) row.get("eventId")).longValue();
            Long count = ((Number) row.get("count")).longValue();
            counts.put(eventId, count);
        }

        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<CommentDto> getCommentsByEventId(Long eventId, int from, int size) {
        log.info("Получение комментариев к событию {}: from={}, size={}", eventId, from, size);

        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }

        int page = from / size;
        Pageable pageable = PageRequest.of(page, size, Sort.by("created").descending());

        Page<Comment> commentsPage = commentRepository.findAllByEventId(eventId, pageable);

        return commentsPage.stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());
    }
}
