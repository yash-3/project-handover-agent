package com.yash.handover.core;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Stand-in model: returns a fixed answer and token counts, and remembers the prompts it saw. */
final class FakeChatModel implements ChatModel {

    final List<Prompt> prompts = new ArrayList<>();
    private final String answer;
    private final int in;
    private final int out;

    FakeChatModel(String answer, int in, int out) {
        this.answer = answer;
        this.in = in;
        this.out = out;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(answer))))
                .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(in, out)).build())
                .build();
    }
}
