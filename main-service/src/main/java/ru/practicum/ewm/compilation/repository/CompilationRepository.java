package ru.practicum.ewm.compilation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.compilation.model.Compilation;

import java.util.Optional;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {

    Page<Compilation> findByPinned(Boolean pinned, Pageable pageable);

    @Query("SELECT c FROM Compilation c LEFT JOIN FETCH c.events WHERE c.id = :id")
    Optional<Compilation> findByIdWithEvents(@Param("id") Long id);

    boolean existsByTitle(String title);
}
