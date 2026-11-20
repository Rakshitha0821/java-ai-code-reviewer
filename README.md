# Java AI Code Reviewer

A small Java 21 + Spring Boot service that receives GitHub pull request webhooks, verifies the HMAC signature, fetches the PR diff, asks an LLM for actionable review feedback, and posts the result as a PR comment.

## Requirements

- JDK 21+
- Maven 3.9+
- A GitHub token with access to the repository and permission to read pull requests and write issue/PR comments
- An OpenAI API key
- A publicly reachable HTTPS URL for production webhooks (for local development, use a tunnel such as ngrok)

## Run locally

1. Copy `.env.example` to `.env` and replace the sample values. Keep `.env` private; it is ignored by Git. The app reads environment variables, so load them into your shell before starting.
2. Start the application:

   ```bash
   mvn spring-boot:run
   ```

   Example PowerShell setup for the current terminal:

   ```powershell
   $env:GITHUB_TOKEN = "your-github-token"
   $env:GITHUB_WEBHOOK_SECRET = "your-long-random-secret"
   $env:OPENAI_API_KEY = "your-openai-key"
   mvn spring-boot:run
   ```

3. Expose port `8080` over HTTPS with your tunnel. Configure the repository webhook to point to `https://YOUR-HOST/webhooks/github`, use the same webhook secret, select **application/json**, and subscribe to **Pull requests**.
4. Open or update a pull request. Supported actions are `opened`, `reopened`, and `synchronize`.

For a fine-grained GitHub token, grant repository **Pull requests: read** and **Issues: write** permissions. GitHub stores PR conversation comments through the Issues comments endpoint.

## Configuration

| Environment variable | Required | Default |
| --- | --- | --- |
| `GITHUB_TOKEN` | Yes | — |
| `GITHUB_WEBHOOK_SECRET` | Yes | — |
| `OPENAI_API_KEY` | Yes | — |
| `OPENAI_MODEL` | No | `gpt-4.1-mini` |
| `SERVER_PORT` | No | `8080` |
| `GITHUB_API_BASE` | No | `https://api.github.com` |
| `OPENAI_API_URL` | No | `https://api.openai.com/v1/chat/completions` |

## Security and limitations

- Webhook authenticity is checked with HMAC-SHA256 and a constant-time comparison. Never commit tokens or webhook secrets.
- The diff is sent to the configured model provider. Use only repositories whose code you are allowed to share with that provider, and review the provider's data handling terms.
- The MVP sends one general PR comment. It does not create inline comments, persist review history, deduplicate webhook retries, or queue jobs. For production, add a durable queue, delivery-id deduplication, rate limiting, request timeouts, observability, and integration tests.
- Treat the model output as suggestions; a human should verify findings before acting on them.
- Diff input is capped at 100 KB to limit request size and model cost.

## Push to GitHub

Create an empty repository on GitHub, then from this directory:

```bash
git init
git add .
git commit -m "Build Java AI code review assistant"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/java-ai-code-reviewer.git
git push -u origin main
```

Do not add real credentials to `.env.example` or any committed file.
