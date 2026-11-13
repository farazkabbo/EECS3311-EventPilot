package com.eventpilot.agent.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete strategy and Adapter: translates our provider-neutral request into an
 * Anthropic Messages API call through the official Java SDK, and the reply
 * (text and tool_use blocks) back into an {@link LLMResponse}.
 */
public class ClaudeProvider implements LLMProvider {

    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String mainModel;
    private final String lightModel;

    public ClaudeProvider(String apiKey, String mainModel, String lightModel) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .maxRetries(0) // LLMClient does the retrying
                .timeout(Duration.ofSeconds(120))
                .build();
        this.mainModel = mainModel;
        this.lightModel = lightModel;
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public String modelFor(LLMRequest.Tier tier) {
        return tier == LLMRequest.Tier.LIGHT ? lightModel : mainModel;
    }

    @Override
    public LLMResponse chat(LLMRequest request) throws LLMException {
        String model = modelFor(request.tier());
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .outputConfig(OutputConfig.builder()
                        .effort(request.tier() == LLMRequest.Tier.LIGHT ? OutputConfig.Effort.LOW : OutputConfig.Effort.MEDIUM)
                        .build());
        if (supportsServerFallback(model)) {
            // On a safety refusal the API retries on a fallback model instead of just stopping.
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        for (LLMRequest.ToolSpec spec : request.tools()) {
            builder.addTool(toTool(spec));
        }
        for (ChatMessage m : request.messages()) {
            switch (m.role()) {
                case USER -> builder.addUserMessage(m.text());
                case ASSISTANT -> builder.addAssistantMessageOfContentBlockParams(assistantBlocks(m));
                case TOOL_RESULTS -> builder.addUserMessageOfContentBlockParams(toolResultBlocks(m));
            }
        }

        Message message;
        try {
            message = client.messages().create(builder.build());
        } catch (AnthropicServiceException e) {
            boolean retryable = e.statusCode() == 429 || e.statusCode() == 529 || e.statusCode() >= 500;
            throw new LLMException("Claude API error " + e.statusCode() + ": " + e.getMessage(), retryable, e);
        } catch (AnthropicIoException e) {
            throw new LLMException("Network error calling Claude: " + e.getMessage(), true, e);
        }

        String stop = message.stopReason().map(Object::toString).orElse("");
        if (stop.equals("refusal")) {
            throw new LLMException("The model declined this request.", false);
        }
        StringBuilder text = new StringBuilder();
        List<ChatMessage.ToolCall> calls = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(tu -> calls.add(new ChatMessage.ToolCall(tu.id(), tu.name(), toJson(tu._input()))));
        }
        return new LLMResponse(text.toString(), calls, stop, message.usage().inputTokens(),
                message.usage().outputTokens(), model);
    }

    static boolean supportsServerFallback(String model) {
        return model.startsWith("claude-sonnet-5-5") || model.startsWith("claude-opus-5") || model.startsWith("claude-fable");
    }

    @SuppressWarnings("unchecked")
    private static Tool toTool(LLMRequest.ToolSpec spec) {
        JsonNode schema = spec.inputSchema();
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        schema.path("properties").fields().forEachRemaining(e ->
                props.putAdditionalProperty(e.getKey(), JsonValue.from(Json.MAPPER.convertValue(e.getValue(), Object.class))));
        List<String> required = new ArrayList<>();
        schema.path("required").forEach(r -> required.add(r.asText()));
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(required).build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<ContentBlockParam> assistantBlocks(ChatMessage m) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (!m.text().isBlank()) {
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.text()).build()));
        }
        for (ChatMessage.ToolCall call : m.toolCalls()) {
            Map<String, Object> args = new LinkedHashMap<>();
            try {
                args = Json.MAPPER.readValue(call.argumentsJson(), Map.class);
            } catch (JsonProcessingException ignored) {
                // keep empty arguments; the registry already reported the problem to the model
            }
            ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
            args.forEach((k, v) -> input.putAdditionalProperty(k, JsonValue.from(v)));
            blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(call.id()).name(call.name()).input(input.build()).build()));
        }
        return blocks;
    }

    private static List<ContentBlockParam> toolResultBlocks(ChatMessage m) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (ChatMessage.ToolResult r : m.toolResults()) {
            blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(r.toolCallId()).content(r.content()).isError(r.isError()).build()));
        }
        return blocks;
    }

    private static String toJson(JsonValue value) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
