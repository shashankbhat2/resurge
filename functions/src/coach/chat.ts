import Anthropic from "@anthropic-ai/sdk";
import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { ANTHROPIC_API_KEY, FALLBACK, anthropic, textOf, usageOf } from "../lib/anthropic";
import { userDoc } from "../lib/firebase";
import { LIMITS, MODELS, type ModelChoice } from "../lib/models";
import { consumeOrThrow, recordUsage } from "../lib/quota";
import { isValidTimeZone, localParts } from "../lib/time";
import { COACH_SYSTEM } from "../prompts/coach";
import { buildSnapshot } from "./snapshot";
import { COACH_TOOLS, runTool } from "./tools";

/** Flip to true once App Check (Play Integrity + debug tokens) is registered in the Firebase console. */
export const ENFORCE_APP_CHECK = false;

const MAX_TOOL_ROUNDS = 4;

const Input = z.object({
  threadId: z.string().min(1).max(64).optional(),
  text: z.string().trim().min(1).max(4000),
  model: z.enum(["fast", "smart"]),
});

/** Chunks streamed to the app while a reply is generated. */
type Chunk =
  | { type: "thread"; threadId: string }
  | { type: "delta"; text: string }
  | { type: "status"; status: "tool"; name: string };

/**
 * One stored conversation turn, appended in order under coachThreads/{threadId}/turns/{seq}.
 * `raw` is the exact content sent to / received from Claude (JSON), replayed unchanged on later
 * requests; `display` is what the app shows (null for tool plumbing and context).
 */
interface Turn {
  seq: number;
  role: "user" | "assistant" | "system";
  kind: "message" | "tool_result" | "context" | "notice";
  raw: string | null;
  display: string | null;
  model?: ModelChoice;
  excluded?: boolean;
}

/**
 * Coach chat. Streams the reply to the app (callable streaming), runs read-only data tools,
 * and stores the full conversation append-only so it can be replayed exactly next time.
 */
