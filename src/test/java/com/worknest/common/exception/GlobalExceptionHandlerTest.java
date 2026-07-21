package com.worknest.common.exception;

import com.worknest.common.api.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @Test
    void unmappedResourceReturnsNotFoundInsteadOfInternalServerError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/branding/assets/removed-logo");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/api/branding/assets/removed-logo"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }
}
