import { Mastra } from "@mastra/core/mastra";
import type { AnySpan, SpanOutputProcessor } from "@mastra/core/observability";
import { MastraPlatformExporter, Observability } from "@mastra/observability";
import { mealLogger } from "../food/agent";

import { MASTRA_PLATFORM_ACCESS_TOKEN, MASTRA_PROJECT_ID } from "../food/secrets";

/**
 * Mastra hosts the agents; traces go to the Mastra platform (projects.mastra.ai). Without a token
 * the exporter discards traces and everything else works.
 */

const LONG_BASE64 = /^[A-Za-z0-9+/=\s]{2000,}$/;

/**
 * Meal photos travel in message content as base64. Keep them out of exported traces: they are
 * large, and the trace only needs to show that a photo was sent.
 */
export class OmitImagesProcessor implements SpanOutputProcessor {
  name = "omit-images";

  process(span?: AnySpan): AnySpan | undefined {
    if (!span) return span;
    span.input = scrub(span.input);
    span.output = scrub(span.output);
    return span;
  }

  async shutdown(): Promise<void> {}
}

export function scrub(value: unknown, depth = 0): unknown {
  if (depth > 12) return value;
  if (typeof value === "string") return LONG_BASE64.test(value) ? `[image omitted, ${value.length} chars]` : value;
  if (value instanceof Uint8Array) return `[binary omitted, ${value.byteLength} bytes]`;
  if (Array.isArray(value)) return value.map((v) => scrub(v, depth + 1));
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value)) out[k] = scrub(v, depth + 1);
    return out;
  }
  return value;
}

let instance: Mastra | undefined;

/** The Mastra instance, created on first use (secrets are only readable inside a request). */
export function mastra(): Mastra {
  instance ??= new Mastra({
    agents: { mealLogger },
    observability: new Observability({
      configs: {
        default: {
          serviceName: "resurge-functions",
          exporters: [
            new MastraPlatformExporter({
              accessToken: secretOrEnv(MASTRA_PLATFORM_ACCESS_TOKEN),
              projectId: secretOrEnv(MASTRA_PROJECT_ID),
              resolveFromEnv: false,
            }),
          ],
          spanOutputProcessors: [new OmitImagesProcessor()],
        },
      },
    }),
  });
  return instance;
}

function secretOrEnv(secret: typeof MASTRA_PROJECT_ID): string | undefined {
  try {
    return secret.value() || process.env[secret.name] || undefined;
  } catch {
    return process.env[secret.name] || undefined;
  }
}

/** Sends buffered spans before the function returns: Cloud Functions may freeze the instance after. */
export async function flushTraces(): Promise<void> {
  try {
    await instance?.observability?.flush();
  } catch {
    // Tracing must never fail a request.
  }
}
