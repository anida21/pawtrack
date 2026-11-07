package com.pawtrack.identity.mapper;

import com.pawtrack.identity.dto.UpdateUserRequest;
import com.pawtrack.identity.dto.UserResponse;
import com.pawtrack.identity.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);

    /** Copies the non-null fields of {@code request} onto {@code user}. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(UpdateUserRequest request, @MappingTarget User user);
}
