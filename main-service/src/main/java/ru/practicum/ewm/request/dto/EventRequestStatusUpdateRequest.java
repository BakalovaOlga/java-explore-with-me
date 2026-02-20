package ru.practicum.ewm.request.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.practicum.ewm.request.model.RequestStatus;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventRequestStatusUpdateRequest {
    @NotEmpty(message = "Список ID запросов не может быть пустым")
    private List<@NotNull Long> requestIds;

    @NotNull(message = "Статус должен быть указан")
    private RequestStatus status;
}