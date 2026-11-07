package com.pawtrack.notification.mapper;

import com.pawtrack.notification.dto.ReminderResponse;
import com.pawtrack.notification.entity.Reminder;
import org.mapstruct.Mapper;

@Mapper
public interface ReminderMapper {

    ReminderResponse toResponse(Reminder reminder);
}
