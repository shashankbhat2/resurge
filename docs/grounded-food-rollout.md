# Grounded food logging: Firebase rollout and merge gate

**Firebase must be updated and smoke-tested before PR #2 is merged.** The PR is not a deployment.
Deploy the reviewed PR commit first, verify the deployed behavior, and merge that same commit only
after the checklist below is complete. Do not release the new Android app before its backend exists.

## Target and changes

- Firebase project: `calorizz-3e749` (the repository's configured default).
- Firestore database: `resurge`; retain the named database rather than creating `(default)`.
- Functions codebase: `resurge`, Node.js 22.
- `analyzeMeal`: Mastra/Haiku grounded lookup, 1 GiB memory, 120-second timeout; existing auth,
  App Check configuration and daily scan limits stay in place.
- New `lookupBarcode` callable: authenticated product lookup without a model call.
- Firestore rules: accept meal source `barcode`; keep `foodProducts` and `foodWebCache` server-only.
- No database migration or backfill. Old meals remain readable; missing micronutrients stay unknown.
- Anthropic native search is primary. OpenRouter is backup only, with one search per provider per
  lookup, five lookups per user/day and a shared 30-day cache. A fallback may incur two searches.

## Before deployment

- [ ] Review the exact PR head commit; record it and the current deployed function revision for rollback.
- [ ] Confirm billing, Secret Manager access, function region, and the named Firestore database on the
  target project. Confirm Anthropic web search is enabled for the key's organization.
- [ ] Rotate the OpenRouter key shared in chat and use the replacement for production.
- [ ] Set or verify these Firebase secrets in the target project (never paste their values into a PR):
  `ANTHROPIC_API_KEY`, `USDA_FDC_API_KEY`, `MASTRA_PLATFORM_ACCESS_TOKEN`, `MASTRA_PROJECT_ID`,
  and `OPENROUTER_API_KEY`. Setting a secret alone does not update running functions; redeploy them.
  Use `firebase functions:secrets:set NAME --project calorizz-3e749` for each secret, entering the
  value privately at the CLI prompt. The OpenRouter secret is bound by the callable and must exist.
- [ ] Backup search defaults to GPT-4.1 Mini + Parallel basic. If overriding with
  `OPENROUTER_SEARCH_MODEL`, verify tool support and availability on that exact model.
- [ ] Gemini is no longer used by this backend; no Gemini billing or key is needed for this release.
  Do not delete shared Gemini secrets until other consumers have been checked.
- [ ] Run backend build/tests, eval self-test/typecheck, Android unit tests and screenshot verification.
- [ ] Configure the real project's `app/google-services.json` locally and build the Android app.
  That file and `functions/.secret.local` must remain uncommitted. Local secret files are explicitly
  excluded from the Functions deployment bundle.

## Deploy before merge

From the reviewed `feat/grounded-food` checkout:

```sh
firebase deploy --only functions:resurge,firestore:rules --project calorizz-3e749
```

This deploys the `resurge` Functions codebase and the configured Firestore rules. Review the CLI's
change list before continuing; stop if unrelated deletion or project changes are proposed.
Record the deployed Git SHA and deployment output in the PR. This step is planned, not yet run.

## Required deployed smoke tests

- [ ] Authenticated photo and text meals return editable drafts with table sources and micros.
- [ ] A known barcode returns its product; an unknown barcode asks for a nutrition-label photo.
- [ ] Label scan, gram edits, corrections, save/reopen and log-again work on a device.
- [ ] Describe voice input works on a phone with an available recognizer; text remains usable.
- [ ] An old meal still loads and a new barcode meal saves under the updated rules.
- [ ] Unauthenticated callable requests and client cache writes are rejected.
- [ ] An uncommon food exercises Anthropic search. Verify OpenRouter failover through a controlled
  test without disabling the production primary key. Cached items should avoid new provider calls.
- [ ] Mastra Studio shows the deployed run and tool traces; meal images are omitted from spans.
- [ ] Check Cloud Logging for startup, timeout, permission and provider errors. Confirm daily limits.
- [ ] Review the current web limitation: the page check confirms numeric presence, not that every
  nutrient and serving belongs to the exact food. Keep this limitation visible in the PR.

## Merge and rollback

After the deployed smoke tests pass, record evidence in PR #2, check that its head still matches
the deployed SHA, and merge. If the head changes, redeploy/retest those changes before merging.
Then release the Android build from that merged code.

If smoke tests fail, leave the PR unmerged and redeploy the previously recorded backend revision
from a clean checkout. Restore the previous rules if required; if barcode meals have already been
written, retain compatible barcode read/write handling rather than stranding those meals. Keep old
secret versions until rollback is no longer needed. Do not delete nutrition caches or user meals.

## Screenshot evidence

Screenshots in `feature/food/src/test/screenshots/` are rendered from the real Compose screens using
Robolectric and deterministic sample meals, not captured from a deployed Firebase session. They show
meal review, source details/estimate labels, daily micronutrients, barcode review and dark mode.
Camera capture, barcode scanning and voice still require the device smoke tests above.
