package ru.practicum.ewm.compilation.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.service.CompilationService;

import jakarta.validation.Valid;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class CompilationController {

    private final CompilationService compilationService;

    //Admin

    @PostMapping("/admin/compilations")
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto createCompilation(@Valid @RequestBody NewCompilationDto newCompilationDto) {
        log.info("POST /admin/compilations - создание подборки: title={}, pinned={}, eventsCount={}",
                newCompilationDto.getTitle(), newCompilationDto.getPinned(),
                newCompilationDto.getEvents() != null ? newCompilationDto.getEvents().size() : 0);

        return compilationService.createCompilation(newCompilationDto);
    }

    @DeleteMapping("/admin/compilations/{compId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable @Positive Long compId) {
        log.info("DELETE /admin/compilations/{} - удаление подборки", compId);

        compilationService.deleteCompilation(compId);
    }

    @PatchMapping("/admin/compilations/{compId}")
    public CompilationDto updateCompilation(
            @PathVariable @Positive Long compId,
            @Valid @RequestBody UpdateCompilationRequest updateRequest
    ) {
        log.info("PATCH /admin/compilations/{} - обновление подборки: {}", compId, updateRequest);

        return compilationService.updateCompilation(compId, updateRequest);
    }

    //Public

    @GetMapping("/compilations")
    public List<CompilationDto> getCompilations(
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(defaultValue = "0") @PositiveOrZero Integer from,
            @RequestParam(defaultValue = "10") @Positive Integer size
    ) {
        log.info("GET /compilations - получение подборок: pinned={}, from={}, size={}", pinned, from, size);

        return compilationService.getCompilations(pinned, from, size);
    }

    @GetMapping("/compilations/{compId}")
    public CompilationDto getCompilationById(@PathVariable @Positive Long compId) {
        log.info("GET /compilations/{} - получение подборки по id", compId);

        return compilationService.getCompilationById(compId);
    }
}