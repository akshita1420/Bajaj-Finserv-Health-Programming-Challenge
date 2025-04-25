package com.example.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class WebhookService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void processWebhook() {
        try {
            // 1. Call generateWebhook
            String url = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("name", "John Doe");
            requestBody.put("regNo", "REG12347");
            requestBody.put("email", "john@example.com");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                System.err.println("Failed to get webhook: " + response.getStatusCode());
                return;
            }

            // Parse the response JSON
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode users = root.path("data").path("users");
            List<List<Object>> idFollowsList = new ArrayList<>();
            for (JsonNode user : users) {
                int id = user.path("id").asInt();
                List<Integer> follows = new ArrayList<>();
                for (JsonNode f : user.path("follows")) {
                    follows.add(f.asInt());
                }
                List<Object> entry = new ArrayList<>();
                entry.add(id);
                entry.add(follows);
                idFollowsList.add(entry);
            }
            //Saving the POST response to response.json file for checking the which question to solve
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File("response.json"), root);
            

            //After looking at the recieved response, it is in the sample format of Question 2 
            
            
            // Parsing the response.json file
            root = objectMapper.readTree(new File("response.json"));
            JsonNode usersNode = root.path("data").path("users");
            JsonNode usersListNode = usersNode.path("users");
            int findId = usersNode.path("findId").asInt();
            int n = usersNode.path("n").asInt();

            // building graph
            Map<Integer, List<Integer>> followsMap = new HashMap<>();
            for (JsonNode user : usersListNode) {
                int id = user.path("id").asInt();
                List<Integer> follows = new ArrayList<>();
                for (JsonNode f : user.path("follows")) {
                    follows.add(f.asInt());
                }
                followsMap.put(id, follows);
            }

            // using BFS 
            Set<Integer> visited = new HashSet<>();
            Queue<Integer> queue = new LinkedList<>();
            queue.add(findId);
            visited.add(findId);
            int level = 0;
            while (!queue.isEmpty() && level < n) {
                int size = queue.size();
                for (int i = 0; i < size; i++) {
                    int curr = queue.poll();
                    for (int next : followsMap.getOrDefault(curr, Collections.emptyList())) {
                        if (!visited.contains(next)) {
                            queue.add(next);
                            visited.add(next);
                        }
                    }
                }
                level++;
            }
            //users at nth level
            List<Integer> outcome = new ArrayList<>(queue);
            Collections.sort(outcome);

            Map<String, Object> result = new HashMap<>();
            result.put("regNo", "REG12347");
            result.put("outcome", outcome);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File("result.json"), result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Retryable(maxAttempts = 4, backoff = @Backoff(delay = 1000))
    public void sendResultWithRetry(String webhookUrl, String accessToken, Map<String, Object> result) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", accessToken);
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(result), headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException("Failed to POST result: " + response.getStatusCode());
            }
            System.out.println("Successfully posted result to webhook.");
        } catch (Exception e) {
            System.err.println("Error posting to webhook, will retry: " + e.getMessage());
            throw e;
        }
    }
}
