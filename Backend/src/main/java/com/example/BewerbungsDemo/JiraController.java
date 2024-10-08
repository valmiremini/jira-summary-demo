package com.example.BewerbungsDemo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.stream.Collectors;

// OpenAI-Importe
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.service.FunctionExecutor;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionResult;

import io.reactivex.Flowable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Base64;
import java.util.List;
import java.time.Duration;

@RestController
@RequestMapping("/api/issues")
@CrossOrigin(origins = "http://localhost:4200") // Angular Frontend erlauben
public class JiraController {

    @Value("${jira.url}")
    private String jiraUrl;

    @Value("${jira.auth.key}")
    private String jiraApiKey;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private String getJiraAuth() {
        return "Basic " + Base64.getEncoder().encodeToString(jiraApiKey.getBytes(StandardCharsets.UTF_8));
    }

        // Neuer Endpunkt zum Abrufen aller Projekte
        @GetMapping("/projects")
        public ResponseEntity<?> getProjects() {
            try {
                String projectsUrl = jiraUrl + "/project";
    
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", getJiraAuth());
                headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
    
                RestTemplate restTemplate = new RestTemplate();
                HttpEntity<String> entity = new HttpEntity<>(headers);
    
                ResponseEntity<List> response = restTemplate.exchange(
                    projectsUrl, HttpMethod.GET, entity, List.class
                );
    
                if (response.getStatusCode() != HttpStatus.OK) {
                    return ResponseEntity.status(response.getStatusCode()).body("Failed to fetch projects from Jira");
                }
    
                List<Map<String, Object>> projects = response.getBody();
    
                // Extrahieren der Projektinformationen
                List<Map<String, String>> projectList = new ArrayList<>();
                for (Map<String, Object> project : projects) {
                    String key = (String) project.get("key");
                    String name = (String) project.get("name");
                    Map<String, String> projectInfo = new HashMap<>();
                    projectInfo.put("key", key);
                    projectInfo.put("name", name);
                    projectList.add(projectInfo);
                }
    
                return ResponseEntity.ok(projectList);
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching projects: " + e.getMessage());
            }
        }


    // Holt alle Tickets eines Projekts und fasst die Kommentare zusammen
    @GetMapping("/{projectKey}/summarize-comments")
    public ResponseEntity<?> getAndSummarizeComments(@PathVariable String projectKey) {
        try {
            // Step 1: Holen der Tickets aus dem Jira-Projekt
            String searchUrl = jiraUrl + "/search?jql=project=" + URLEncoder.encode(projectKey, StandardCharsets.UTF_8.toString());
    
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", getJiraAuth());
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
    
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<String> entity = new HttpEntity<>(headers);
    
            ResponseEntity<Map> response = restTemplate.exchange(
                searchUrl, HttpMethod.GET, entity, Map.class
            );
    
            if (response.getStatusCode() != HttpStatus.OK) {
                return ResponseEntity.status(response.getStatusCode()).body("Failed to fetch issues from Jira");
            }
    
            // Step 2: Extrahieren der Beschreibungen und Kommentare aus allen Tickets
            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.getBody().get("issues");
            StringBuilder allContent = new StringBuilder();
    
            for (Map<String, Object> issue : issues) {
                String issueKey = (String) issue.get("key");
    
                // Holen der Beschreibung des Tickets
                Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
                Map<String, Object> descriptionMap = (Map<String, Object>) fields.get("description");
    
                if (descriptionMap != null) {
                    String description = descriptionMap.toString(); // Beschreibung des Tickets
                    allContent.append("Beschreibung des Tickets ").append(issueKey).append(": ").append(description).append("\n");
                }
    
                // Holen der Kommentare
                String commentsUrl = jiraUrl + "/issue/" + issueKey + "/comment";
    
                ResponseEntity<Map> commentsResponse = restTemplate.exchange(
                    commentsUrl, HttpMethod.GET, entity, Map.class
                );
    
                if (commentsResponse.getStatusCode() == HttpStatus.OK) {
                    List<Map<String, Object>> comments = (List<Map<String, Object>>) commentsResponse.getBody().get("comments");
                    for (Map<String, Object> comment : comments) {
                        Map<String, Object> commentBodyMap = (Map<String, Object>) comment.get("body");
                        if (commentBodyMap != null) {
                            String commentBody = commentBodyMap.get("content").toString();
                            allContent.append("Kommentar zu ").append(issueKey).append(": ").append(commentBody).append("\n");
                        }
                    }
                }
            }
    
            // Step 3: Verwenden von OpenAI zur Zusammenfassung der Beschreibungen und Kommentare
            if (allContent.length() == 0) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No content found for the specified project");
            }
    
            OpenAiService openAiService = new OpenAiService(openAiApiKey);
    
            List<ChatMessage> messages = new ArrayList<>();
            ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), "Du bist ein Assistent, der präzise und umfassende Release Notes basierend auf Jira-Ticket-Beschreibungen und deren Kommentaren erstellt. Deine Aufgabe ist es, so viele relevante Informationen wie möglich aus den Tickets und Kommentaren zu berücksichtigen, um die wichtigsten Features, Verbesserungen und Fehlerbehebungen zusammenzufassen. Achte darauf, ausschließlich auf die im Jira-System enthaltenen Fakten zuzugreifen, ohne zusätzliche Informationen hinzuzufügen oder zu spekulieren. Stelle sicher, dass die Stakeholder umfassend und korrekt informiert werden.");
            messages.add(systemMessage);
            ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), allContent.toString());
            messages.add(userMessage);
    
            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                    .builder()
                    .model("gpt-3.5-turbo")
                    .messages(messages)
                    .n(1)
                    .maxTokens(150)
                    .build();
    
            Flowable<ChatCompletionChunk> flowable = openAiService.streamChatCompletion(chatCompletionRequest);
    
            AtomicBoolean isFirst = new AtomicBoolean(true);
            ChatMessage chatMessage = openAiService.mapStreamToAccumulator(flowable)
                    .doOnNext(accumulator -> {
                        if (isFirst.getAndSet(true)) {
                            System.out.print(" Response: ");
                        }
                        if (accumulator.getMessageChunk().getContent() != null) {
                            System.out.print(accumulator.getMessageChunk().getContent());
                        }
                    })
                    .doOnComplete(System.out::println)
                    .lastElement()
                    .blockingGet()
                    .getAccumulatedMessage();
    
            String summary = chatMessage.getContent().trim();
    
            return ResponseEntity.ok(summary);
        } catch (UnsupportedEncodingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid project key encoding");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized: Please check your Jira credentials");
            } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("The requested Jira resource was not found: " + e.getResponseBodyAsString());
            }
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing request: " + e.getMessage());
        }
    }
    
}