export const coachChat = onCall(
  {
    secrets: [ANTHROPIC_API_KEY],
    enforceAppCheck: ENFORCE_APP_CHECK,
    timeoutSeconds: 300,
    memory: "512MiB",
  },
  async (request, response) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in to chat with your coach.");
    const parsed = Input.safeParse(request.data);
    if (!parsed.success) throw new HttpsError("invalid-argument", "Invalid message.");
    const { text, model: choice } = parsed.data;
    const spec = MODELS[choice];

    const user = (await userDoc(uid).get()).data() ?? {};
    const tz = isValidTimeZone(user.timezone) ? (user.timezone as string) : "UTC";
    const today = localParts(tz).date;
    await consumeOrThrow(uid, today, choice === "smart" ? "smartTurns" : "fastTurns",
      choice === "smart" ? LIMITS.smartTurns : LIMITS.fastTurns);

    const send = (chunk: Chunk) => {
      if (request.acceptsStreaming) response?.sendChunk(chunk);
    };

    // --- Thread + history ---
    const threads = userDoc(uid).collection("coachThreads");
    const threadRef = parsed.data.threadId ? threads.doc(parsed.data.threadId) : threads.doc();
    const thread = await threadRef.get();
    if (parsed.data.threadId && !thread.exists) throw new HttpsError("not-found", "Conversation not found.");
    send({ type: "thread", threadId: threadRef.id });

    const history = thread.exists ? await loadTurns(threadRef) : [];
    let nextSeq = history.length === 0 ? 0 : history[history.length - 1].seq + 1;
    const appended: Turn[] = [];
    const append = async (turn: Omit<Turn, "seq">): Promise<Turn> => {
      const full: Turn = { ...turn, seq: nextSeq++ };
      appended.push(full);
      await threadRef.collection("turns").doc(seqId(full.seq)).set({ ...full, createdAt: FieldValue.serverTimestamp() });
      return full;
    };

    if (!thread.exists) {
      await threadRef.set({
        title: text.length > 60 ? `${text.slice(0, 57)}…` : text,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        contextDate: today,
        lastPreview: text,
      });
    }

    // New thread: the health snapshot rides along with the first user message. Later days on an
    // existing thread: append a mid-conversation system message with the new snapshot instead of
    // editing anything earlier (keeps the cached prefix and history intact).
    const userContent: Anthropic.Beta.BetaContentBlockParam[] = thread.exists
      ? [{ type: "text", text }]
      : [{ type: "text", text: await buildSnapshot(uid, today) }, { type: "text", text }];
    const userTurn = await append({ role: "user", kind: "message", raw: JSON.stringify(userContent), display: text });
    let contextTurn: Turn | null = null;
    if (thread.exists && thread.get("contextDate") !== today) {
      const snapshot = await buildSnapshot(uid, today);
      contextTurn = await append({ role: "system", kind: "context", raw: JSON.stringify(snapshot), display: null });
      await threadRef.update({ contextDate: today });
    }

    const messages: Anthropic.Beta.BetaMessageParam[] = [...history, ...appended]
      .filter((t) => !t.excluded && t.raw != null && t.kind !== "notice")
      .map((t) => ({ role: t.role, content: JSON.parse(t.raw!) }));

    // --- Generate (with tool loop) ---
    let finalText = "";
    try {
      for (let round = 0; round <= MAX_TOOL_ROUNDS; round++) {
        const stream = anthropic().beta.messages.stream({
          ...FALLBACK,
          model: spec.id,
          max_tokens: 16000,
          system: [{ type: "text", text: COACH_SYSTEM, cache_control: { type: "ephemeral" } }],
          cache_control: { type: "ephemeral" }, // also cache the conversation so far
          tools: round < MAX_TOOL_ROUNDS ? COACH_TOOLS : [],
          thinking: { type: "adaptive" },
          output_config: { effort: spec.effort },
          messages,
        });
        stream.on("text", (delta) => send({ type: "delta", text: delta }));
        const message = await stream.finalMessage();
        await recordUsage(uid, today, usageOf(message));

        if (message.stop_reason === "refusal") {
          await markRefused(threadRef, [userTurn, contextTurn], append);
          return { threadId: threadRef.id, text: REFUSAL_NOTICE, refused: true };
        }

        const replyText = textOf(message.content);
        await append({ role: "assistant", kind: "message", raw: JSON.stringify(message.content), display: replyText || null, model: choice });
        messages.push({ role: "assistant", content: message.content });
        if (replyText) finalText = replyText;

        const toolUses = message.content.filter((b): b is Anthropic.Beta.BetaToolUseBlock => b.type === "tool_use");
        if (message.stop_reason !== "tool_use" || toolUses.length === 0) break;

        const results: Anthropic.Beta.BetaToolResultBlockParam[] = [];
        for (const use of toolUses) {
          send({ type: "status", status: "tool", name: use.name });
          const r = await runTool(uid, use.name, use.input);
          results.push({ type: "tool_result", tool_use_id: use.id, content: r.content, is_error: r.isError });
        }
        await append({ role: "user", kind: "tool_result", raw: JSON.stringify(results), display: null });
        messages.push({ role: "user", content: results });
      }
    } catch (err) {
      logger.error("coachChat failed", { uid, threadId: threadRef.id, err: String(err) });
      await markRefused(threadRef, [userTurn, contextTurn], append, ERROR_NOTICE);
      if (err instanceof Anthropic.RateLimitError) throw new HttpsError("resource-exhausted", "The coach is busy. Try again in a moment.");
      throw new HttpsError("unavailable", "The coach couldn't reply. Try again.");
    }

    await threadRef.update({ updatedAt: FieldValue.serverTimestamp(), lastPreview: finalText.slice(0, 140), lastModel: choice });
    return { threadId: threadRef.id, text: finalText };
  },
);

const REFUSAL_NOTICE = "I can't help with that one. Try asking in a different way, or about another part of your health data.";
const ERROR_NOTICE = "Something went wrong and I couldn't reply. Please try again.";

/**
 * Leaves the unanswered turns out of future requests (they're marked, never deleted) and adds a
 * display-only notice so the app can show what happened.
 */
async function markRefused(
  threadRef: FirebaseFirestore.DocumentReference,
  turns: (Turn | null)[],
  append: (turn: Omit<Turn, "seq">) => Promise<Turn>,
  notice: string = REFUSAL_NOTICE,
) {
  await Promise.all(
    turns.filter((t): t is Turn => t != null).map((t) => threadRef.collection("turns").doc(seqId(t.seq)).update({ excluded: true })),
  );
  await append({ role: "assistant", kind: "notice", raw: null, display: notice, excluded: true });
}

async function loadTurns(threadRef: FirebaseFirestore.DocumentReference): Promise<Turn[]> {
  const snap = await threadRef.collection("turns").orderBy("seq").get();
  return snap.docs.map((d) => d.data() as Turn);
}

/** Zero-padded so document IDs sort in conversation order. */
export const seqId = (seq: number) => String(seq).padStart(6, "0");
