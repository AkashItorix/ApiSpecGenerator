package com.itorix.apiwiz.enhance.apiwiz;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.util.Json;
import io.swagger.v3.oas.models.Operation;
import java.io.IOException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;


public class Enhancer {

    public static Operation enhance(Operation operation, String openaiApiKey, String openAiUrl, String openAIModel) throws IOException {
        final String prompt ="Enhance the operation with description, summary and other things while STRICTLY PRESERVING its structure. Give it as an Operation Json String ( not with any other values ). Don't add any bodies or anything if not there. Don't add any null values as well . Don't add any new fields or properties to any schema . Make it compliant with io.swagger.v3.oas.models.Operation. Don't change any fields in request body or response body, leave it as it is. Don't add any examples.";

//        String filesLocation = args[0];
//        String openaiApiKey = args[1];
//        String openAiUrl = args[2];
//        String openAIModel = args[3];
//        String customPrompt = "";
//        if(args[4] != null) {
//            customPrompt = args[4];
//        }
//        String customPromptString = (!customPrompt.isEmpty())
//                ? ("\nAdditional requirements from user context:\n" + customPrompt)
//                : "";
//        String finalPrompt = prompt + customPromptString;
//        File directory = new File(filesLocation);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", openAIModel);
        requestBody.put("temperature", 0.7);
        ArrayNode messages = objectMapper.createArrayNode();
        RestTemplate restTemplate = new RestTemplate();
        messages.add(objectMapper.createObjectNode()
                .put("role", "system")
                .put("content", prompt));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey);
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
//        if (directory.exists() && directory.isDirectory()) {
//            File[] files = directory.listFiles();
//            if (files != null) {
//                for (File file : files) {
                    String content = objectMapper.writeValueAsString(operation);
                    messages.add(objectMapper.createObjectNode()
                            .put("role", "user")
                            .put("content", content));
                    requestBody.set("messages", messages);
                    HttpEntity<?> request = new HttpEntity<>(requestBody, headers);
                    ResponseEntity<String> exchange = restTemplate.exchange(openAiUrl,
                            HttpMethod.POST, request, String.class);
                    if(exchange.getStatusCode().is2xxSuccessful()) {
                        try {
                        JsonNode responseNode = objectMapper.readValue(exchange.getBody(), JsonNode.class);
                        JsonNode choicesNode = responseNode.get("choices");
                        JsonNode firstChoice = choicesNode.get(0);
                        JsonNode contentNode = firstChoice.get("message");
                        String responseContent = contentNode.get("content").asText();
                        if(!responseContent.contains("```json")){
                            responseContent = responseContent.replaceAll("\"exampleSetFlag\"\\s*:\\s*true\\s*,?", "");
                            responseContent = responseContent.replaceAll("\"exampleSetFlag\"\\s*:\\s*false\\s*,?", "");
                            responseContent = responseContent.replaceAll("\"valueSetFlag\"\\s*:\\s*true\\s*,?", "");
                            responseContent = responseContent.replaceAll("\"valueSetFlag\"\\s*:\\s*false\\s*,?", "");
                            responseContent = responseContent.replaceAll("\"style\"\\s*:\\s*\"SIMPLE\"", "\"style\": \"simple\"");
                            responseContent = responseContent.replaceAll("\"style\"\\s*:\\s*\"FORM\"", "\"style\": \"form\"");
                            responseContent = responseContent.replaceAll("\"type\"\\s*:\\s*\"OAUTH2\"", "\"type\": \"oauth2\"");
                            responseContent = responseContent.replaceAll("\"type\"\\s*:\\s*\"HTTP\"", "\"type\": \"http\"");
                            responseContent = responseContent.replaceAll("\"type\"\\s*:\\s*\"OPENIDCONNECT\"", "\"type\": \"openIdConnect\"");
                            responseContent = responseContent.replaceAll("\"type\"\\s*:\\s*\"APIKEY\"", "\"type\": \"apiKey\"");
                            responseContent = responseContent.replaceAll(",\\s*}", " }");
//                            System.out.println("After Enhancement + \n" + Json.pretty(responseContent));
                            JsonNode node = objectMapper.readTree(responseContent);
                            Operation op;
                            if (node.isTextual()) {
                                // It was a stringified JSON
                                op = objectMapper.readValue(node.asText(), Operation.class);
                            } else {
                                // It was a proper JSON object
                                op = objectMapper.treeToValue(node, Operation.class);
                            }
//                            System.out.println("Operation \n" + Json.pretty(op));
                            return op;
//                                String title = jsonNode.get("info").get("title").asText();
//                            FileWriter writer = null;
//                            try {
//                                writer = new FileWriter(file);
//                                writer.write(responseContent);
//                            } finally {
//                                if (writer != null) {
//                                    try {
//                                        writer.close();
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                            }
//                                File renamedFile = new File(file.getParent() + File.separator +  title + ".json");
//                                file.renameTo(renamedFile);
                        }
                        }catch (Exception ex){
                                ex.printStackTrace();
                                return operation;
                        }
                    }
//                }
//            }
//        }
//        System.exit(200);
        return operation;
    }
}
