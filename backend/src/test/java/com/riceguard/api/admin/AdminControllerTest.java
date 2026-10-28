package com.riceguard.api.admin;

import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.community.CommunityService;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.media.MediaService;
import com.riceguard.api.user.UserService;
import com.riceguard.api.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {
    private ReminderService reminders;
    private AdminOperationsController operations;
    private Times times;
    private AdminController controller;
    private AuthUser admin;

    @BeforeEach
    void setup() {
        reminders = mock(ReminderService.class);
        operations = mock(AdminOperationsController.class);
        times = mock(Times.class);
        when(times.today()).thenReturn(LocalDate.of(2026, 10, 27));
        admin = new AuthUser("admin", "admin@example.com", "Admin", true);
        controller = new AdminController(mock(Fs.class), mock(Refs.class), mock(Paging.class), times,
                mock(CommunityService.class), mock(UserService.class), mock(DiseaseService.class),
                mock(MediaService.class), reminders, operations, mock(AdminAuditService.class));
    }

    @Test
    void testModeLimitsRecipientsBeforeSending() {
        when(reminders.runFor(LocalDate.of(2026, 10, 27), false, 10)).thenReturn(3);
        assertEquals(Map.of("sent", 3), controller.runReminders(admin, true));
        verify(reminders).runFor(LocalDate.of(2026, 10, 27), false, 10);
        verify(operations).log("REMINDERS_TEST", 3);
    }

    @Test
    void fullModeHasNoTestLimit() {
        controller.runReminders(admin, false);
        verify(reminders).runFor(LocalDate.of(2026, 10, 27), false, Integer.MAX_VALUE);
    }

    @Test
    void invalidFeedbackStatusCannotBeSaved() {
        assertThrows(ApiException.class, () -> controller.updateFeedback(admin, "fb", Map.of("status", "INVALID")));
    }
}
