package com.example.bfh;

import com.example.bfh.model.GenerateWebhookResponse;
import com.example.bfh.service.WebhookService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@SpringBootApplication
public class BfhApplication {

    public static void main(String[] args) {
        SpringApplication.run(BfhApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public CommandLineRunner run(WebhookService webhookService) {
        return args -> {
            // 1️⃣ Generate webhook
            Map<String, String> payload = Map.of(
                    "name", "John Doe",
                    "regNo", "REG12347",
                    "email", "john@example.com"
            );
            String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";
            GenerateWebhookResponse response = webhookService.generateWebhook(generateUrl, payload);

            if (response != null) {
                System.out.println("Webhook: " + response.getWebhook());
                System.out.println("AccessToken: " + response.getAccessToken());

                // 2️⃣ SQL query string (Question 1)
                String finalQuery = "SELECT p.AMOUNT AS SALARY, CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME, " +
                        "TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE, d.DEPARTMENT_NAME " +
                        "FROM PAYMENTS p " +
                        "JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID " +
                        "JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID " +
                        "WHERE DAY(p.PAYMENT_TIME) <> 1 " +
                        "ORDER BY p.AMOUNT DESC " +
                        "LIMIT 1;";

                // 3️⃣ Submit final query to webhook
                boolean success = webhookService.submitFinalQuery(response.getWebhook(), response.getAccessToken(), finalQuery);
                System.out.println("Submission success: " + success);
            } else {
                System.err.println("Failed to generate webhook");
            }
        };
    }
}
