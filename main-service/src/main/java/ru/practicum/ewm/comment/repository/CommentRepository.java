package ru.practicum.ewm.comment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.comment.model.Comment;

import java.util.List;
import java.util.Map;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "author")
    Page<Comment> findAllByEventId(Long eventId, Pageable pageable);

    List<Comment> findAllByAuthorId(Long authorId, Pageable pageable);

    @Query("""
                select new map(c.event.id as eventId, count(c) as count)
                from Comment c
                where c.event.id in :eventIds
                group by c.event.id
            """)
    List<Map<String, Object>> countCommentsAsMap(@Param("eventIds") List<Long> eventIds);

    Long countByEventId(Long eventId);

    List<Comment> findByEventId(Long eventId, Pageable pageable);
}
