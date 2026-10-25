#!/usr/bin/env node
/* Runs ONE model over the sample issues (matcher on the frozen wiki fixture) and prints a comparable
 * line per issue. Load the model once. GitHub runner only. */
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { loadGenerator, writeIntro, judge, minTokensFor } from "./ai.mjs";

const require = createRequire(import.meta.url);
const S = require("./suggest.js");
const intro = require("./intro.js");
const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const issues = JSON.parse(fs.readFileSync(args.find((a) => !a.startsWith("--")), "utf8"));
const summaryFile = args.includes("--summary") ? args[args.indexOf("--summary") + 1] : null;
const MODEL = process.env.AI_MODEL || "HuggingFaceTB/SmolLM2-360M-Instruct";
const DTYPE = process.env.AI_DTYPE || "q8";
const short = MODEL.split("/").pop();
const one = (s) => String(s || "").replace(/\s+/g, " ").trim();

(async () => {
  const rows = [];
  try {
    const { generator, loadMs } = await loadGenerator(MODEL, DTYPE, process.env.HF_CACHE_DIR);
    console.log(`EVAL|${short}|loaded in ${loadMs} ms`);
    for (const it of issues) {
      const r = await S.run({ feedUrl: "https://wiki.psbdx.com/api/v1/posts", feedFile: path.join(here, "fixtures", "wiki-api-sample.json"), title: it.title, body: it.body, user: "alice" });
      const row = { id: it.id, matcher: r.status, used: "-", reason: "", text: "", genMs: 0, raw: "" };
      if (r.status === "ok") {
        const { bodyPart } = S.cleanIssueText(it.title, it.body);
        const gate = intro.shouldTryAi(it.title, bodyPart);
        if (!gate.ok) { row.used = "skipped"; row.reason = gate.reason; }
        else {
          const ctx = { title: it.title, body: bodyPart, suggestions: r.suggestions };
          try {
            const g = await writeIntro(generator, ctx, 150000, minTokensFor(MODEL));
            const j = judge(g.raw, ctx);
            Object.assign(row, { genMs: g.genMs, raw: g.raw, used: j.ok ? "ai" : "fallback", text: j.text, reason: j.reason });
          } catch (e) { row.used = "error"; row.reason = e.message; }
        }
      }
      rows.push(row);
      console.log(`EVAL|${short}|${row.id}|matcher=${row.matcher}|${row.used}|${row.genMs}ms|text=${one(row.text)}|reason=${one(row.reason)}|raw=${one(row.raw).slice(0, 170)}`);
    }
  } catch (e) { console.log(`EVAL|${short}|FAILED|${e && e.message}`); }
  if (summaryFile) {
    const md = ["### 🧪 Sample issues: " + short, "| Issue | Matcher | AI | ms | Intro / reason |", "|---|---|---|---|---|",
      ...rows.map((r) => `| ${r.id} | ${r.matcher} | **${r.used}** | ${r.genMs} | ${(r.text || r.reason || "").replace(/[|`]/g, "'")} |`), ""].join("\n");
    fs.appendFileSync(summaryFile, md);
  }
  process.exit(0);
})();
