package com.riceguard.api.notification;

import com.riceguard.api.user.UserProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferenceTest {
    @Test
    void respectsUserSettings() {
        var s = new UserProfile.Settings("SYSTEM", "vi", false, true, false);
        assertFalse(NotificationService.enabled(s, "ALERT"));
        assertFalse(NotificationService.enabled(s, "WEATHER"));
        assertTrue(NotificationService.enabled(s, "REMINDER"));
        assertFalse(NotificationService.enabled(s, "COMMUNITY"));
        assertTrue(NotificationService.enabled(s, "TIP"));
        assertTrue(NotificationService.enabled(s, "UPDATE"));
    }
}
