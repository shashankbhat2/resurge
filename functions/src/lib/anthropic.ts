import Anthropic from "@anthropic-ai/sdk";
import { defineSecret } from "firebase-functions/params";

/** Set with: firebase functions:secrets:set ANTHROPIC_API_KEY */
export const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");

let client: Anthropic | undefined;

export function anthropic(): Anthropic {
  client ??= new Anthropic({ apiKey: ANTHROPIC_API_KEY.value(), maxRetries: 2 });
  return client;
}

/**
 * Server-side refusal fallback (Claude API): if a safety classifier declines, the API re-runs the
 * request on Anthropic's recommended model for that refusal category instead of returning a refusal.
 * Haiku has no server-side fallback, so its requests go without it.
 */
const FALLBACK = {
  betas: ["server-side-fallback-2026-07-01"] as Anthropic.Beta.AnthropicBeta[],
  fallbacks: "default" as const,
};

export function fallbackFor(modelId: string): Partial<typeof FALLBACK> {
  return modelId.startsWith("claude-haiku-") ? {} : FALLBACK;
}

/** Concatenated text of a response's text blocks. */
export function textOf(content: Anthropic.Beta.BetaContentBlock[]): string {
  return content
    .filter((b): b is Anthropic.Beta.BetaTextBlock => b.type === "text")
    .map((b) => b.text)
    .join("")
    .trim();
}

export interface UsageSummary {
  model: string;
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
}

export function usageOf(message: Anthropic.Beta.BetaMessage): UsageSummary {
  return {
    model: message.model,
    inputTokens: message.usage.input_tokens,
    outputTokens: message.usage.output_tokens,
    cacheReadTokens: message.usage.cache_read_input_tokens ?? 0,
    cacheWriteTokens: message.usage.cache_creation_input_tokens ?? 0,
  };
}
