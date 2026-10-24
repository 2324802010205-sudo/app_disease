package com.riceguard.api.auth;

import com.riceguard.api.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityToolkitErrorTest {
    @Test
    void mapsFirebaseErrorCodes() {
        assertEquals(HttpStatus.CONFLICT, IdentityToolkitClient.mapCode("EMAIL_EXISTS").status());
        assertEquals(HttpStatus.UNAUTHORIZED, IdentityToolkitClient.mapCode("INVALID_LOGIN_CREDENTIALS").status());
        assertEquals(HttpStatus.UNAUTHORIZED, IdentityToolkitClient.mapCode("INVALID_PASSWORD").status());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, IdentityToolkitClient.mapCode("TOO_MANY_ATTEMPTS_TRY_LATER : Access to this account has been temporarily disabled").status());
        assertEquals(HttpStatus.UNAUTHORIZED, IdentityToolkitClient.mapCode("INVALID_REFRESH_TOKEN").status());
    }

    @Test
    void unknownCodeBecomesBadRequestWithoutLeakingDetails() {
        ApiException e = IdentityToolkitClient.mapCode("SOMETHING_NEW");
        assertEquals(HttpStatus.BAD_REQUEST, e.status());
    }

    @Test
    void loginErrorDoesNotRevealWhetherEmailExists() {
        assertEquals(IdentityToolkitClient.mapCode("EMAIL_NOT_FOUND").getMessage(), IdentityToolkitClient.mapCode("INVALID_PASSWORD").getMessage());
    }
}
