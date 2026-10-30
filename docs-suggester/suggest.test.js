"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");
const S = require("./suggest.js");

const fx = (n) => path.join(__dirname, "fixtures", n);
const syn = S.loadSynonyms(path.join(__dirname, "synonyms.json"));
const items = S.parseFeed(fs.readFileSync(fx("sample-rss.xml"), "utf8"));
const index = S.buildIndex(items, syn);
const HOSTS = ["wiki.example.org"];
const suggest = (title, body = "") => S.pick(S.rank(index, title, body, syn), HOSTS);
const FEED = "https://wiki.example.org/rss.xml";

test("parses RSS: entities decoded, CDATA unwrapped, tags stripped", () => {
  assert.equal(items.length, 9);
  const evil = items.find((i) => new URL(i.link).hostname === "evil.example.net");
  assert.equal(evil.title, "Fix & tune battery drain @everyone");
  const dot = items.find((i) => i.link.endsWith("/dot-upstreams/"));
  assert.match(dot.content, /port 853 the app cannot reach/);
  assert.ok(!/[<>]/.test(dot.content));
  assert.deepEqual(dot.categories, ["DNS", "TLS"]);
});

test("parses Atom: rel=alternate link, summary, term categories", () => {
  const a = S.parseFeed(fs.readFileSync(fx("sample-atom.xml"), "utf8"));
  assert.equal(a.length, 2);
  assert.equal(a[0].link, "https://wiki.example.org/blocklist-guide/");
  assert.equal(a[0].title, "Blocklist & filter list guide");
  assert.equal(a[0].summary, "How to add a custom blocklist.");
  assert.deepEqual(a[1].categories, ["DNS", "TLS"]);
});

test("meaning without shared words: 'phone gets hot ... dies' finds the battery page", () => {
  const r = suggest("My phone gets hot and the vpn dies in the background", "huge drain on xiaomi");
  assert.ok(r.length >= 1);
  assert.ok(r[0].item.link.endsWith("/battery-and-background/"), r[0].item.link);
});

test("error string finds the DoT page", () => {
  const r = suggest("Upstream fails", "java.net.UnknownHostException: Unable to resolve host dns.adguard-dns.com on port 853");
  assert.ok(r.length >= 1);
  assert.ok(r[0].item.link.endsWith("/dot-upstreams/"), r[0].item.link);
});

test("unrelated text stays silent", () => {
  assert.deepEqual(suggest("How do I bake sourdough bread at home", "flour water salt"), []);
});

test("one generic word is not enough (needs >= 2 informative terms)", () => {
  assert.deepEqual(suggest("dns", ""), []);
  assert.deepEqual(suggest("app crashes", ""), []);
});

test("hostile links (other domain, javascript:, http:) are never suggested, even if they score best", () => {
  for (const q of [["battery drain autostart fix @everyone", ""], ["Verify APK signature fingerprint now", ""], ["dns over tls dot upstream port 853", "unknownhostexception"]]) {
    const scored = S.rank(index, q[0], q[1], syn);
    assert.ok(scored.slice(0, 2).some((s) => !S.isAllowedLink(s.item.link, HOSTS)), `precondition: a hostile entry should rank high for "${q[0]}"`);
    for (const p of S.pick(scored, HOSTS)) assert.ok(S.isAllowedLink(p.item.link, HOSTS), p.item.link);
  }
});

test("isAllowedLink: https only, exact host or subdomain, no look-alikes", () => {
  assert.ok(S.isAllowedLink("https://wiki.example.org/x", HOSTS));
  assert.ok(S.isAllowedLink("https://fr.wiki.example.org/x", HOSTS));
  assert.ok(!S.isAllowedLink("https://wiki.example.org.evil.net/x", HOSTS));
  assert.ok(!S.isAllowedLink("https://notwiki.example.org/x", HOSTS));
  assert.ok(!S.isAllowedLink("http://wiki.example.org/x", HOSTS));
  assert.ok(!S.isAllowedLink("javascript:alert(1)", HOSTS));
  assert.ok(!S.isAllowedLink("not a url", HOSTS));
});

test("never more than 2 suggestions", () => {
  assert.ok(suggest("dns tls vpn block battery install signature", "everything at once").length <= 2);
});

test("comment is safe: no pings, no markdown/link injection, user sanitised, marker present", () => {
  const c = S.buildComment([{ item: { title: "@everyone [click](http://evil.example) `x` <img src=x>", link: "https://wiki.example.org/a/" }, matched: ["dns", "@admin"] }], "bob\n@admin");
  assert.ok(c.startsWith(S.MARKER));
  assert.ok(!/@everyone/.test(c) && !/@admin/.test(c.split("matched")[1]));
  assert.ok(!/\]\(http:\/\/evil/.test(c));
  assert.ok(!/<img/.test(c));
  assert.match(c, /@bobadmin:/);
});

