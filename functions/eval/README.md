# Meal agent eval

41 text-only meals (`cases.ts`): Indian home and restaurant food, Indian packaged brands, Hinglish
phrasing ("2 roti aur dal"), global foods, and one non-food ("my keyboard"). The runner sends each to
the real meal agent (`runMealAgent` with `mealLogger`, Claude Haiku 5.5) and scores what comes back.
Web lookup is off; barcodes go to Open Food Facts; USDA uses `DEMO_KEY` unless `USDA_FDC_API_KEY` is set.

```sh
cd functions
ANTHROPIC_API_KEY=sk-ant-… npx tsx eval/run.ts                  # all cases, 3 at a time
ANTHROPIC_API_KEY=sk-ant-… npx tsx eval/run.ts --only=maggi,banana --concurrency=1
npx tsx eval/run.ts --self-test                                   # checks the scoring, no API calls
npx tsc -p eval                                                   # type-checks eval/ with src/
```

Set `MASTRA_PLATFORM_ACCESS_TOKEN` and `MASTRA_PROJECT_ID` to see each run's trace (tagged `eval`
and the case id) on the Mastra platform. A summary table prints at the end and the full results are
written to `eval/results/<timestamp>.json` (git-ignored).

## Metrics

- **grounding**: share of logged items backed by a source (a table entry, a dish built from table
  ingredients, a label or a product), not an unverified estimate.
- **source match**: expected foods paired with a distinct logged item whose source id is acceptable
  for that food (exact ids, regexes, `fdc:`/`off:` prefixes, or `components`).
- **kcal in range**: the meal's total calories fall in the case's range. The range is computed at run
  time from a reference portion (table entries × grams stated in the comment on each case) ± the
  case's tolerance (25% by default, 30–40% where portions vary a lot), so it follows `data/food-db.json`.
- **steps / max request**: agent steps used and the largest single request's input tokens, which must
  stay under 99,000 (Haiku 5.5's lower price tier ends at 100K).
- **cost**: Haiku 5.5 rates for prompts ≤ 100K tokens: $0.10/M input, $0.50/M output, cache reads
  $0.01/M, 5-minute cache writes $0.125/M. `runMealAgent` reports uncached input, cache
  reads and cache writes separately (from the provider's raw usage when it has it).

A case passes when every expected food matched, the calories are in range and no request reached
the token cap; the non-food case passes only if nothing is logged.

Scoring is plain functions (`score.ts`), not Mastra scorers: the checks are deterministic and run
on the agent's structured result, so an LLM judge or the scorer pipeline would add nothing.

## Cost of a full run

About 3 steps and 15–30K input tokens per meal, so roughly $0.002–0.004 a meal and **$0.10–0.20 for
all 41 cases**. The exact figure is printed at the end of each run.

## Interpreting portions and failures

Source allowlists include the cooked CoFID entries for pigeon-pea dal (15-780), Punjabi kidney-bean
curry (15-146), and bhindi subji (15-627). The self-test checks these entries and rejects chapati
as a match for those dishes. Composed items retain their ingredient source IDs, which the scorer also checks.

The roti/toor-dal, rajma-chawal, porridge, and cappuccino cases state serving weights explicitly. Earlier versions
left those portions unspecified while scoring against one fixed portion, so results before this
change are not directly comparable. Other cases still test typical serving assumptions; a calorie
failure alone can reflect portion ambiguity rather than incorrect nutrient data. The runner exits
with status 1 when a case fails and prints its missing foods and calorie range.

The text suite disables web lookup, so it does not validate Anthropic web search, photographs, label OCR,
voice recognition, or the phone's barcode camera. Test those paths separately.

## Web search providers

The production Mastra `web_lookup` tool uses Anthropic Haiku's native web search first, with the
existing Anthropic key. If no usable or verified result is returned, it tries OpenRouter when
`OPENROUTER_API_KEY` is configured. The default backup is `openai/gpt-4.1-mini` with OpenRouter's
Parallel basic search engine; set `OPENROUTER_SEARCH_MODEL` to reuse a different configured model.
Each provider gets at most one search per lookup (up to two searches when fallback is needed).
The existing per-meal and daily lookup limits and shared 30-day cache still apply. Both providers'
source pages go through the same verification check; citations alone do not mark a result verified.

Before deploying, bind `OPENROUTER_API_KEY` in Firebase Secret Manager. Gemini is no longer a
runtime dependency. The direct Anthropic path was tested live; OpenRouter needs a configured key
for live verification. Unit tests cover primary success, fallback success and both providers failing.

The current page check confirms numeric presence, not a full semantic match of food, serving and
nutrient labels. Treat web nutrition as less reliable than a matching food-table or label entry.
