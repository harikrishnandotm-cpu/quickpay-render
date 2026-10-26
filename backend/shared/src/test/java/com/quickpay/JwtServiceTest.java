package com.quickpay;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-which-is-long-enough-for-hmac";

    @Test
    void issuesAndVerifiesCustomerTokens() {
        JwtService service = new JwtService(SECRET);
        String token = service.issue(42, "person@example.test");

        assertThat(service.verify(token)).isEqualTo(42);
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        String token = new JwtService(SECRET).issue(42, "person@example.test");

        assertThatThrownBy(() -> new JwtService("another-test-secret-which-is-long-enough").verify(token))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsWeakSigningSecrets() {
        assertThatThrownBy(() -> new JwtService("too-short"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void storesPasswordsAsVerifiableHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        String password = "local-demo-passphrase";
        String hash = encoder.encode(password);

        assertThat(hash).startsWith("$2a$").isNotEqualTo(password);
        assertThat(encoder.matches(password, hash)).isTrue();
    }
}
