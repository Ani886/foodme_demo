package am.foodme.backend.exceptionHandler;

import io.sentry.Sentry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * The generic handler resolves every unexpected exception before Sentry's own
 * exception resolver (order 1) runs, so it must report to GlitchTip itself.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void genericExceptionIsReportedToSentryExactlyOnce() {
        RuntimeException ex = new RuntimeException("unexpected failure");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handler.handleGeneric(ex, request("/api/debug/boom"));

            sentry.verify(() -> Sentry.captureException(ex), times(1));
            sentry.verifyNoMoreInteractions();
        }
    }

    @Test
    void genericExceptionStillReturnsTheSame500Response() {
        RuntimeException ex = new RuntimeException("unexpected failure");

        try (MockedStatic<Sentry> ignored = mockStatic(Sentry.class)) {
            ResponseEntity<ErrorResponseDto> response = handler.handleGeneric(ex, request("/api/debug/boom"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            ErrorResponseDto body = response.getBody();
            assertNotNull(body);
            assertEquals(500, body.getStatus());
            assertEquals("Internal Server Error", body.getError());
            assertEquals("unexpected failure", body.getMessage());
            assertEquals("/api/debug/boom", body.getPath());
            assertNotNull(body.getTimestamp());
            assertTrue(body.getTrace().startsWith("java.lang.RuntimeException: unexpected failure"));
        }
    }

    @Test
    void notFoundAndBadRequestAreNotReportedToSentry() {
        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            handler.handleNotFound(new NotFoundException("missing"), request("/api/chef/999"));
            handler.handleBadRequest(new BadRequestException("bad"), request("/api/order"));

            sentry.verify(() -> Sentry.captureException(any()), times(0));
        }
    }
}
