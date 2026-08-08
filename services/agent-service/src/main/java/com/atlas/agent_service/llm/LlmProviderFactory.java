package com.atlas.agent_service.llm;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LlmProviderFactory {

    private final Map<String, LlmProvider> providers;

    public LlmProviderFactory(List<LlmProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(LlmProvider::name, Function.identity()));
    }

    public LlmProvider getProvider(String name) {
        LlmProvider provider = providers.get(name.toLowerCase());
        if (provider == null) {
            throw new LlmException("Unsupported LLM provider: " + name
                    + ". Supported: " + providers.keySet());
        }
        return provider;
    }
}
