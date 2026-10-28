#!/usr/bin/env node
/*
 * AI personalization step (runs on GitHub's runner only, never on a phone).
 *
 * Takes the suggestion made by suggest.js and asks a very small open model
 * (SmolLM2 / Qwen, 8-bit, CPU) for ONE short intro line. Then:
 *   - everything the model returns is untrusted and goes through intro.validateIntro
 *   - links, titles and matched terms are still produced by code, never by the model
 *   - any failure (model missing, too slow, bad text) keeps the plain comment as is
 *
 * usage: node personalize.mjs result.json --out ai.json --md-out comment-ai.md --summary file.md
 * env:   ISSUE_TITLE ISSUE_BODY ISSUE_USER AI_MODEL AI_DTYPE HF_CACHE_DIR
 * test hook: AI_FAKE_OUTPUT="text" skips the model and treats the text as its output
 */
import fs from "node:fs";
import { createRequire } from "node:module";
import { performance } from "node:perf_hooks";
import { loadGenerator, writeIntro, judge, minTokensFor } from "./ai.mjs";

const require = createRequire(import.meta.url);
const intro = require("./intro.js");
const S = require("./suggest.js");

const args = process.argv.slice(2);
const resultPath = args.find((a, i) => !a.startsWith("--") && !(args[i - 1] || "").startsWith("--"));
const opt = (name) => { const i = args.indexOf(`--${name}`); return i >= 0 ? args[i + 1] : undefined; };

const env = process.env;
const MODEL = env.AI_MODEL || "HuggingFaceTB/SmolLM2-360M-Instruct";
const DTYPE = env.AI_DTYPE || "q8";
const MAX_GEN_MS = 150000;
const t0 = performance.now();
const out = { used: "none", reason: "", model: MODEL, dtype: DTYPE, timings: {}, text: "", raw: "" };

function summary() {
  const t = out.timings;
  const raw = String(out.raw || "").replace(/`/g, "'").slice(0, 400);
  return [
    `### 🤖 AI intro: ${MODEL.split("/").pop()} (${DTYPE})`,
    "| Step | Result |", "|---|---|",
    `| Outcome | **${out.used}**${out.reason ? ` (${out.reason})` : ""} |`,
    `| Model load (download or cache) | ${t.loadMs ?? "-"} ms |`,
    `| Generation | ${t.genMs ?? "-"} ms |`,
    `| Total | ${t.totalMs} ms |`,
    "",
    out.text ? `**Intro that would be posted:** ${out.text}` : "_No AI intro would be posted._",
    "",
    raw ? "**Raw model output (before the checks):**\n```text\n" + raw + "\n```" : "",
    "",
  ].join("\n");
}

function finish() {
  out.timings.totalMs = Math.round(performance.now() - t0);
  if (opt("out")) fs.writeFileSync(opt("out"), JSON.stringify(out, null, 2));
  if (opt("md-out") && out.comment) fs.writeFileSync(opt("md-out"), out.comment);
  if (opt("summary")) fs.appendFileSync(opt("summary"), summary());
  console.log(JSON.stringify({ used: out.used, reason: out.reason, model: out.model, dtype: out.dtype, timings: out.timings, text: out.text, raw: String(out.raw || "").slice(0, 300) }, null, 2));
  process.exit(0); // onnxruntime keeps worker threads alive; never let them hang the job
}

(async () => {
  try {
    const result = JSON.parse(fs.readFileSync(resultPath, "utf8"));
    if (result.status !== "ok") { out.reason = `no suggestion to personalize (status ${result.status})`; return finish(); }

    const title = env.ISSUE_TITLE || "";
    const m = intro.modelText(title, env.ISSUE_BODY || "");
    const prefix = intro.prefixFor(title); // chosen from the RAW title ([BUG]: vs [QUESTION]:)
    const gate = intro.shouldTryAi(m.title, m.body);
    if (!gate.ok) { out.used = "skipped"; out.reason = gate.reason; return finish(); }

    const ctx = { title: m.title, body: m.body, suggestions: result.suggestions };
    if (env.AI_FAKE_OUTPUT !== undefined) {
      out.raw = env.AI_FAKE_OUTPUT; // test hook, no model
    } else {
      const { generator, loadMs } = await loadGenerator(MODEL, DTYPE, env.HF_CACHE_DIR);
      out.timings.loadMs = loadMs;
      const g = await writeIntro(generator, ctx, MAX_GEN_MS, minTokensFor(MODEL), prefix);
      out.raw = g.raw;
      out.timings.genMs = g.genMs;
    }

    const verdict = judge(out.raw, ctx, prefix);
    if (!verdict.ok) { out.used = "fallback"; out.reason = `output rejected: ${verdict.reason}`; return finish(); }

    out.used = "ai";
    out.text = verdict.text;
    const short = MODEL.split("/").pop().replace(/[^A-Za-z0-9._-]/g, "");
    out.comment = S.buildComment(
      result.suggestions.map((s) => ({ item: { title: s.title, link: s.link }, matched: s.matched })),
      env.ISSUE_USER || "",
      { intro: out.text, modelNote: `✨ The intro line was written by a small AI model (${short}) and checked by simple rules. The links are chosen by keyword matching.` }
    );
  } catch (e) {
    out.used = "none";
    out.reason = `model step failed: ${e && e.message ? e.message : e}`;
    console.log(`::warning::${out.reason}`);
  }
  finish();
})();
