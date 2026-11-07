package com.pawtrack.identity.mapper;

import com.pawtrack.identity.dto.InvitationResponse;
import com.pawtrack.identity.entity.Invitation;
import org.mapstruct.Mapper;

@Mapper
public interface InvitationMapper {

    InvitationResponse toResponse(Invitation invitation);
}
