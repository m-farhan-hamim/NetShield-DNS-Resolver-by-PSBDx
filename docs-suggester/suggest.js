#!/usr/bin/env node
/*
 * NetShield docs suggester (demo)
 *
 * Reads an RSS/Atom feed, scores every entry against a new issue with BM25
 * keyword matching (title boost, light stemming, synonyms), and writes a
 * "These docs may help" comment. No dependencies, no AI model, no API key.
 *
 * Design rules:
 *  - It must NEVER fail the job. Any problem becomes status "skipped".
 *  - It stays silent unless the match is strong (better no hint than a wrong one).
 *  - Feed content and issue text are untrusted: links must be https and on an
 *    allowed host, titles are escaped, the issue text never goes into a URL.
 */
"use strict";

const fs = require("fs");
const path = require("path");
const { performance } = require("perf_hooks");

const DEFAULTS = {
  feedUrl: "https://wiki.psbdx.com/api/v1/posts",
  timeoutMs: 10000,
  maxBytes: 2 * 1024 * 1024,
  maxItems: 200,
  maxPages: 10,
  maxSuggestions: 2,
  minMatchedTerms: 2,
  minScore: 5,
  secondRatio: 0.6,
  contentChars: 1500,
  queryBodyChars: 4000,
};

const MARKER = "<!-- netshield-bot:docs -->";

/* ------------------------------ text helpers ------------------------------ */

const ENTITIES = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " " };

function decodeEntities(s) {
  return String(s)
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => safeCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => safeCodePoint(parseInt(d, 10)))
    .replace(/&([a-z]+);/gi, (m, n) => (ENTITIES[n.toLowerCase()] !== undefined ? ENTITIES[n.toLowerCase()] : m));
}

function safeCodePoint(n) {
  try { return n > 0 && n <= 0x10ffff ? String.fromCodePoint(n) : " "; } catch { return " "; }
}

function stripHtml(s) {
  return decodeEntities(
    String(s)
      .replace(/<(script|style)[\s\S]*?<\/\1>/gi, " ")
      .replace(/<[^>]*>/g, " ")
  ).replace(/\s+/g, " ").trim();
}

function unwrap(s) {
  const m = String(s).match(/^\s*<!\[CDATA\[([\s\S]*?)\]\]>\s*$/);
  return m ? m[1] : decodeEntities(s);
}

/* -------------------------------- feed parse ------------------------------ */

function tag(block, name) {
  const re = new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`, "i");
  const m = block.match(re);
  return m ? unwrap(m[1]).trim() : "";
}

function tagAll(block, name) {
  const re = new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`, "gi");
  const out = [];
  let m;
  while ((m = re.exec(block))) out.push(unwrap(m[1]).trim());
  return out;
}

