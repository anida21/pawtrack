package com.pawtrack.identity.service;

import java.util.Set;

import com.pawtrack.identity.messaging.EventEnvelope;
import com.pawtrack.identity.messaging.IdentityEvent;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdMemberAdded;
import com.pawtrack.identity.messaging.IdentityEvent.UserUpdated;
import com.pawtrack.identity.messaging.RabbitEventRelay;
import com.pawtrack.identity.repository.HouseholdMemberRepository;
import com.pawtrack.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Republishes the current state as upsert events (backfill, events.md): one {@code user.updated} per user and
 * one {@code household.member.added} per membership. Nothing changes here, so events are sent right away
 * instead of after a commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    static final String USER_UPDATED = "user.updated";
    static final String MEMBER_ADDED = "household.member.added";

    private final UserRepository users;
    private final HouseholdMemberRepository members;
    private final RabbitEventRelay relay;

    @Transactional(readOnly = true)
    public void replay(Set<String> types) {
        int sent = 0;
        if (types.contains(USER_UPDATED)) {
            for (var user : users.findAll()) {
                send(UserUpdated.of(user));
                sent++;
            }
        }
        if (types.contains(MEMBER_ADDED)) {
            for (var member : members.findAll()) {
                send(new HouseholdMemberAdded(member.getId().getHouseholdId(), member.getId().getUserId(),
                    member.getRole()));
                sent++;
            }
        }
        log.info("Replayed {} events for {}", sent, types);
    }

    private void send(IdentityEvent event) {
        relay.relay(EventEnvelope.wrap(event));
    }
}
