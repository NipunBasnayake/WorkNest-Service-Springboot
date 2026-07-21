package com.worknest.notification.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailTemplateFactoryBrandingTest {

    @Test
    void rendersTypedTenantBrandAndWorkNestAttribution() {
        BrandContextResolver resolver = mock(BrandContextResolver.class);
        when(resolver.resolveCurrentTenantOrDefault()).thenReturn(new BrandContext(
                "Acme & Co",
                "#FDE047"));
        EmailTemplateFactory factory = new EmailTemplateFactory(resolver);

        EmailContent content = factory.temporaryPassword("Jane <Admin>", "secret-value");

        assertThat(content.htmlBody())
                .contains("background:#FDE047")
                .contains("color:#111827")
                .contains("Acme &amp; Co")
                .contains("Powered by WorkNest")
                .contains("Jane &lt;Admin&gt;")
                .doesNotContain("Jane <Admin>");
    }

    @Test
    void usesWorkNestContextWhenResolverReturnsFallback() {
        BrandContextResolver resolver = mock(BrandContextResolver.class);
        when(resolver.resolveCurrentTenantOrDefault()).thenReturn(BrandContext.workNest());
        EmailTemplateFactory factory = new EmailTemplateFactory(resolver);

        EmailContent content = factory.passwordResetLink("Jane", "https://example.test/reset", 15);

        assertThat(content.htmlBody()).contains("WorkNest Notification").contains("background:#9332EA");
    }
}
