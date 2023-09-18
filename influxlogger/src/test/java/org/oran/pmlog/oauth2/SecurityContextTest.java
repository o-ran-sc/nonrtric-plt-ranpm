package org.oran.pmlog.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SecurityContextTest {

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testConstructorWithAuthTokenFilename() {
        SecurityContext securityContext = new SecurityContext("auth-token-file.txt");
        assertNotNull(securityContext.getAuthTokenFilePath());
        assertEquals(Path.of("auth-token-file.txt"), securityContext.getAuthTokenFilePath());
    }

    @Test
    void testConstructorWithoutAuthTokenFilename() {
        SecurityContext securityContext = new SecurityContext("");
        assertNull(securityContext.getAuthTokenFilePath());
    }

    @Test
    void testIsConfigured() {
        SecurityContext securityContext = new SecurityContext("auth-token-file.txt");
        assertTrue(securityContext.isConfigured());
    }

    @Test
    void testIsNotConfigured() {
        SecurityContext securityContext = new SecurityContext("");
        assertFalse(securityContext.isConfigured());
    }

    @Test
    void testGetBearerAuthToken() {
        assertEquals("", SecurityContext.getInstance().getBearerAuthToken());
        assertEquals("", (new SecurityContext("foo.txt")).getBearerAuthToken());
    }

    @Test
    void testGetBearerAuthTokenWhenNotConfigured() {
        SecurityContext securityContext = new SecurityContext("");
        assertEquals("", securityContext.getBearerAuthToken());
    }
}

