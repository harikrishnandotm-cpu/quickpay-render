package com.quickpay;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
class GatewayController {

    private final JwtService jwt;
    private final Map<String, String> serviceUrls;
    private final RestClient client;

    GatewayController(JwtService jwt, Environment env) {
        this.jwt = jwt;

        // Timeouts: a sleeping Render service can take ~60s to wake up
        var factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.client = RestClient.builder().requestFactory(factory).build();

        serviceUrls = Map.of(
            "auth", env.getProperty("CUSTOMER_SERVICE_URL", "http://localhost:8081"),
            "customers", env.getProperty("CUSTOMER_SERVICE_URL", "http://localhost:8081"),
            "merchants", env.getProperty("MERCHANT_SERVICE_URL", "http://localhost:8082"),
            "payments", env.getProperty("PAYMENT_SERVICE_URL", "http://localhost:8083"),
            "transactions", env.getProperty("TRANSACTION_SERVICE_URL", "http://localhost:8084"),
            "notifications", env.getProperty("NOTIFICATION_SERVICE_URL", "http://localhost:8085")
        );
    }

    @RequestMapping("/api/{service}/**")
    ResponseEntity<byte[]> route(
            @PathVariable String service,
            HttpServletRequest request,
            @RequestHeader HttpHeaders incoming) throws IOException {

        // Handle CORS preflight requests
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return ResponseEntity.noContent().build();
        }

        String path = request.getRequestURI();

        // Signup and login are public endpoints
        boolean publicAuth = service.equals("auth")
            && (path.endsWith("/signup") || path.endsWith("/login"))
            && request.getMethod().equals("POST");

        String customerId = null;

        // JWT validation for protected endpoints
        if (!publicAuth) {

            String authHeader = incoming.getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":\"Authentication required\"}"
                    .getBytes(StandardCharsets.UTF_8));
            }

            try {
                customerId = Long.toString(
                    jwt.verify(authHeader.substring(7))
                );

            } catch (RuntimeException ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":\"Invalid or expired token\"}"
                    .getBytes(StandardCharsets.UTF_8));
            }
        }

        String baseUrl = serviceUrls.get(service);

        if (baseUrl == null) {
            return ResponseEntity.notFound().build();
        }

        String downstreamPath = path;

        String query = request.getQueryString();

        if (query != null) {
            downstreamPath += "?" + query;
        }

        byte[] body = StreamUtils.copyToByteArray(
            request.getInputStream()
        );

        String finalCustomerId = customerId;

        RestClient.RequestBodySpec downstream =
            client.method(HttpMethod.valueOf(request.getMethod()))
                .uri(baseUrl + downstreamPath)
                .headers(headers -> {

                    MediaType contentType = incoming.getContentType();

                    if (contentType != null) {
                        headers.setContentType(contentType);
                    }

                    if (finalCustomerId != null) {
                        headers.set("X-Customer-Id", finalCustomerId);
                    }
                });

        if (body.length > 0) {
            downstream.body(body);
        }

        try {
        return downstream.exchange((req, res) -> {

            HttpHeaders responseHeaders = new HttpHeaders();

            if (res.getHeaders().getContentType() != null) {
                responseHeaders.setContentType(
                    res.getHeaders().getContentType()
                );
            }

            return new ResponseEntity<>(
                StreamUtils.copyToByteArray(res.getBody()),
                responseHeaders,
                res.getStatusCode()
            );
        });
        } catch (ResourceAccessException ex) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"The service is waking up or unreachable. Please retry in a minute.\"}"
                    .getBytes(StandardCharsets.UTF_8));
        }
    }
}
