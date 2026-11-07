package com.pawtrack.identity.mapper;

import java.util.List;

import com.pawtrack.identity.dto.HouseholdMembershipResponse;
import com.pawtrack.identity.dto.HouseholdResponse;
import com.pawtrack.identity.dto.MemberResponse;
import com.pawtrack.identity.entity.Household;
import com.pawtrack.identity.entity.HouseholdMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface HouseholdMapper {

    HouseholdResponse toResponse(Household household);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "displayName", source = "user.displayName")
    @Mapping(target = "email", source = "user.email")
    MemberResponse toMemberResponse(HouseholdMember member);

    @Mapping(target = "householdId", source = "household.id")
    @Mapping(target = "householdName", source = "household.name")
    HouseholdMembershipResponse toMembershipResponse(HouseholdMember member);

    List<HouseholdMembershipResponse> toMembershipResponses(List<HouseholdMember> members);
}