function atomLink(block) {
  const links = [...block.matchAll(/<link\b([^>]*)\/?>/gi)].map((m) => m[1]);
  const pick = links.find((a) => /rel=["']alternate["']/i.test(a)) || links.find((a) => !/rel=/i.test(a)) || links[0] || "";
  const h = pick.match(/href=["']([^"']+)["']/i);
  return h ? decodeEntities(h[1]) : "";
}

function parseFeed(xml, opts = {}) {
  const o = { ...DEFAULTS, ...opts };
  const text = String(xml);
  const isAtom = /<entry[\s>]/i.test(text) && !/<item[\s>]/i.test(text);
  const re = isAtom ? /<entry[\s>][\s\S]*?<\/entry>/gi : /<item[\s>][\s\S]*?<\/item>/gi;
  const blocks = text.match(re) || [];
  const items = [];
  for (const b of blocks.slice(0, o.maxItems)) {
    const title = stripHtml(tag(b, "title"));
    const link = isAtom ? atomLink(b) : tag(b, "link");
    if (!title || !link) continue;
    const summary = stripHtml(isAtom ? tag(b, "summary") || tag(b, "content") : tag(b, "description"));
    const full = isAtom ? tag(b, "content") : tag(b, "content:encoded");
    const categories = isAtom
      ? [...b.matchAll(/<category\b[^>]*term=["']([^"']+)["']/gi)].map((m) => decodeEntities(m[1]))
      : tagAll(b, "category").map(stripHtml);
    items.push({
      title,
      link: link.trim(),
      summary,
      categories: categories.filter(Boolean),
      content: stripHtml(full).slice(0, o.contentChars),
    });
  }
  return items;
}

/* ------------------------- JSON API (wiki /api/v1/posts) ------------------ */

// Maps {data:[{title,url,excerpt,category:{name},tags:[...],lang}], meta:{...}} to feed entries.
// Tags are treated like categories, so error strings in tags (EAI_NODATA) help matching.
function parseApiPosts(json, opts = {}) {
  const o = { ...DEFAULTS, ...opts };
  const list = Array.isArray(json) ? json : json && Array.isArray(json.data) ? json.data : [];
  const items = [];
  for (const p of list.slice(0, o.maxItems)) {
    if (!p || typeof p !== "object") continue;
    const title = stripHtml(String(p.title || ""));
    const link = String(p.url || p.link || "").trim();
    if (!title || !link) continue;
    const cats = [];
    if (p.category && p.category.name) cats.push(String(p.category.name));
    if (Array.isArray(p.tags)) for (const t of p.tags) cats.push(String(t));
    items.push({
      title,
      link,
      summary: stripHtml(String(p.excerpt || p.summary || "")),
      categories: cats.map(stripHtml).filter(Boolean),
      content: stripHtml(String(p.content || "")).slice(0, o.contentChars),
      lang: p.lang ? String(p.lang) : "",
    });
  }
  return items;
}

// RSS/Atom (XML) or JSON API, decided by the first character.
function parseEntries(text, opts = {}) {
  const t = String(text);
  if (/^\s*[\[{]/.test(t)) {
    let json;
    try { json = JSON.parse(t); } catch { return []; }
    return parseApiPosts(json, opts);
  }
  return parseFeed(t, opts);
}

/* ------------------------------ links & escaping -------------------------- */

function isAllowedLink(url, allowedHosts) {
  let u;
  try { u = new URL(url); } catch { return false; }
  if (u.protocol !== "https:") return false;
  const host = u.hostname.toLowerCase();
  return allowedHosts.some((h) => host === h || host.endsWith("." + h));
}

function escapeMd(s) {
  return String(s)
    .replace(/[\r\n]+/g, " ")
    .replace(/[`\[\]()<>|*_~#]/g, "")
    .replace(/@/g, "@\u200b")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 140);
}

/* -------------------------------- text → terms ---------------------------- */

const STOP = new Set(("a an and are as at be but by can could do does did for from had has have how i if in into is it its " +
  "me my not of on or our so than that the their them then there these they this to up us was we were what when where which " +
  "who why will with would you your am app apps get got just like use used using also very much many some any").split(/\s+/));

function loadSynonyms(file) {
  let raw = {};
  try { raw = JSON.parse(fs.readFileSync(file, "utf8")); } catch { /* optional */ }
  const map = new Map();
  for (const [canon, aliases] of Object.entries(raw)) {
    map.set(canon.toLowerCase(), canon.toLowerCase());
    for (const a of aliases) map.set(String(a).toLowerCase(), canon.toLowerCase());
  }
  return map;
}

function stem(w) {
  if (w.length > 4 && w.endsWith("ies")) return w.slice(0, -3) + "y";
  if (w.length > 5 && w.endsWith("ing")) return w.slice(0, -3);
  if (w.length > 4 && w.endsWith("ed")) return w.slice(0, -2);
  if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.slice(0, -1);
  return w;
}

function terms(text, syn) {
  const out = [];
  for (const w of String(text).toLowerCase().split(/[^\p{L}\p{N}]+/u)) {
    if (w.length < 2 || STOP.has(w)) continue;
    const s = stem(w);
    if (STOP.has(s)) continue; // "uses" -> "use" is a stopword too
    // try the exact word, then its stem, so "drains" and "drain" both reach "battery"
    out.push(syn ? syn.get(w) || syn.get(s) || s : s);
  }
  return out;
}

/* ---------------------------------- ranking ------------------------------- */

function buildIndex(items, syn) {
  const docs = items.map((item) => {
    const tf = new Map();
    const add = (list, weight) => { for (const t of list) tf.set(t, (tf.get(t) || 0) + weight); };
    add(terms(item.title, syn), 3);
    add(terms(item.categories.join(" "), syn), 2);
    add(terms(item.summary, syn), 1);
    add(terms(item.content, syn), 0.5);
    const shallow = new Set([
      ...terms(item.title, syn), ...terms(item.categories.join(" "), syn), ...terms(item.summary, syn),
    ]);
    const titleSet = new Set(terms(item.title, syn));
    let len = 0;
    for (const v of tf.values()) len += v;
    return { item, tf, len, shallow, titleSet };
  });
  const df = new Map();
  for (const d of docs) for (const t of d.tf.keys()) df.set(t, (df.get(t) || 0) + 1);
  const avgLen = docs.reduce((a, d) => a + d.len, 0) / (docs.length || 1);
  return { docs, df, avgLen, n: docs.length };
}

function cleanIssueText(title, body, max = DEFAULTS.queryBodyChars) {
  const cleaned = String(body || "")
    .replace(/^###.*$/gm, " ")
    .replace(/^\s*- \[[ xX]\] .*$/gm, " ")
    .replace(/_No response_/gi, " ")
    .replace(/```[a-z]*/gi, " ")
    .slice(0, max);
  return { titlePart: String(title || ""), bodyPart: cleaned };
}

function rank(index, title, body, syn, opts = {}) {
  const o = { ...DEFAULTS, ...opts };
  const { titlePart, bodyPart } = cleanIssueText(title, body, o.queryBodyChars);
  const q = new Map();
  for (const t of terms(titlePart, syn)) q.set(t, (q.get(t) || 0) + 2);
  for (const t of terms(bodyPart, syn)) q.set(t, (q.get(t) || 0) + 1);

  const k1 = 1.4, b = 0.75;
  const informativeMax = Math.max(1, 0.4 * index.n);
  const scored = [];
  for (const d of index.docs) {
    let score = 0;
    const matched = [];
    let matchedInTitle = 0;
    let titleHits = 0;
    for (const t of q.keys()) if (d.titleSet.has(t)) titleHits++;
    for (const [t, qc] of q) {
      const f = d.tf.get(t);
      if (!f) continue;
      const dfv = index.df.get(t) || 1;
      const idf = Math.log(1 + (index.n - dfv + 0.5) / (dfv + 0.5));
      const part = (idf * (f * (k1 + 1))) / (f + k1 * (1 - b + (b * d.len) / (index.avgLen || 1)));
      score += part * (1 + Math.log(qc));
      if (dfv <= informativeMax && d.shallow.has(t)) { matched.push(t); if (d.titleSet.has(t)) matchedInTitle++; }
    }
    scored.push({ item: d.item, score, matched, matchedInTitle, titleHits, titleCov: d.titleSet.size ? titleHits / d.titleSet.size : 0 });
  }
  scored.sort((x, y) => y.score - x.score);
  return scored;
}

function pick(scored, allowedHosts, opts = {}) {
  const o = { ...DEFAULTS, ...opts };
  const out = [];
  for (const s of scored) {
    if (!isAllowedLink(s.item.link, allowedHosts)) continue;
    // Enough evidence = 2+ distinctive words, OR 1 distinctive word plus a strong title match
    // (at least 2 shared title words covering half of the page title). The second rule is what
    // lets "Orbot proxy mode only" find its page on a small wiki where "proxy"/"mode" are common.
    // (the distinctive word must be in the page TITLE: a stray word in a summary or tag is not evidence)
    const strongTitle = s.matched.length >= 1 && s.matchedInTitle >= 1 && s.titleHits >= 2 && s.titleCov >= 0.5;
    if ((s.matched.length < o.minMatchedTerms && !strongTitle) || s.score < o.minScore) continue;
    if (out.length && s.score < out[0].score * o.secondRatio) break;
    out.push(s);
    if (out.length >= o.maxSuggestions) break;
  }
  return out;
}

function buildComment(suggestions, user, extra = {}) {
  const who = user ? `@${String(user).replace(/[^A-Za-z0-9-]/g, "")}` : "there";
  const lines = suggestions.map((s, i) => `${i + 1}. [${escapeMd(s.item.title)}](${s.item.link}) (matched: ${s.matched.slice(0, 4).map(escapeMd).join(", ")})`);
  // optional one-line intro written by the small AI model (already validated; stripped again here)
  const intro = extra.intro ? String(extra.intro).replace(/[<>`\[\]|*#@]/g, "").replace(/\s+/g, " ").trim() : "";
  const note = extra.modelNote ? String(extra.modelNote).replace(/[<>`\[\]|*#@]/g, "").trim() : "";
  return [
    MARKER,
    `📚 **These docs may help, ${who}:**`,
    "",
    ...(intro ? [intro, ""] : []),
    ...lines,
    "",
    "_I match keywords automatically, so I can be wrong. If these don't help, the maintainer will take a look._",
    ...(note ? ["", `_${note}_`] : []),
  ].join("\n");
}

/* ----------------------------------- fetch -------------------------------- */

async function fetchFeed(url, o) {
  const u = new URL(url);
  if (u.protocol !== "https:") throw new Error("feed URL must be https");
  const res = await fetch(url, {
    headers: { "User-Agent": "netshield-docs-suggester", Accept: "application/rss+xml, application/atom+xml, application/xml, text/xml" },
    signal: AbortSignal.timeout(o.timeoutMs),
    redirect: "follow",
  });
  if (!res.ok) throw new Error(`feed returned HTTP ${res.status}`);
  if (res.url && new URL(res.url).protocol !== "https:") throw new Error("feed redirected to a non-https URL");
  const reader = res.body.getReader();
  const chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    if (total > o.maxBytes) { await reader.cancel(); throw new Error(`feed larger than ${o.maxBytes} bytes`); }
    chunks.push(value);
  }
  return { xml: Buffer.concat(chunks).toString("utf8"), bytes: total };
}

// JSON APIs only: follow meta.pages (capped), same host, same page size as page 1.
async function fetchMorePages(url, firstText, o) {
  let meta;
  try { meta = JSON.parse(firstText).meta; } catch { return { items: [], bytes: 0 }; }
  if (!meta || !(Number(meta.pages) > 1)) return { items: [], bytes: 0 };
  const items = [];
  let bytes = 0;
  const last = Math.min(Number(meta.pages), o.maxPages);
  for (let page = 2; page <= last; page++) {
    const u = new URL(url);
    u.searchParams.set("page", String(page));
    if (meta.per_page) u.searchParams.set("per_page", String(meta.per_page));
    const f = await fetchFeed(u.toString(), o);
    items.push(...parseEntries(f.xml, o));
    bytes += f.bytes;
  }
  return { items, bytes };
}

/* ------------------------------------ run --------------------------------- */

async function run(input) {
  const o = { ...DEFAULTS, ...input };
  const t0 = performance.now();
  const timings = {};
  const result = { status: "skipped", reason: "", suggestions: [], timings, feed: { url: o.feedUrl, items: 0, bytes: 0 } };
  try {
    const feedHost = new URL(o.feedUrl).hostname.toLowerCase();
    const allowedHosts = [feedHost, ...(o.allowedHosts || [])].map((h) => h.toLowerCase());

    const t1 = performance.now();
    let xml;
    let more = { items: [], bytes: 0 };
    if (o.feedFile) { xml = fs.readFileSync(o.feedFile, "utf8"); result.feed.bytes = Buffer.byteLength(xml); }
    else {
      const f = await fetchFeed(o.feedUrl, o); xml = f.xml; result.feed.bytes = f.bytes;
      more = await fetchMorePages(o.feedUrl, xml, o);
      result.feed.bytes += more.bytes;
    }
    timings.fetchMs = Math.round(performance.now() - t1);

    const t2 = performance.now();
    const items = parseEntries(xml, o).concat(more.items).slice(0, o.maxItems);
    result.feed.items = items.length;
    if (!items.length) { result.reason = "feed had no readable entries"; return finish(result, t0, o); }
    const syn = loadSynonyms(o.synonymsFile || path.join(__dirname, "synonyms.json"));
    const index = buildIndex(items, syn);
    timings.parseMs = Math.round(performance.now() - t2);

    const t3 = performance.now();
    const scored = rank(index, o.title, o.body, syn, o);
    const chosen = pick(scored, allowedHosts, o);
    timings.matchMs = Math.round(performance.now() - t3);
    result.top = scored.slice(0, 3).map((s) => ({ title: s.item.title, score: +s.score.toFixed(2), matched: s.matched, titleCov: +s.titleCov.toFixed(2) }));

    if (!chosen.length) { result.status = "no-match"; result.reason = "nothing above the confidence threshold, staying silent"; return finish(result, t0, o); }
    result.status = "ok";
    result.suggestions = chosen.map((s) => ({ title: s.item.title, link: s.item.link, score: +s.score.toFixed(2), matched: s.matched }));
    result.comment = buildComment(chosen, o.user);
  } catch (e) {
    result.reason = `skipped: ${e && e.message ? e.message : e}`;
  }
  return finish(result, t0, o);
}

function finish(result, t0, o) {
  result.timings.totalMs = Math.round(performance.now() - t0);
  if (o.issueCreatedAt) {
    const ms = Date.now() - Date.parse(o.issueCreatedAt);
    if (Number.isFinite(ms)) result.timings.sinceIssueCreatedSec = Math.round(ms / 1000);
  }
  return result;
}

function summaryMarkdown(r) {
  const t = r.timings;
  const rows = [
    ["Status", `**${r.status}**${r.reason ? ` (${r.reason})` : ""}`],
    ["Feed", `${r.feed.items} entries, ${r.feed.bytes} bytes`],
    ["Fetch", `${t.fetchMs ?? "-"} ms`],
    ["Parse + index", `${t.parseMs ?? "-"} ms`],
    ["Match", `${t.matchMs ?? "-"} ms`],
    ["Script total", `${t.totalMs} ms`],
  ];
  if (t.sinceIssueCreatedSec !== undefined) rows.push(["Issue created → now", `${t.sinceIssueCreatedSec} s (includes runner start-up)`]);
  const top = (r.top || []).map((x) => `| ${escapeMd(x.title)} | ${x.score} | ${x.matched.map(escapeMd).join(", ")} |`).join("\n");
  return [
    "### 📚 Docs suggester",
    "| Step | Result |", "|---|---|", ...rows.map(([a, b]) => `| ${a} | ${b} |`),
    "", "**Top candidates (what the matcher saw)**", "| Title | Score | Matched terms |", "|---|---|---|", top, "",
    r.comment ? "**Comment that would be posted**\n\n" + r.comment.split("\n").map((l) => "> " + l).join("\n") : "_No comment would be posted._",
    "",
  ].join("\n");
}

/* ------------------------------------ CLI --------------------------------- */

function parseArgs(argv) {
  const a = {};
  for (let i = 0; i < argv.length; i++) {
    if (!argv[i].startsWith("--")) continue;
    const k = argv[i].slice(2);
    const next = argv[i + 1];
    if (next === undefined || next.startsWith("--")) a[k] = true; else { a[k] = next; i++; }
  }
  return a;
}

async function main() {
  const a = parseArgs(process.argv.slice(2));
  const env = process.env;
  const result = await run({
    feedUrl: a.feed || env.FEED_URL || DEFAULTS.feedUrl,
    feedFile: a["feed-file"],
    title: a.title || env.ISSUE_TITLE || "",
    body: a.body || env.ISSUE_BODY || "",
    user: a.user || env.ISSUE_USER || "",
    issueCreatedAt: env.ISSUE_CREATED_AT || "",
    allowedHosts: (a["allowed-hosts"] || env.DOCS_ALLOWED_HOSTS || "").split(",").map((s) => s.trim()).filter(Boolean),
    ...(a["min-score"] ? { minScore: Number(a["min-score"]) } : {}),
    ...(a["min-terms"] ? { minMatchedTerms: Number(a["min-terms"]) } : {}),
  });
  if (a["json-out"]) fs.writeFileSync(a["json-out"], JSON.stringify(result, null, 2));
  if (a["md-out"] && result.comment) fs.writeFileSync(a["md-out"], result.comment);
  const summaryFile = a.summary && a.summary !== true ? a.summary : null;
  if (summaryFile) fs.appendFileSync(summaryFile, summaryMarkdown(result));
  if (result.status === "skipped") console.log(`::warning::docs suggester ${result.reason}`);
  console.log(JSON.stringify({ status: result.status, reason: result.reason, timings: result.timings, top: result.top, suggestions: result.suggestions }, null, 2));
}

if (require.main === module) main().catch((e) => { console.log(`::warning::docs suggester crashed: ${e && e.message}`); process.exit(0); });

module.exports = { parseApiPosts, parseEntries, parseFeed, buildIndex, rank, pick, buildComment, isAllowedLink, escapeMd, cleanIssueText, loadSynonyms, terms, run, summaryMarkdown, MARKER, DEFAULTS };
