package io.mofazzal.sentinel.agent.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiToolCallingLoopTest {

    @Test
    void advisorExecutesJavaToolFeedsResultBackAndLetsModelContinue() {
        AtomicInteger toolInvocations = new AtomicInteger();
        class ReadTool {
            @Tool(description = "Return bounded deployment evidence")
            String lookup(String service) {
                toolInvocations.incrementAndGet();
                return "deploy-v2-for-" + service;
            }
        }
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel scriptedModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                if (modelCalls.incrementAndGet() == 1) {
                    var toolCall = new AssistantMessage.ToolCall(
                            "call-1", "function", "lookup", "{\"service\":\"payments-api\"}");
                    ChatResponse toolResponse = response(AssistantMessage.builder().content("")
                            .toolCalls(List.of(toolCall)).build());
                    assertThat(toolResponse.hasToolCalls()).isTrue();
                    return toolResponse;
                }
                assertThat(prompt.getInstructions())
                        .filteredOn(ToolResponseMessage.class::isInstance)
                        .singleElement()
                        .satisfies(message -> assertThat(((ToolResponseMessage) message).getResponses())
                                .singleElement()
                                .satisfies(result -> assertThat(result.responseData())
                                        .contains("deploy-v2-for-payments-api")));
                return response(new AssistantMessage("Evidence gathered"));
            }
        };

        var callbacks = MethodToolCallbackProvider.builder().toolObjects(new ReadTool())
                .build().getToolCallbacks();
        var options = ToolCallingChatOptions.builder().toolCallbacks(callbacks).build();
        Prompt firstPrompt = new Prompt("Gather deployment evidence", options);
        ChatResponse toolRequest = scriptedModel.call(firstPrompt);
        var execution = ToolCallingManager.builder().build().executeToolCalls(firstPrompt, toolRequest);
        ChatResponse finalResponse = scriptedModel.call(new Prompt(execution.conversationHistory(), options));
        String result = finalResponse.getResult().getOutput().getText();

        assertThat(modelCalls).hasValue(2);
        assertThat(toolInvocations).hasValue(1);
        assertThat(result).isEqualTo("Evidence gathered");
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }
}