test("cleanIssueText drops form headers, checkboxes, 'No response' and code fences", () => {
  const { bodyPart } = S.cleanIssueText("t", "### Version Type\n\nStable\n\n- [x] I searched\n_No response_\n```shell\nboom\n```");
  assert.ok(!/###|I searched|No response|```/.test(bodyPart));
  assert.match(bodyPart, /Stable/);
  assert.match(bodyPart, /boom/);
});

test("run(): ok with a good match, with timings and a postable comment", async () => {
  const r = await S.run({ feedUrl: FEED, feedFile: fx("sample-rss.xml"), title: "Battery drain, vpn killed in background", body: "xiaomi", user: "alice" });
  assert.equal(r.status, "ok");
  assert.ok(r.comment.includes("[Fixing battery drain"));
  assert.equal(typeof r.timings.totalMs, "number");
  assert.equal(r.feed.items, 9);
});

test("run(): no-match stays silent", async () => {
  const r = await S.run({ feedUrl: FEED, feedFile: fx("sample-rss.xml"), title: "sourdough bread", body: "" });
  assert.equal(r.status, "no-match");
  assert.equal(r.comment, undefined);
});

test("run(): failures become 'skipped', never throw", async () => {
  const missing = await S.run({ feedUrl: FEED, feedFile: fx("does-not-exist.xml"), title: "x", body: "" });
  assert.equal(missing.status, "skipped");
  const empty = await S.run({ feedUrl: FEED, feedFile: fx("empty.xml"), title: "x", body: "" });
  assert.equal(empty.status, "skipped");
  assert.match(empty.reason, /no readable entries/);
  const http = await S.run({ feedUrl: "http://wiki.example.org/rss.xml", title: "x", body: "" });
  assert.equal(http.status, "skipped");
  assert.match(http.reason, /https/);
  const badUrl = await S.run({ feedUrl: "not a url", title: "x", body: "" });
  assert.equal(badUrl.status, "skipped");
});

test("run(): reports time since the issue was created", async () => {
  const r = await S.run({ feedUrl: FEED, feedFile: fx("sample-rss.xml"), title: "x", body: "", issueCreatedAt: new Date(Date.now() - 5000).toISOString() });
  assert.ok(r.timings.sinceIssueCreatedSec >= 4 && r.timings.sinceIssueCreatedSec <= 9, String(r.timings.sinceIssueCreatedSec));
});

test("speed: indexing + ranking 200 entries is far below a second", () => {
  const big = [];
  for (let i = 0; i < 200; i++) big.push({ title: `Guide ${i} about dns tls vpn topic${i}`, link: `https://wiki.example.org/g${i}/`, summary: "lorem ipsum ".repeat(60) + `unique${i}`, categories: ["DNS"], content: "word ".repeat(300) });
  const t = Date.now();
  const idx = S.buildIndex(big, syn);
  S.rank(idx, "dns tls problem", "x ".repeat(500), syn);
  assert.ok(Date.now() - t < 1500, `took ${Date.now() - t} ms`);
});

/* ---------------------- wiki JSON API (real response shape) ---------------------- */
const WH = ["wiki.psbdx.com"];
const apiItems = S.parseEntries(fs.readFileSync(fx("wiki-api-sample.json"), "utf8"));
const apiIndex = S.buildIndex(apiItems, syn);
const apiSuggest = (t, b = "") => S.pick(S.rank(apiIndex, t, b, syn), WH);

test("API: parses the real wiki response; tags and category become matchable terms", () => {
  assert.equal(apiItems.length, 5);
  assert.ok(apiItems.every((i) => i.link.startsWith("https://wiki.psbdx.com/wiki/")));
  const t = apiItems.find((i) => /Troubleshooting/.test(i.title));
  assert.ok(t.categories.includes("General Support") && t.categories.includes("EAI_NODATA"));
  assert.equal(t.lang, "en");
});

test("API: real wiki questions find the right page", () => {
  const cases = [
    ["Getting UnknownHostException on mobile data", "the app shows EAI_NODATA and nothing resolves", "troubleshooting-guide"],
    ["How do I make Orbot work in proxy mode only", "", "orbot-to-proxy-mode"],
    ["v2rayNG SOCKS5 proxy only mode", "", "v2rayng-to-proxy-mode"],
    ["InviZible Pro proxy mode setup", "", "invizible-pro-to-proxy-mode"],
  ];
  for (const [t, b, slug] of cases) {
    const r = apiSuggest(t, b);
    assert.ok(r.length >= 1, `no suggestion for: ${t}`);
    assert.ok(r[0].item.link.includes(slug), `${t} -> ${r[0].item.link}`);
  }
});

test("API: unrelated or generic issues stay silent", () => {
  assert.deepEqual(apiSuggest("How do I bake sourdough bread", "flour water salt"), []);
  assert.deepEqual(apiSuggest("proxy", ""), []);
});

test("API run(): works from a file and from a malformed body", async () => {
  const r = await S.run({ feedUrl: "https://wiki.psbdx.com/api/v1/posts", feedFile: fx("wiki-api-sample.json"), title: "Orbot proxy mode only", body: "", user: "alice" });
  assert.equal(r.status, "ok");
  assert.equal(r.feed.items, 5);
  fs.writeFileSync(path.join(__dirname, "fixtures", ".tmp-bad.json"), "{ not json");
  const bad = await S.run({ feedUrl: "https://wiki.psbdx.com/api/v1/posts", feedFile: path.join(__dirname, "fixtures", ".tmp-bad.json"), title: "x", body: "" });
  fs.unlinkSync(path.join(__dirname, "fixtures", ".tmp-bad.json"));
  assert.equal(bad.status, "skipped");
});

function fakeApiFetch(all, perDefault, seen, pagesOverride) {
  return async (url) => {
    seen.push(String(url));
    const u = new URL(url);
    const page = Number(u.searchParams.get("page") || 1);
    const per = Number(u.searchParams.get("per_page") || perDefault);
    const slice = all.slice((page - 1) * per, page * per);
    const res = new Response(JSON.stringify({ data: slice, meta: { page, per_page: per, total: all.length, pages: pagesOverride || Math.ceil(all.length / per) } }), { status: 200 });
    Object.defineProperty(res, "url", { value: String(url) });
    return res;
  };
}

test("API pagination: follows meta.pages with the same page size, same host", async () => {
  const all = JSON.parse(fs.readFileSync(fx("wiki-api-sample.json"), "utf8")).data;
  const seen = [], realFetch = global.fetch;
  global.fetch = fakeApiFetch(all, 2, seen);
  try {
    const r = await S.run({ feedUrl: "https://wiki.psbdx.com/api/v1/posts?per_page=2", title: "UnknownHostException", body: "EAI_NODATA nothing resolves" });
    assert.equal(r.feed.items, 5);
    assert.deepEqual(seen.map((s) => new URL(s).searchParams.get("page")), [null, "2", "3"]);
    assert.ok(seen.every((s) => new URL(s).host === "wiki.psbdx.com"));
  } finally { global.fetch = realFetch; }
});

test("API pagination: a huge meta.pages cannot make us fetch more than maxPages", async () => {
  const all = JSON.parse(fs.readFileSync(fx("wiki-api-sample.json"), "utf8")).data;
  const seen = [], realFetch = global.fetch;
  global.fetch = fakeApiFetch(all, 1, seen, 99999);
  try {
    await S.run({ feedUrl: "https://wiki.psbdx.com/api/v1/posts?per_page=1", title: "x", body: "" });
    assert.equal(seen.length, S.DEFAULTS.maxPages);
  } finally { global.fetch = realFetch; }
});

test("title-coverage rule: one generic title word is not enough, a strong title match is", () => {
  assert.deepEqual(suggest("fingerprint", ""), []);              // 1 shared word, weak title overlap
  const r = apiSuggest("Orbot proxy mode only", "");              // 1 distinctive word + 4 shared title words
  assert.ok(r.length >= 1 && r[0].item.link.includes("orbot-to-proxy-mode"));
  assert.deepEqual(apiSuggest("proxy mode only", ""), []);        // no distinctive word: which app? stay silent
});

test("REGRESSION (live #29): a stray non-title word must not earn a second suggestion", () => {
  // Real output was a second suggestion "(matched: set)": the v2rayNG guide, from the words "where to set it up".
  const r = apiSuggest("How do I make Orbot work in proxy mode only", "I installed Orbot on Android 13 and want NetShield to use it as a SOCKS proxy but I do not see where to set it up.");
  assert.ok(r.length >= 1 && r[0].item.link.includes("orbot-to-proxy-mode"), "Orbot guide must still be first");
  for (const s of r.slice(1)) assert.ok(s.matchedInTitle >= 1 || s.matched.length >= 2, `weak extra suggestion: ${s.item.title} (${s.matched})`);
  assert.ok(!r.some((s) => s.item.link.includes("v2rayng") && s.matched.join() === "set"));
});
