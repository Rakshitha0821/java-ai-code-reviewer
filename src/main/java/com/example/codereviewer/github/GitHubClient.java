package com.example.codereviewer.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class GitHubClient {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String token;
    private final String apiBase;

    public GitHubClient(HttpClient http, ObjectMapper mapper,
                        @Value("${app.github.token:}") String token,
                        @Value("${app.github.api-base:https://api.github.com}") String apiBase) {
        this.http = http;
        this.mapper = mapper;
        this.token = token;
        this.apiBase = apiBase.replaceAll("/$", "");
    }

    public String getPullRequestDiff(String owner, String repo, int number) {
        String path = "/repos/" + enc(owner) + "/" + enc(repo) + "/pulls/" + number;
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path))
                .header("Accept", "application/vnd.github.diff")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> response = send(request);
        if (response.body().length() > 100_000) {
            return response.body().substring(0, 100_000) + "\n[Diff truncated to 100 KB]";
        }
        return response.body();
    }

    public void postIssueComment(String owner, String repo, int number, String comment) {
        String path = "/repos/" + enc(owner) + "/" + enc(repo) + "/issues/" + number + "/comments";
        ObjectNode json = mapper.createObjectNode().put("body", comment);
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + path))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        send(request);
    }

    private HttpResponse<String> send(HttpRequest request) {
        if (token.isBlank()) throw new IllegalStateException("GITHUB_TOKEN is not configured");
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("GitHub API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return response;
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach GitHub API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub API request interrupted", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
