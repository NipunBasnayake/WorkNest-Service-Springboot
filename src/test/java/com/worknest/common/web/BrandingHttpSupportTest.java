package com.worknest.common.web;

import com.worknest.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandingHttpSupportTest {

    @Test
    void supportsStrongWeakWildcardAndListEtagMatching() {
        assertThat(BrandingHttpSupport.etag(12)).isEqualTo("\"brand-12\"");
        assertThat(BrandingHttpSupport.matches("\"other\", W/\"brand-12\"", 12)).isTrue();
        assertThat(BrandingHttpSupport.matches("*", 12)).isTrue();
        assertThat(BrandingHttpSupport.matches("\"brand-11\"", 12)).isFalse();
    }

    @Test
    void parsesVersionAndRejectsMalformedConcurrencyHeader() {
        assertThat(BrandingHttpSupport.parseVersion("W/\"brand-42\"")).isEqualTo(42L);
        assertThat(BrandingHttpSupport.parseVersion(null)).isNull();
        assertThatThrownBy(() -> BrandingHttpSupport.parseVersion("brand-current"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valid branding version");
    }
}
