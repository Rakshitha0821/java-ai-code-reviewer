package com.example.codereviewer.webhook;

import com.example.codereviewer.github.GitHubClient;
import com.example.codereviewer.llm.AiReviewer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
public class WebhookController {
    private final ObjectMapper mapper;
    private final GitHubClient gitHubClient;
    private final AiReviewer aiReviewer;
    private final String webhookSecret;

    public WebhookController(ObjectMapper mapper, GitHubClient gitHubClient, AiReviewer aiReviewer,
                             @Value("${app.github.webhook-secret:}") String webhookSecret) {
        this.mapper = mapper;
        this.gitHubClient = gitHubClient;
        this.aiReviewer = aiReviewer;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhooks/github")
    public ResponseEntity<String> receive(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            HttpServletRequest request) throws IOException {

        if (webhookSecret.isBlank() || !isValidSignature(body, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        if (!"pull_request".equals(event)) {
            return ResponseEntity.ok("Ignored event");
        }

        JsonNode payload = mapper.readTree(body);
        String action = payload.path("action").asText();
        if (!(action.equals("opened") || action.equals("reopened") || action.equals("synchronize"))) {
            return ResponseEntity.ok("Ignored pull request action");
        }

        JsonNode repository = payload.path("repository");
        String owner = repository.path("owner").path("login").asText();
        String repo = repository.path("name").asText();
        int pullNumber = payload.path("number").asInt(-1);
        if (owner.isBlank() || repo.isBlank() || pullNumber < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing repository or pull request details");
        }

        String diff = gitHubClient.getPullRequestDiff(owner, repo, pullNumber);
        String review = aiReviewer.review(owner, repo, pullNumber, diff);
        if (!review.isBlank()) {
            gitHubClient.postIssueComment(owner, repo, pullNumber, review);
        }
        return ResponseEntity.ok("Review completed");
    }

    private boolean isValidSignature(byte[] body, String supplied) {
        if (supplied == null || !supplied.matches("sha256=[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }
}
