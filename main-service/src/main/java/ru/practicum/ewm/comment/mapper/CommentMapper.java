package ru.practicum.ewm.comment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import ru.practicum.ewm.comment.dto.CommentDto;
import ru.practicum.ewm.comment.dto.NewCommentDto;
import ru.practicum.ewm.comment.dto.UpdateCommentDto;
import ru.practicum.ewm.comment.model.Comment;
import ru.practicum.ewm.user.mapper.UserMapper;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface CommentMapper {

    @Mapping(target = "author", source = "author")
    @Mapping(target = "eventId", source = "event.id")
    CommentDto toDto(Comment comment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "event", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "edited", constant = "false")
    Comment toEntity(NewCommentDto newCommentDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "event", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "edited", expression = "java(comment.isEdited() || !comment.getText().equals(updateDto.getText()))")
    void updateEntity(UpdateCommentDto updateDto, @MappingTarget Comment comment);
}
