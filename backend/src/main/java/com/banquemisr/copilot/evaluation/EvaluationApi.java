package com.banquemisr.copilot.evaluation;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EvaluationApi {

    record Response(int status, JsonNode body) {
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;

    EvaluationApi(String baseUrl, JsonMapper json) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.json = json;
    }

    String login(String username, String password) {
        return post("/api/auth/login", null, Map.of("username", username, "password", password)).body().path("accessToken").asString();
    }

    Response get(String path, String token) {
        return send("GET", path, token, null);
    }

    Response post(String path, String token, Object body) {
        return send("POST", path, token, body);
    }

    Response put(String path, String token, Object body) {
        return send("PUT", path, token, body);
    }

    static String query(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Response send(String method, String path, String token, Object body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("X-Request-Id", "eval-" + UUID.randomUUID())
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
            return new Response(response.statusCode(), parsed);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot reach " + baseUrl + path + ": " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
    }
}
