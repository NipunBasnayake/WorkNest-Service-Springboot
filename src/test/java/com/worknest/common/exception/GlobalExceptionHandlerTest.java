package com.worknest.common.exception;

import com.worknest.common.api.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void duplicateApplicationReturnsConflictWithoutInternalDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/public/residue-solutions/careers/software-engineer/apply");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateApplicationException(
                new DuplicateApplicationException("Application already exists"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DUPLICATE_APPLICATION");
        assertThat(response.getBody().getMessage()).isEqualTo("Application already exists");
    }

    @Test
    void unsupportedMediaTypeIsNotReportedAsAnInternalServerError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/public/residue-solutions/careers/software-engineer/apply");

        ResponseEntity<ErrorResponse> response = handler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException("application/json"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(response.getBody().getMessage()).isEqualTo("Content type is not supported for this endpoint");
    }
}
