rootProject.name = "llm-gateway"

include(
    "core-domain",
    "application",
    "adapter-tokenizer-bpe",
    "adapter-provider-anthropic",
    "adapter-provider-openai",
    "adapter-provider-gemini",
    "adapter-web",
    "bootstrap",
)
