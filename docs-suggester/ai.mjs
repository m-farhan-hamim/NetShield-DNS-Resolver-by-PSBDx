/* Shared model helpers for personalize.mjs and eval.mjs. Runs on GitHub's runner only. */
import { createRequire } from "node:module";
import { performance } from "node:perf_hooks";

const require = createRequire(import.meta.url);
const intro = require("./intro.js");

export async function loadGenerator(model, dtype, cacheDir) {
  const t = performance.now();
  const { pipeline, env } = await import("@huggingface/transformers");
  env.cacheDir = cacheDir || "./.hf-cache";
  env.allowLocalModels = false;
  const generator = await pipeline("text-generation", model, { dtype });
  return { generator, loadMs: Math.round(performance.now() - t) };
}

// The code writes the opening; the model only continues it (greedy, no randomness).
// Measured on GitHub runners (transformers.js 3.8.1): SmolLM2 sometimes ended its text immediately after
// the opening, and min_new_tokens fixed that. The same setting made Qwen2.5-0.5B ramble ("I'm sorry to
// say..." repeated), so it is applied per model family, not globally.
export const minTokensFor = (model) => (/smollm2/i.test(model) ? 4 : 0);

export async function writeIntro(generator, { title, body, suggestions }, maxMs = 150000, minNewTokens = 0, prefix = intro.PREFIX) {
  const messages = intro.buildMessages(title, body, prefix);
  const prompt = generator.tokenizer.apply_chat_template(messages, { tokenize: false, add_generation_prompt: true }) + prefix;
  const t = performance.now();
  let timer;
  try {
    const gen = await Promise.race([
      generator(prompt, { max_new_tokens: 48, ...(minNewTokens ? { min_new_tokens: minNewTokens } : {}), do_sample: false, repetition_penalty: 1.1, return_full_text: false }),
      new Promise((_, rej) => { timer = setTimeout(() => rej(new Error(`generation took longer than ${maxMs / 1000}s`)), maxMs); }),
    ]);
    let cont = String((gen && gen[0] && gen[0].generated_text) || "");
    if (cont.startsWith(prompt)) cont = cont.slice(prompt.length);
    return { raw: cont, genMs: Math.round(performance.now() - t) };
  } finally { clearTimeout(timer); }
}

// raw model text -> { ok, text, reason }. Untrusted input: compose, restore casing, validate.
export function judge(raw, { title, body, suggestions }, prefix = intro.PREFIX) {
  const composed = intro.composeIntro(raw, prefix);
  if (!composed) return { ok: false, text: "", reason: "no complete sentence (output was cut off or contained a glued domain)" };
  const fixed = intro.restoreCase(composed, `${title}\n${body}`);
  const v = intro.validateIntro(fixed, { title, body, suggestions });
  return v.ok ? { ok: true, text: v.text, reason: "" } : { ok: false, text: "", reason: v.reason };
}
