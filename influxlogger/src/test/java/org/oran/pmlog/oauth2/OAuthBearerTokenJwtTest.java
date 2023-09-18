package org.oran.pmlog.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.oran.pmlog.exceptions.ServiceException;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = {OAuthBearerTokenJwtTest.class})
@ExtendWith(MockitoExtension.class)
class OAuthBearerTokenJwtTest {

    private OAuthBearerTokenJwt token;

    @BeforeEach
    void setUp() throws ServiceException, JsonProcessingException {
        String validJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"; // Replace with a valid JWT token for testing
        token = OAuthBearerTokenJwt.create(validJwt);
    }

    @Test
    void testCreateValidToken() {
        assertNotNull(token);
    }

    @Test
    void testCreateInvalidToken() {
        assertThrows(ServiceException.class, () -> OAuthBearerTokenJwt.create("invalid_token"));
    }

    @Test
    void testTokenValue() {
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c", token.value());
    }

    @Test
    void testTokenScope() {
        assertEquals(0, token.scope().size());
        assertFalse(token.scope().contains(""));
    }

    @Test
    void testTokenLifetimeMs() {
        assertEquals(Long.MAX_VALUE, token.lifetimeMs());
    }

    @Test
    void testTokenPrincipalName() {
        assertEquals("1234567890", token.principalName());
    }

    @Test
    void testTokenStartTimeMs() {
        assertEquals(1516239022L, token.startTimeMs());
    }

    @Test
    void testCreateTokenFromInvalidPayload() throws ServiceException {
        // Create a JWT with an invalid payload (missing fields)
        String invalidPayload = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
        assertThrows(ServiceException.class, () -> OAuthBearerTokenJwt.create(invalidPayload));
    }

    @Test
    void testCreateTokenWithValidPayload() throws ServiceException, JsonProcessingException {
        // Create a JWT with a valid payload
        String validPayload = "eyJzdWIiOiAiVGVzdCIsICJleHAiOiAxNjM1MTUwMDAwLCAiaWF0IjogMTYzNTA5NTAwMCwgInNjb3BlIjogInNjb3BlX3Rva2VuIiwgImp0aSI6ICJmb28ifQ==";
        OAuthBearerTokenJwt jwt = OAuthBearerTokenJwt.create("header." + validPayload + ".signature");

        assertNotNull(jwt);
        assertEquals("header." + validPayload + ".signature", jwt.value());
        assertEquals(1, jwt.scope().size());
        assertEquals("scope_token", jwt.scope().iterator().next());
        assertEquals("Test", jwt.principalName());
        assertEquals(1635095000, jwt.startTimeMs());
    }

    @Test
    void testCreateThrowsExceptionWithInvalidToken() throws ServiceException {
        String tokenRaw = "your_mocked_token_here";
        assertThrows(ServiceException.class, () -> OAuthBearerTokenJwt.create(tokenRaw));
    }
}

