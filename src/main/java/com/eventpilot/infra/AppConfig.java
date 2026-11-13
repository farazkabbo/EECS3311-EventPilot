package com.eventpilot.infra;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application configuration. Values are read, in order of priority, from
 * real environment variables, then from a local {@code .env} file (git-ignored),
 * then from built-in defaults. API keys are never hard-coded.
 */
public final class AppConfig {

    private final Map<String, String> values = new HashMap<>();

    private AppConfig() {
    }

    /** Loads configuration from {@code .env} in the working directory plus the environment. */
    public static AppConfig load() {
        return load(Path.of(".env"), System.getenv());
    }

    public static AppConfig load(Path envFile, Map<String, String> environment) {
        AppConfig config = new AppConfig();
        if (envFile != null && Files.isRegularFile(envFile)) {
            try {
                config.values.putAll(parseEnvFile(Files.readAllLines(envFile)));
            } catch (IOException e) {
                System.err.println("Warning: could not read " + envFile + ": " + e.getMessage());
            }
        }
        environment.forEach((k, v) -> {
            if (k.startsWith("LLM_") || k.startsWith("ANTHROPIC_") || k.startsWith("OLLAMA_")
                    || k.startsWith("EVENTPILOT_")) {
                config.values.put(k, v);
            }
        });
        return config;
    }

    /** Creates a configuration from explicit values (used by tests). */
    public static AppConfig of(Map<String, String> values) {
        AppConfig config = new AppConfig();
        config.values.putAll(values);
        return config;
    }

    static Map<String, String> parseEnvFile(List<String> lines) {
        Map<String, String> result = new HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    public Optional<String> get(String key) {
        String v = values.get(key);
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    public String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /** The configured provider; falls back to the offline mock when no Claude key is set. */
    public String llmProvider() {
        String provider = get("LLM_PROVIDER", "claude").toLowerCase();
        if (provider.equals("claude") && anthropicApiKey().isEmpty()) {
            return "mock";
        }
        return provider;
    }

    public Optional<String> anthropicApiKey() {
        return get("ANTHROPIC_API_KEY").filter(k -> !k.equals("YOUR_LOCAL_API_KEY_HERE"));
    }

    public String mainModel() {
        return get("LLM_MODEL", "claude-sonnet-5-5");
    }

    public String lightModel() {
        return get("LLM_LIGHT_MODEL", "claude-haiku-5-5");
    }

    public String ollamaUrl() {
        return get("OLLAMA_URL", "http://localhost:11434");
    }

    public String ollamaModel() {
        return get("OLLAMA_MODEL", "llama3.1:8b");
    }

    public Path dataDir() {
        return Path.of(get("EVENTPILOT_DATA_DIR", "data"));
    }
}
