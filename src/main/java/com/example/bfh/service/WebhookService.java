package com.example.bfh.service;

import com.example.bfh.model.GenerateWebhookResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WebhookService {
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public GenerateWebhookResponse generateWebhook(String url, Map<String, String> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                System.err.println("generateWebhook returned non-2xx: " + resp.getStatusCode());
                return null;
            }
            JsonNode root = mapper.readTree(resp.getBody());
            // Try common field names
            String webhook = root.path("webhook").asText(null);
            if (webhook == null || webhook.isEmpty()) webhook = root.path("webHook").asText(null);

            String accessToken = root.path("accessToken").asText(null);
            if (accessToken == null || accessToken.isEmpty()) accessToken = root.path("token").asText(null);

            return new GenerateWebhookResponse(webhook, accessToken);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean submitFinalQuery(String webhookUrl, String accessToken, String finalQuery) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // As per instruction, put accessToken raw in Authorization header
            headers.set("Authorization", accessToken);
            Map<String, String> body = Map.of("finalQuery", finalQuery);
            HttpEntity<Map<String,String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, request, String.class);
            System.out.println("submitFinalQuery status: " + resp.getStatusCodeValue() + " body: " + resp.getBody());
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
