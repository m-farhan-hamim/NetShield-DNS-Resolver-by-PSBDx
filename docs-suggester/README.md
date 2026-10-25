# Docs suggester (demo)

When someone opens an issue, find the most relevant wiki page and suggest it in a comment.
A very small open AI model can then add one personalised intro line. No paid API, no key,
and the model never runs on a phone: only on GitHub's runners.

## The two jobs

```
issue opened
   |
   +--> issue-filter (existing): form checks, version question        <- untouched
   |
   +--> suggest  (about 10 s)  keyword matching on the wiki API, posts the plain suggestion
   |        |
   |        +--> personalize (slower, separate)  tiny model writes ONE intro line,
   |                                              the same comment is edited to include it
```

The plain comment is already useful and appears first. The AI line is a bonus: if the
model fails, is too slow, or writes something that fails the checks, the plain comment
simply stays as it is.

## Where the pages come from

- **Wiki API:** `https://wiki.psbdx.com/api/v1/posts` (JSON, pages followed via `meta.pages`,
  same host only, at most 10 pages). `tags` and `category` are matched like keywords, so put
  error strings in tags (`EAI_NODATA`, `UnknownHostException`).
- **RSS / Atom** feeds work too (the first demo used the Cloudflare blog feed).
- Set the source with `DOCS_FEED_URL` in the `env:` block of the workflow.

## How a page is chosen (no AI)

BM25 keyword scoring with a title boost, light stemming and `synonyms.json`. It stays silent
unless there is enough evidence: **2 distinctive words match, or 1 distinctive word plus a strong
title match** (at least 2 shared title words covering half of the page title). That second rule is
what lets "Orbot proxy mode only" find its page on a small wiki where "proxy" and "mode" are common.
Generic questions ("proxy mode only", "app not working") get no suggestion on purpose.

`synonyms.json` needs care: ambiguous words create false matches. "hot", "power", "charge" and
"language" all matched unrelated posts during testing and were removed.

## What the AI does, and what it is not allowed to do

The model only finishes one clause. The code writes everything else:

```
It sounds like you're <one clause from the model>. The pages below may help.
 ^ fixed opening, always second person                ^ fixed closing
```

Links, titles and matched terms always come from code. The model's text is untrusted and is
thrown away unless **all** of these pass (see `intro.js`, covered by tests):

- no markup, `@mentions`, links, domains, commands or risky wording (checked on the whole output first)
- one complete sentence, not cut off, within a length limit
- no number that is not in the issue
- no third person ("the user", "they")
- at least half of its content words appear in **the user's own text** (page titles do not count,
  otherwise a model can repeat page names back as if the user said them)
- the issue is mostly Latin script (the tiny model writes poorly in Bengali, so it is skipped)

Identifiers get their casing back (`unknownhostexception` -> `UnknownHostException`).
The comment says that a small AI model wrote the intro line, and names the model.

## Which model (real GitHub runs, 7 issues the matcher answered)

| Model (8-bit) | Size | Accepted | Notes |
|---|---|---|---|
| **SmolLM2-360M-Instruct** (default) | 365 MB | **6 / 7** | 5 accurate, 1 vague; about 1.7-2.7 s per intro |
| SmolLM2-135M-Instruct | 137 MB | 2 / 7 | one accepted intro was wrong; in an earlier run it invented a URL (caught) |
| Qwen2.5-0.5B-Instruct | 512 MB | 2 / 7 | decent when accepted, but slower and wordy |

This is a small sample (9 issues: angry all-caps, long log, vague, unrelated and a prompt-injection
attempt), not a benchmark. In it, no model followed the injected instructions. Greedy decoding is
used (no randomness): the same input gave the same output on repeated runs.

Change the model with `DOCS_AI_MODEL` in the workflow `env:` block. Settings are per model family: `min_new_tokens`
fixed empty outputs from SmolLM2 but made Qwen ramble, so it is only applied to SmolLM2 (`ai.mjs`).

## Speed (measured on GitHub, demo branch)

- `suggest` job: about 9-15 s in total.
- AI job with warm caches: about 22-27 s (cache restore ~5 s, runtime install 4-7 s, model load ~1 s,
  generation ~1-2 s). The first run downloaded the models and was only a few seconds slower.
- The two jobs are separate, so the AI job cannot delay the plain suggestion or your version question.
- Not measured: the real delay from "issue opened" to "comment posted" on a live issue
  (Issues are disabled on the fork). The `suggest` summary prints it for real issues.

## Cost

Free on a public repository. A private repository counts the minutes against its quota
(about one minute per issue for the AI job).

## Safety summary

- Issue text and feed content are untrusted. Issue text reaches scripts only through environment
  variables, never a shell command or URL.
- Links are posted only if `https` and on the feed's own host (extra hosts: `DOCS_ALLOWED_HOSTS`).
- Titles are escaped (no pings, no markdown or HTML injection).
- Every problem (feed down, model fails, bad output, timeout) leaves the plain comment alone;
  nothing here can fail the issue bot.

## Try it

```sh
node --test docs-suggester/suggest.test.js docs-suggester/intro.test.js   # 48 tests, offline, no model
node docs-suggester/suggest.js --feed https://wiki.psbdx.com/api/v1/posts \
  --title "Orbot proxy mode only" --body ""                                # live check (no AI)
```

The model step needs the model runtime and only runs in the workflow, never on a phone.

## How it is wired into the issue bot

Everything is in `.github/workflows/issue-filter.yml`:

- **Settings** are at the top (`env:`), next to the custom message, one line each:
  `DOCS_SUGGESTER_ENABLED`, `DOCS_FEED_URL`, `DOCS_AI_ENABLED`, `DOCS_AI_MODEL`,
  `DOCS_REPLACES_GITHUB_WIKI`. Set a flag to `"false"` to switch a part off.
- The existing script marks an issue as eligible only after it has passed **every** check
  (valid form, official source, supported version, version confirmed). Closed issues and issues
  still waiting for a version never get a suggestion. `edited` events are ignored.
- Job `docs-suggest` posts the plain suggestion as the bot. Job `docs-ai-intro` then edits that
  same comment to add the intro line. Both use `continue-on-error`, so they can never turn the
  issue bot red, and neither can delay the bot's own replies.
- `DOCS_REPLACES_GITHUB_WIKI: "true"` turns off the older GitHub-wiki suggestion so an issue gets
  suggestions from the docs site only. It defaults to `"false"`: both can then comment.
- Run the offline tests with `node --test docs-suggester/suggest.test.js docs-suggester/intro.test.js`
  (also run by `docs-suggester-tests.yml`).
- `eval.mjs` and `eval-issues.json` compare models on sample issues. They are only used by hand.

## Limits

- It finds your pages; it cannot reason about a bug nobody documented.
- English keyword stemming only. Romanized Bengali ("ami app khulte parchi na") will match poorly.
- Small models are brittle: expect some issues to get the plain comment without the AI line.
- Intros can be generic ("need some guidance"). They are checked for being grounded, not for being good.
