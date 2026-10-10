import type Anthropic from "@anthropic-ai/sdk";
import type { z } from "zod";
import { FALLBACK, anthropic, textOf, usageOf, type UsageSummary } from "../lib/anthropic";
import type { ModelSpec } from "../lib/models";

export class InsightGenerationError extends Error {}

/**
 * One structured-output call: frozen (cached) system prompt + per-user context, JSON-schema-constrained
 * reply validated with Zod. Throws InsightGenerationError on refusal or unusable output.
 */
export async function generateStructured<T>(args: {
  model: ModelSpec;
  system: string;
  context: string;
  jsonSchema: Record<string, unknown>;
  schema: z.ZodType<T>;
  maxTokens?: number;
}): Promise<{ value: T; usage: UsageSummary }> {
  const message = await anthropic().beta.messages.create({
    ...FALLBACK,
    model: args.model.id,
    max_tokens: args.maxTokens ?? 8000,
    system: [{ type: "text", text: args.system, cache_control: { type: "ephemeral" } }],
    messages: [{ role: "user", content: args.context }],
    output_config: { effort: args.model.effort, format: { type: "json_schema", schema: args.jsonSchema } },
  } satisfies Anthropic.Beta.MessageCreateParamsNonStreaming);

  if (message.stop_reason === "refusal") throw new InsightGenerationError("Model declined to write this insight");
  if (message.stop_reason === "max_tokens") throw new InsightGenerationError("Insight output was truncated");

  let parsed: unknown;
  try {
    parsed = JSON.parse(textOf(message.content));
  } catch {
    throw new InsightGenerationError("Insight output was not valid JSON");
  }
  const result = args.schema.safeParse(parsed);
  if (!result.success) throw new InsightGenerationError(`Insight output failed validation: ${result.error.message}`);
  return { value: result.data, usage: usageOf(message) };
}
