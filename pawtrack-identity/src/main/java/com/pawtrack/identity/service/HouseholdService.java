package com.pawtrack.identity.service;

import java.util.List;
import java.util.UUID;

import com.pawtrack.identity.dto.HouseholdMembershipResponse;
import com.pawtrack.identity.dto.HouseholdRequest;
import com.pawtrack.identity.dto.HouseholdResponse;
import com.pawtrack.identity.entity.Household;
import com.pawtrack.identity.entity.HouseholdMember;
import com.pawtrack.identity.entity.HouseholdRole;
import com.pawtrack.identity.entity.User;
import com.pawtrack.identity.exception.ConflictException;
import com.pawtrack.identity.exception.ForbiddenException;
import com.pawtrack.identity.exception.NotFoundException;
import com.pawtrack.identity.mapper.HouseholdMapper;
import com.pawtrack.identity.messaging.EventPublisher;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdDeleted;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdMemberAdded;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdMemberRemoved;
import com.pawtrack.identity.repository.HouseholdMemberRepository;
import com.pawtrack.identity.repository.HouseholdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HouseholdService {

    private final UserService userService;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository memberRepository;
    private final HouseholdMapper householdMapper;
    private final EventPublisher events;

    @Transactional
    public List<HouseholdMembershipResponse> getMyHouseholds(Jwt jwt) {
        User user = userService.currentUser(jwt);
        return householdMapper.toMembershipResponses(memberRepository.findAllByUserIdWithHousehold(user.getId()));
    }

    @Transactional
    public HouseholdResponse create(Jwt jwt, HouseholdRequest request) {
        User user = userService.currentUser(jwt);
        Household household = householdRepository.save(new Household(request.name(), user));
        events.publish(new HouseholdMemberAdded(household.getId(), user.getId(), HouseholdRole.OWNER));
        return householdMapper.toResponse(household);
    }

    @Transactional
    public HouseholdResponse get(Jwt jwt, UUID householdId) {
        User user = userService.currentUser(jwt);
        return householdMapper.toResponse(loadAsMember(householdId, user.getId()));
    }

    @Transactional
    public HouseholdResponse rename(Jwt jwt, UUID householdId, HouseholdRequest request) {
        User user = userService.currentUser(jwt);
        Household household = loadAsOwner(householdId, user.getId());
        household.setName(request.name());
        return householdMapper.toResponse(household);
    }

    @Transactional
    public void delete(Jwt jwt, UUID householdId) {
        User user = userService.currentUser(jwt);
        Household household = loadAsOwner(householdId, user.getId());
        householdRepository.delete(household);
        events.publish(new HouseholdDeleted(householdId));
    }

    /**
     * An OWNER may remove anyone; any member may remove themself.
     * The last OWNER can never be removed, otherwise the household would be unmanageable.
     */
    @Transactional
    public void removeMember(Jwt jwt, UUID householdId, UUID userId) {
        User caller = userService.currentUser(jwt);
        Household household = loadAsMember(householdId, caller.getId());
        HouseholdMember callerMembership = household.findMember(caller.getId()).orElseThrow();

        boolean removingSelf = caller.getId().equals(userId);
        if (!removingSelf && !callerMembership.isOwner()) {
            throw new ForbiddenException("Only an OWNER can remove other members");
        }
        HouseholdMember target = household.findMember(userId)
            .orElseThrow(() -> new NotFoundException("User " + userId + " is not a member of household " + householdId));
        if (target.isOwner() && household.ownerCount() == 1) {
            throw new ConflictException("The last OWNER cannot leave the household; delete it instead");
        }

        household.removeMember(target);
        events.publish(new HouseholdMemberRemoved(householdId, userId));
    }

    /** Loads the household with its members; 404 if missing, 403 if {@code userId} is not a member. */
    Household loadAsMember(UUID householdId, UUID userId) {
        Household household = householdRepository.findWithMembersById(householdId)
            .orElseThrow(() -> new NotFoundException("Household " + householdId + " not found"));
        if (household.findMember(userId).isEmpty()) {
            throw new ForbiddenException("You are not a member of household " + householdId);
        }
        return household;
    }

    /** Like {@link #loadAsMember} but additionally requires the OWNER role. */
    Household loadAsOwner(UUID householdId, UUID userId) {
        Household household = loadAsMember(householdId, userId);
        if (!household.findMember(userId).orElseThrow().isOwner()) {
            throw new ForbiddenException("Only an OWNER can do this");
        }
        return household;
    }
}
