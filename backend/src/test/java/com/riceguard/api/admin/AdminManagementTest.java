package com.riceguard.api.admin;

import com.google.firebase.auth.FirebaseAuth;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminManagementTest {
    private final AuthUser admin = new AuthUser("admin", "admin@example.com", "Admin", true);

    @Test
    void cannotLockOwnAccount() {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        AdminUsersController controller = new AdminUsersController(mock(Fs.class), mock(Refs.class),
                mock(Paging.class), mock(Times.class), mock(UserService.class), auth, mock(AdminAuditService.class));
        assertThrows(ApiException.class, () -> controller.lock(admin, "admin", Map.of("locked", true)));
        verifyNoInteractions(auth);
    }

    @Test
    void campaignRequiresValidAudienceBeforeAnyWrite() {
        Fs fs = mock(Fs.class);
        AdminCampaignController controller = new AdminCampaignController(fs, mock(Refs.class), mock(Paging.class),
                mock(Times.class), mock(AdminCampaignDispatcher.class), mock(AdminAuditService.class));
        assertThrows(ApiException.class, () -> controller.preview(new AdminCampaignController.Draft("Title", "Body", "OTHER", null, List.of())));
        assertThrows(ApiException.class, () -> controller.preview(new AdminCampaignController.Draft("Title", "Body", "PROVINCE", "", List.of())));
        assertThrows(ApiException.class, () -> controller.preview(new AdminCampaignController.Draft("Title", "Body", "SELECTED", null, List.of())));
        verifyNoInteractions(fs);
    }

    @Test
    void diseaseCreateRejectsInvalidId() {
        Fs fs = mock(Fs.class);
        AdminDiseasesController controller = new AdminDiseasesController(fs, mock(Refs.class), mock(DiseaseService.class), mock(AdminAuditService.class));
        AdminDiseasesController.Draft draft = new AdminDiseasesController.Draft("Bệnh", null, "Mô tả", "LEAF", "FUNGUS", false,
                List.of(), List.of(), List.of(), List.of());
        assertThrows(ApiException.class, () -> controller.create(admin, "bad/id", draft));
        verifyNoInteractions(fs);
    }
}
