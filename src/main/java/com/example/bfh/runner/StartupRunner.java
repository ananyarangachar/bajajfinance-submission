package com.example.bfh.runner;

import com.example.bfh.entity.Submission;
import com.example.bfh.model.GenerateWebhookResponse;
import com.example.bfh.repo.SubmissionRepository;
import com.example.bfh.service.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StartupRunner implements CommandLineRunner {

    private final WebhookService webhookService;
    private final SubmissionRepository submissionRepository;

    @Value("${app.name}")
    private String name;
    @Value("${app.regNo}")
    private String regNo;
    @Value("${app.email}")
    private String email;
    @Value("${app.generateWebhookUrl}")
    private String generateWebhookUrl;
    @Value("${app.finalQueryFile:final-query.sql}")
    private String finalQueryFile;

    public StartupRunner(WebhookService webhookService, SubmissionRepository submissionRepository) {
        this.webhookService = webhookService;
        this.submissionRepository = submissionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Starting flow: generating webhook...");
        Map<String, String> payload = Map.of("name", name, "regNo", regNo, "email", email);

        GenerateWebhookResponse resp = webhookService.generateWebhook(generateWebhookUrl, payload);
        if (resp == null || resp.getWebhook() == null) {
            System.err.println("Failed to generate webhook or missing webhook URL in response. Exiting.");
            return;
        }
        System.out.println("Received webhook: " + resp.getWebhook());
        System.out.println("Received accessToken: " + (resp.getAccessToken() != null ? "[REDACTED]" : "null"));

        // Decide question link by last two digits of regNo
        int lastTwo = extractLastTwoDigits(regNo);
        boolean isOdd = (lastTwo % 2 != 0);
        String questionLink = isOdd
                ? "https://drive.google.com/file/d/1IeSI6l6KoSQAFfRihIT9tEDICtoz-G/view?usp=sharing"  // Q1 (odd)
                : "https://drive.google.com/file/d/143MR5cLFrlNEuHzzWJ5RHnEWuijuM9X/view?usp=sharing"; // Q2 (even)
        System.out.println("RegNo last two digits: " + lastTwo + " -> " + (isOdd ? "Odd (Question 1)" : "Even (Question 2)"));
        System.out.println("Question link (open, solve SQL, then paste final SQL into file " + finalQueryFile + "):");
        System.out.println(questionLink);

        // Read final query from file
        String finalQuery = "";
        Path file = Path.of(finalQueryFile);
        if (Files.exists(file)) {
            finalQuery = Files.readString(file).trim();
        } else {
            System.err.println("final-query file not found at: " + file.toAbsolutePath());
            System.err.println("Create the file, paste your final SQL query (single SQL statement), then rerun.");
            return;
        }

        if (finalQuery.isEmpty()) {
            System.err.println("final-query file is empty. Paste your final SQL query and re-run.");
            return;
        }

        // Save submission record
        Submission s = new Submission();
        s.setRegNo(regNo);
        s.setFinalQuery(finalQuery);
        s.setWebhookUrl(resp.getWebhook());
        s.setAccessToken(resp.getAccessToken());
        s.setStatus("PENDING");
        s.setCreatedAt(OffsetDateTime.now());
        s = submissionRepository.save(s);
        System.out.println("Saved submission id=" + s.getId());

        // Submit final query to webhook
        System.out.println("Submitting final query to the webhook...");
        boolean ok = webhookService.submitFinalQuery(resp.getWebhook(), resp.getAccessToken(), finalQuery);
        s.setStatus(ok ? "SUBMITTED" : "FAILED");
        submissionRepository.save(s);

        System.out.println("Submission result: " + s.getStatus());
        if (!ok) System.err.println("Submission failed. See logs above.");
    }

    private int extractLastTwoDigits(String reg) {
        // Extract digits from regNo, take last two, fallback to single digit
        Pattern p = Pattern.compile("(\\d+)$");
        Matcher m = p.matcher(reg);
        if (m.find()) {
            String digits = m.group(1);
            if (digits.length() >= 2) {
                return Integer.parseInt(digits.substring(digits.length() - 2));
            } else {
                return Integer.parseInt(digits);
            }
        }
        return 0;
    }
}
