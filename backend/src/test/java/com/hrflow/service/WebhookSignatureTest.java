package com.hrflow.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the HMAC-SHA256 signing matches an independent reference value
 * (computed with Python's hmac), so the receiver can trust the algorithm.
 */
class WebhookSignatureTest {

    @Test
    void matchesIndependentReferenceValue() {
        // python -c "import hmac,hashlib;
        //   print(hmac.new(b'test-secret', b'...', hashlib.sha256).hexdigest())"
        String body = "{\"event\":\"LEAVE_APPROVED\",\"id\":42}";
        String sig = WebhookService.hmacSha256(body, "test-secret");
        assertThat(sig).isEqualTo("18e1038c1f93e56889a2bcf903ff7025fb23f89ae87e1e6f35a8a0899f89b4b8");
    }

    @Test
    void differentSecretProducesDifferentSignature() {
        String body = "{\"event\":\"LEAVE_APPROVED\",\"id\":42}";
        assertThat(WebhookService.hmacSha256(body, "secret-a"))
                .isNotEqualTo(WebhookService.hmacSha256(body, "secret-b"));
    }

    @Test
    void signatureIsStableAndHexEncoded() {
        String sig = WebhookService.hmacSha256("hello", "k");
        assertThat(sig).hasSize(64).matches("[0-9a-f]+");
        assertThat(sig).isEqualTo(WebhookService.hmacSha256("hello", "k")); // deterministic
    }
}
