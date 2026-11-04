package com.example.shoppingagent.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The agent loop. Gemini may answer directly, or ask us to run a tool first.
 * We keep going until it produces a text answer (or we hit MAX_ROUNDS).
 *
 *   user question -> Gemini -> [function call -> run tool -> send result back -> Gemini] ... -> text
 */
@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** Safety cap so a confused model cannot loop (and spend money) forever. */
    static final int MAX_ROUNDS = 5;

    /** Every web search costs a SerpApi credit, so limit them per customer question. */
    static final int MAX_WEB_SEARCHES = 3;

    private final GeminiClient gemini;
    private final ShoppingTools tools;
    private final ObjectMapper mapper;

    public AgentService(GeminiClient gemini, ShoppingTools tools, ObjectMapper mapper) {
        this.gemini = gemini;
        this.tools = tools;
        this.mapper = mapper;
    }

    public String chat(String userMessage) {
        // "contents" is the whole conversation. Gemini is stateless: we resend it on every call.
        List<JsonNode> contents = new ArrayList<>();
        contents.add(userContent(userMessage));
        int webSearches = 0;

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            JsonNode modelContent = gemini.generateContent(contents, tools.toolsPayload());
            contents.add(modelContent); // keep the model's turn exactly as received

            List<JsonNode> calls = functionCalls(modelContent);
            if (calls.isEmpty()) {
                return textOf(modelContent);
            }

            // Run every tool the model asked for, and send all results back in ONE turn.
            ObjectNode resultTurn = mapper.createObjectNode().put("role", "user");
            ArrayNode resultParts = resultTurn.putArray("parts");
            for (JsonNode call : calls) {
                String name = call.path("name").asText();
                JsonNode args = call.path("args");
                log.info("Round {}: model called tool {} with {}", round, name, args);
                Map<String, Object> result;
                if (ShoppingTools.SEARCH_WEB_PRODUCTS.equals(name) && ++webSearches > MAX_WEB_SEARCHES) {
                    result = Map.of("error", "Web search limit reached for this question. "
                            + "Answer with the results you already have.");
                } else {
                    result = tools.execute(name, args);
                }

                ObjectNode functionResponse = resultParts.addObject().putObject("functionResponse");
                functionResponse.put("name", name);
                functionResponse.set("response", mapper.valueToTree(result));
            }
            contents.add(resultTurn);
        }
        throw new GeminiException(HttpStatus.BAD_GATEWAY,
                "The assistant did not finish within " + MAX_ROUNDS + " steps");
    }

    private ObjectNode userContent(String text) {
        ObjectNode content = mapper.createObjectNode().put("role", "user");
        content.putArray("parts").addObject().put("text", text);
        return content;
    }

    private static List<JsonNode> functionCalls(JsonNode content) {
        List<JsonNode> calls = new ArrayList<>();
        for (JsonNode part : content.path("parts")) {
            if (part.has("functionCall")) calls.add(part.get("functionCall"));
        }
        return calls;
    }

    private static String textOf(JsonNode content) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : content.path("parts")) {
            if (part.has("text") && !part.path("thought").asBoolean(false)) sb.append(part.get("text").asText());
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            throw new GeminiException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty answer");
        }
        return text;
    }
}
