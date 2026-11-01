package com.snakesafe.config;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.snakesafe.web.ApiException;

/** Builds the Claude client on first use. The key is read from AI_API_KEY and never leaves the server. */
@Component
public class AiClient {

    private final AppProperties props;
    private volatile AnthropicClient client;

    public AiClient(AppProperties props) {
        this.props = props;
    }

    public AnthropicClient get() {
        AnthropicClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    String key = props.ai().apiKey();
                    if (key == null || key.isBlank()) {
                        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                                "The assistant is not set up on this server. If someone was bitten, call "
                                        + props.emergencyNumber() + ".");
                    }
                    local = AnthropicOkHttpClient.builder().apiKey(key).build();
                    client = local;
                }
            }
        }
        return local;
    }

    public String model() {
        return props.ai().model();
    }
}
