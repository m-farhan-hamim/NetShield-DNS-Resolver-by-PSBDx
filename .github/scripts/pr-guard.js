'use strict';

/**
 * Pull request bot logic (kept out of the workflow YAML so it can be unit-tested).
 *
 * It is run by .github/workflows/pr-guard.yml on `pull_request_target`, i.e. from the BASE branch,
 * so a pull request can never change the code that checks it. It never checks out or runs the
 * pull request's code: it only reads the diff through the GitHub API.
 *
 * What it checks:
 *   1. Protected files: only the maintainers may edit them (the maintainers are exempt).
 *   2. A tripwire for risky code: binaries, dynamic code loading, shell execution, hidden unicode,
 *      exfiltration endpoints, dangerous permissions, ... This is a pattern check and NOT a malware
 *      scanner. It catches the obvious and the lazy; a human review is still required.
 *   3. That the pull request template was filled in (maintainers are exempt).
 */

const OWNER_LOGINS = ['m-farhan-hamim'];
const MAINTAINER_PERMISSIONS = ['admin', 'maintain'];
/** Leave false: closing someone's pull request automatically is the maintainer's call. */
const CLOSE_ON_PROTECTED_EDIT = false;
const MARKER = '<!-- netshield-pr-guard -->';
const MAX_FINDINGS_SHOWN = 25;
const MAX_FILES = 3000; // the GitHub API stops listing files here

const LABELS = {
  'protected-files': { color: 'b60205', description: 'Edits files that only maintainers may change' },
  'security-review': { color: 'd93f0b', description: 'Automated check found risky code patterns' },
  'review-carefully': { color: 'fbca04', description: 'Touches build, dependency, manifest or network code' },
  'incomplete-template': { color: 'cfd3d7', description: 'The pull request form is not filled in' },
};

// ---------------------------------------------------------------- protected files

/** Files only maintainers may edit: everything under .github/ plus the community files. */
function isProtectedPath(path) {
  const p = String(path || '').replace(/\\/g, '/').replace(/^\.?\/+/, '').toLowerCase();
  if (p === '.github' || p.startsWith('.github/')) return true;
  const parts = p.split('/');
  const base = parts[parts.length - 1];
  const dir = parts.slice(0, -1).join('/');
  const communityFile = /^(readme|code_of_conduct|security|contributing|accessibility)(\.[a-z0-9]+)?$/.test(base);
  return communityFile && (dir === '' || dir === 'docs');
}

// ---------------------------------------------------------------- scanning

const ALLOWED_BINARY_EXT = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif', 'ico', 'ttf', 'otf', 'woff', 'woff2']);
const BLOCKED_EXT = new Set([
  'apk', 'aab', 'apks', 'xapk', 'dex', 'jar', 'aar', 'war', 'ear', 'so', 'a', 'o', 'dll', 'dylib', 'exe', 'msi',
  'bin', 'class', 'elf', 'deb', 'rpm', 'dmg', 'zip', '7z', 'rar', 'tar', 'gz', 'tgz', 'xz', 'bz2', 'lz4', 'zst',
]);
const SCRIPT_EXT = new Set(['sh', 'bash', 'bat', 'cmd', 'ps1', 'py', 'pl', 'rb']);
const CODE_EXT = new Set(['java', 'kt', 'kts', 'gradle', 'groovy', 'js', 'mjs', 'cjs', 'ts', 'py', 'sh', 'bash',
  'bat', 'cmd', 'ps1', 'c', 'cc', 'cpp', 'h', 'hpp', 'rs', 'go', 'pl', 'rb']);
const DOC_EXT = new Set(['md', 'txt', 'rst', 'adoc']);

const DANGEROUS_PERMISSIONS = new Set([
  'READ_SMS', 'SEND_SMS', 'RECEIVE_SMS', 'RECEIVE_MMS', 'RECEIVE_WAP_PUSH', 'READ_CONTACTS', 'WRITE_CONTACTS',
  'GET_ACCOUNTS', 'READ_CALL_LOG', 'WRITE_CALL_LOG', 'PROCESS_OUTGOING_CALLS', 'CALL_PHONE', 'ANSWER_PHONE_CALLS',
  'READ_PHONE_STATE', 'READ_PHONE_NUMBERS', 'RECORD_AUDIO', 'CAMERA', 'ACCESS_FINE_LOCATION',
  'ACCESS_COARSE_LOCATION', 'ACCESS_BACKGROUND_LOCATION', 'MANAGE_EXTERNAL_STORAGE', 'REQUEST_INSTALL_PACKAGES',
  'REQUEST_DELETE_PACKAGES', 'INSTALL_PACKAGES', 'SYSTEM_ALERT_WINDOW', 'BIND_ACCESSIBILITY_SERVICE',
  'BIND_DEVICE_ADMIN', 'WRITE_SETTINGS', 'WRITE_SECURE_SETTINGS', 'READ_LOGS', 'BODY_SENSORS',
]);

/** Hosts that may appear in new URLs without a heads-up (matched as a suffix). */
const URL_ALLOWLIST = [
  'github.com', 'githubusercontent.com', 'f-droid.org', 'izzysoft.de', 'psbdx.com', 'android.com', 'google.com',
  'gstatic.com', 'googleapis.com', 'schemas.android.com', 'w3.org', 'apache.org', 'gnu.org', 'creativecommons.org',
  'opensource.org', 'mozilla.org', 'shields.io', 'gradle.org', 'maven.org', 'cloudflare.com', 'cloudflare-dns.com',
  'one.one.one.one', 'dns.google', 'quad9.net', 'adguard.com', 'adguard-dns.com', 'opendns.com', 'nextdns.io',
  'mullvad.net', 'example.com', 'example.org', 'example.net', 'example.test', 'localhost', 'v2ray.com', 'xeovo.com',
];

const HIDDEN_UNICODE = /[\u202A-\u202E\u2066-\u2069\u200B-\u200D\u2060\uFEFF]/;

/** Each rule: id, severity (high = blocks outside contributors, medium = heads-up), message, test(line). */
const RULES = [
  { id: 'dynamic-code', sev: 'high', exts: CODE_EXT, msg: 'Loads code at runtime (class loader)',
    re: /\b(DexClassLoader|InMemoryDexClassLoader|BaseDexClassLoader|PathClassLoader|URLClassLoader)\b/ },
  { id: 'native-load', sev: 'high', exts: CODE_EXT, msg: 'Loads native code',
    re: /\bSystem\s*\.\s*(load|loadLibrary)\s*\(|\bRuntime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*load(Library)?\s*\(/ },
  { id: 'shell-exec', sev: 'high', exts: CODE_EXT, msg: 'Runs a shell/OS command',
    re: /\bRuntime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\s*\(|\bnew\s+ProcessBuilder\s*\(|\bProcessBuilder\s*\(\s*["'a-zA-Z]/ },
  { id: 'root-su', sev: 'high', exts: CODE_EXT, msg: 'Tries to get root (su)',
    re: /["'](\/system\/(x?bin)\/)?su["']|\bsu\s+-c\b|\bMagisk\b/ },
  { id: 'exfil-endpoint', sev: 'high', exts: null, msg: 'Sends data to a chat/webhook endpoint',
    re: /api\.telegram\.org\/bot|discord(app)?\.com\/api\/webhooks|hooks\.slack\.com\/services/i },
  { id: 'paste-tunnel', sev: 'medium', exts: null, msg: 'Talks to a paste/tunnel service',
    re: /pastebin\.com\/raw|transfer\.sh|\bngrok(-free)?\.(io|app|dev)\b|requestbin|webhook\.site|pipedream\.net/i },
  { id: 'crypto-miner', sev: 'high', exts: null, msg: 'Mentions cryptocurrency mining',
    re: /\b(stratum\+tcp|xmrig|coinhive|cryptonight|minexmr|nicehash)\b/i },
  { id: 'private-data', sev: 'high', exts: CODE_EXT, msg: 'Reads SMS, contacts, call log or device identifiers',
    re: /content:\/\/(sms|mms|contacts|call_log)|\bContactsContract\b|Telephony\.Sms|\bSmsManager\b|\bgetLine1Number\s*\(|\bgetImei\s*\(|\bgetDeviceId\s*\(|\bgetSubscriberId\s*\(/ },
  { id: 'accessibility-admin-abuse', sev: 'high', exts: null, msg: 'Accessibility-service or device-admin hooks',
    re: /BIND_ACCESSIBILITY_SERVICE|extends\s+AccessibilityService\b|\bDeviceAdminReceiver\b|BIND_DEVICE_ADMIN/ },
  { id: 'trust-all-tls', sev: 'high', exts: CODE_EXT, msg: 'Turns off TLS certificate/hostname checks',
    re: /ALLOW_ALL_HOSTNAME_VERIFIER|checkServerTrusted\s*\([^)]*\)[^{;]*\{\s*\}|\b(TrustAllCerts|trustAllCerts|InsecureTrustManager|NoopHostnameVerifier)\b/ },
  { id: 'pipe-to-shell', sev: 'high', exts: null, msg: 'Downloads and runs a script in one step',
    re: /\b(curl|wget)\b[^|\n]*\|\s*(sudo\s+)?(ba|z|da)?sh\b|base64\s+(-d|--decode)[^|\n]*\|\s*(ba|z)?sh\b|powershell[^\n]*-(enc|encodedcommand)\b/i },
  { id: 'eval', sev: 'high', exts: new Set(['js', 'mjs', 'cjs', 'ts', 'py', 'sh', 'bash', 'ps1']), msg: 'Runs text as code (eval)',
    re: /\beval\s*\(|\bnew\s+Function\s*\(|\bexec\s*\(\s*(compile|base64)/ },
  { id: 'hex-escape-run', sev: 'high', exts: CODE_EXT, msg: 'Long run of escaped characters (obfuscation)',
    re: /(\\x[0-9a-fA-F]{2}){16,}|(\\u[0-9a-fA-F]{4}){16,}/ },
  { id: 'encoded-blob', sev: 'high', exts: null, msg: 'Very long encoded string (possible hidden payload)',
    re: /[A-Za-z0-9+\/=_-]{300,}/ },
  { id: 'svg-script', sev: 'high', exts: new Set(['svg', 'html', 'htm']), msg: 'Script in an SVG/HTML file',
    re: /<script\b|\bonload\s*=|\bonerror\s*=|javascript:/i },
  { id: 'reflection', sev: 'medium', exts: CODE_EXT, msg: 'Uses reflection',
    re: /\bClass\s*\.\s*forName\s*\(|\bgetDeclaredMethod\s*\(|\bgetDeclaredField\s*\(|\bsetAccessible\s*\(\s*true\s*\)/ },
  { id: 'webview-js', sev: 'medium', exts: CODE_EXT, msg: 'Enables JavaScript in a WebView',
    re: /\bsetJavaScriptEnabled\s*\(\s*true\s*\)|\baddJavascriptInterface\s*\(/ },
  { id: 'exported-component', sev: 'medium', exts: new Set(['xml']), msg: 'Exports an Android component',
    re: /android:exported\s*=\s*"true"/ },
  { id: 'cleartext-debuggable', sev: 'medium', exts: new Set(['xml']), msg: 'Allows cleartext traffic or marks the app debuggable',
    re: /usesCleartextTraffic\s*=\s*"true"|android:debuggable\s*=\s*"true"|cleartextTrafficPermitted\s*=\s*"true"/ },
];

function extOf(path) {
  const base = String(path).split('/').pop();
  const i = base.lastIndexOf('.');
  return i <= 0 ? '' : base.slice(i + 1).toLowerCase();
}

/** Added lines of a unified-diff patch with their new line numbers. */
function addedLines(patch) {
  const out = [];
  let n = 0;
  for (const raw of String(patch || '').split('\n')) {
    const h = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(raw);
    if (h) { n = parseInt(h[1], 10); continue; }
    if (raw.startsWith('+')) { out.push({ line: n, text: raw.slice(1) }); n++; }
    else if (raw.startsWith('-') || raw.startsWith('\\')) { /* removed line / "no newline" marker */ }
    else n++;
  }
  return out;
}

function hostAllowed(host) {
  const h = host.toLowerCase();
  return URL_ALLOWLIST.some((a) => h === a || h.endsWith('.' + a));
}

function snippet(text, max = 90) {
  return String(text).replace(/[`\r\n\t]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);
}

function isBuildFile(path) {
  return /(^|\/)(settings|build)\.gradle(\.kts)?$/.test(path) || /\.gradle(\.kts)?$/.test(path)
    || /(^|\/)libs\.versions\.toml$/.test(path) || /(^|\/)gradle\.properties$/.test(path)
    || /(^|\/)gradle\/wrapper\//.test(path) || /(^|\/)proguard[^/]*\.pro$/.test(path)
    || /(^|\/)AndroidManifest\.xml$/.test(path) || /(^|\/)network_security_config\.xml$/.test(path)
    || /(^|\/)(backup_rules|data_extraction_rules)\.xml$/.test(path);
}

/** Findings for ONE changed file: [{sev, rule, file, line, msg, text}] */
function scanFile(file) {
  const findings = [];
  const path = file.filename;
  const ext = extOf(path);
  const add = (sev, rule, line, msg, text) => findings.push({ sev, rule, file: path, line, msg, text: snippet(text || '') });

  if (file.status === 'removed') return findings;

  if (BLOCKED_EXT.has(ext)) {
    add('high', 'binary-file', 0, 'Binary, archive or executable file (not allowed)', path);
    return findings;
  }
  if (SCRIPT_EXT.has(ext) && file.status === 'added') {
    add('medium', 'new-script', 0, 'New script file', path);
  }
  if (file.status === 'changed') {
    add('medium', 'mode-change', 0, 'File mode changed (for example made executable)', path);
  }
  if (isBuildFile(path)) {
    add('medium', 'build-config', 0, 'Changes build, dependency or manifest configuration', path);
  }

  if (!file.patch) {
    const changes = (file.additions || 0) + (file.deletions || 0) + (file.changes || 0);
    const keepFile = /(^|\/)\.(gitkeep|keep)$/.test(path);
    if (file.status !== 'renamed' && !ALLOWED_BINARY_EXT.has(ext) && !keepFile) {
      if (changes === 0) add('high', 'binary-file', 0, 'File with no readable diff (binary?)', path);
      else add('medium', 'diff-too-large', 0, 'Diff too large to inspect automatically', path);
    }
    return findings;
  }

  const perRule = {};
  const report = (sev, rule, line, msg, text) => {
    const key = rule;
    perRule[key] = (perRule[key] || 0) + 1;
    if (perRule[key] <= 3) add(sev, rule, line, msg, text);
  };

  const isManifest = /(^|\/)AndroidManifest\.xml$/.test(path);
  const isGradle = /\.gradle(\.kts)?$/.test(path);
  const lines = addedLines(file.patch);
  lines.forEach((l, idx) => {
    let text = l.text;
    if (idx === 0 && text.charCodeAt(0) === 0xFEFF) text = text.slice(1); // a leading BOM is harmless
    if (HIDDEN_UNICODE.test(text)) {
      report('high', 'hidden-unicode', l.line, 'Invisible or direction-changing unicode character', text);
    }
    for (const r of RULES) {
      if (r.exts && !r.exts.has(ext)) continue;
      if (DOC_EXT.has(ext) && r.id !== 'encoded-blob' && r.id !== 'exfil-endpoint' && r.id !== 'crypto-miner'
        && r.id !== 'pipe-to-shell') continue;
      if (r.re.test(text)) report(r.sev, r.id, l.line, r.msg, text);
    }
    if (isManifest) {
      const m = /<uses-permission[^>]*android:name\s*=\s*"([^"]+)"/.exec(text);
      if (m) {
        const short = m[1].split('.').pop();
        if (DANGEROUS_PERMISSIONS.has(short)) {
          report('high', 'dangerous-permission', l.line, 'Adds a sensitive permission: ' + short, text);
        } else {
          report('medium', 'new-permission', l.line, 'Adds a permission: ' + short, text);
        }
      }
    }
    if (isGradle && /\b(versionCode|versionName)\b/.test(text)) {
      report('medium', 'version-change', l.line, 'Changes the app version (maintainers do this)', text);
    }
    const urlRe = /https?:\/\/([a-z0-9][a-z0-9.-]*)/gi;
    let um;
    while ((um = urlRe.exec(text)) !== null) {
      if (!hostAllowed(um[1])) report('medium', 'new-url', l.line, 'New URL to ' + um[1], um[0]);
    }
  });
  return findings;
}

// ---------------------------------------------------------------- template

const REQUIRED_HEADINGS = ['Summary', 'What changed and why', 'How I tested it', 'Checklist'];
const REQUIRED_CHECKS = [
  'I have read CONTRIBUTING.md',
  'does not edit protected files',
  'adds no binary files',
  'adds no tracking',
  'did not change versionName or versionCode',
  'I tested this change myself',
  'I followed ACCESSIBILITY.md',
  'I understand every line',
  'licensed under the project',
];

function sectionOf(body, heading) {
  const re = new RegExp('^##\\s+' + heading.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s*$', 'im');
  const m = re.exec(body);
  if (!m) return null;
  const rest = body.slice(m.index + m[0].length);
  const next = /^##\s+/m.exec(rest);
  return (next ? rest.slice(0, next.index) : rest).trim();
}

/** Problems with the filled-in pull request form; [] when it is complete. */
function checkTemplate(rawBody) {
  const problems = [];
  const body = String(rawBody || '').replace(/<!--[\s\S]*?-->/g, '');
  for (const h of REQUIRED_HEADINGS) {
    if (sectionOf(body, h) === null) problems.push('The section "' + h + '" is missing. Please keep the pull request form as it is.');
  }
  for (const h of ['Summary', 'What changed and why', 'How I tested it']) {
    const s = sectionOf(body, h);
    if (s !== null && s.replace(/\s+/g, ' ').length < 15) problems.push('Please fill in "' + h + '" (a sentence or two).');
  }
  const type = sectionOf(body, 'Type of change');
  if (type === null || !/^\s*[-*]\s*\[[xX]\]/m.test(type)) problems.push('Please tick at least one box under "Type of change".');
  const checklist = sectionOf(body, 'Checklist');
  if (checklist !== null) {
    for (const phrase of REQUIRED_CHECKS) {
      const line = checklist.split('\n').find((l) => l.toLowerCase().includes(phrase.toLowerCase()));
      if (!line) problems.push('The checklist item "' + phrase + '..." is missing. Please keep the checklist as it is.');
      else if (!/\[[xX]\]/.test(line)) problems.push('Please confirm the checklist item "' + phrase + '...".');
    }
  }
  return problems;
}

// ---------------------------------------------------------------- decision

/**
 * @param {{files: Array, isMaintainer: boolean, body: string}} input
 * Maintainers are exempt from the protected-files rule and the template. Their pull requests are
 * still scanned, but risky patterns are then only a heads-up (never blocking).
 */
function evaluate({ files, isMaintainer, body }) {
  const failures = [];
  const warnings = [];
  const findings = [];
  const protectedHits = [];
  const labels = new Set();

  if (files.length >= MAX_FILES) {
    failures.push('This pull request changes ' + files.length + '+ files, more than can be inspected automatically. Please split it up.');
  }

  for (const f of files) {
    if (isProtectedPath(f.filename) || (f.previous_filename && isProtectedPath(f.previous_filename))) {
      protectedHits.push(f.filename);
    }
    findings.push(...scanFile(f));
  }

  if (protectedHits.length && !isMaintainer) {
    labels.add('protected-files');
    failures.push('This pull request edits protected files that only maintainers may change: '
      + protectedHits.slice(0, 10).map((p) => '`' + snippet(p, 80) + '`').join(', ')
      + (protectedHits.length > 10 ? ' and ' + (protectedHits.length - 10) + ' more' : '') + '.');
  }

  const high = findings.filter((f) => f.sev === 'high');
  const medium = findings.filter((f) => f.sev === 'medium');
  if (high.length) {
    labels.add('security-review');
    const msg = high.length + ' risky pattern' + (high.length === 1 ? '' : 's') + ' found (listed below).';
    if (isMaintainer) warnings.push(msg + ' Not blocking, because the author is a maintainer.');
    else failures.push(msg + ' A maintainer has to review them before this can be merged.');
  }
  if (medium.length) {
    labels.add('review-carefully');
    warnings.push(medium.length + ' item' + (medium.length === 1 ? '' : 's') + ' to look at more closely (build files, permissions, new URLs, ...).');
  }

  if (!isMaintainer) {
    const problems = checkTemplate(body);
    if (problems.length) {
      labels.add('incomplete-template');
      failures.push('The pull request form is not complete:\n' + problems.map((p) => '   - ' + p).join('\n'));
    }
  }

  return { failures, warnings, findings, protectedHits, labels: [...labels], isMaintainer };
}

// ---------------------------------------------------------------- comment

function renderComment(result) {
  const out = [MARKER, '### Pull request check', ''];
  if (result.failures.length) out.push('**Changes needed** before this can be merged.', '');
  else if (result.warnings.length) out.push('**Passed**, but a maintainer should look more closely.', '');
  else out.push('**All checks passed.** A maintainer will review this when they can.', '');

  if (result.failures.length) {
    out.push('#### What needs fixing');
    for (const f of result.failures) out.push('- ' + f);
    out.push('');
    if (result.protectedHits.length && !result.isMaintainer) {
      out.push('To undo changes to a protected file in your branch: `git checkout origin/main -- <file>` and push again.',
        'If you think a protected file really needs a change, please open an issue and explain it instead.', '');
    }
  }
  if (result.warnings.length) {
    out.push('#### For the reviewer');
    for (const w of result.warnings) out.push('- ' + w);
    out.push('');
  }
  if (result.findings.length) {
    const rows = result.findings.slice(0, MAX_FINDINGS_SHOWN);
    out.push('<details><summary>' + result.findings.length + ' finding' + (result.findings.length === 1 ? '' : 's') + '</summary>', '',
      '| Level | Where | What |', '|---|---|---|');
    for (const f of rows) {
      const where = '`' + snippet(f.file, 70) + (f.line ? ':' + f.line : '') + '`';
      const what = f.msg + (f.text && f.rule !== 'binary-file' ? ': `' + f.text + '`' : '');
      out.push('| ' + f.sev + ' | ' + where + ' | ' + what.replace(/\|/g, '\\|') + ' |');
    }
    if (result.findings.length > rows.length) out.push('| | | ... and ' + (result.findings.length - rows.length) + ' more |');
    out.push('', '</details>', '');
  }
  out.push('<sub>This is an automated pattern check. It is not a malware scanner and does not replace a human review. ' +
    'It re-runs when you push or edit the description. See CONTRIBUTING.md for what is checked.</sub>');
  return out.join('\n');
}

// ---------------------------------------------------------------- GitHub glue

async function isMaintainerLogin(reader, owner, repo, login, association, core) {
  if (association === 'OWNER' || OWNER_LOGINS.includes(login)) return true;
  try {
    const { data } = await reader.rest.repos.getCollaboratorPermissionLevel({ owner, repo, username: login });
    return MAINTAINER_PERMISSIONS.includes(data.permission);
  } catch (e) {
    if (e.status !== 404) core.warning('Could not read ' + login + "'s permission: " + e.message);
    return false;
  }
}

/**
 * A client for posting comments and labels. Uses the bot's own token when one was created, so the
 * comment comes from the bot; otherwise the default token. github-script does not hand us a
 * getOctokit helper in every version, so try the ways of making a client in turn.
 */
function makeWriter(github, token, getOctokit, core) {
  if (!token) return github;
  const attempts = [
    () => getOctokit(token),
    () => new github.constructor({ auth: token }),
  ];
  for (const make of attempts) {
    try {
      const client = make();
      if (client && client.rest) return client;
    } catch (e) {
      core.info('Writer client attempt failed: ' + e.message);
    }
  }
  core.warning('Could not create a client for the bot token; using the default token.');
  return github;
}

async function run({ github, context, core, getOctokit, env = process.env }) {
  const { owner, repo } = context.repo;
  const dry = String(env.DRY_RUN || '').toLowerCase() === 'true';
  let pr = context.payload.pull_request;
  if (!pr) {
    const number = parseInt(env.PR_NUMBER || '', 10);
    if (!number) { core.setFailed('No pull request to check.'); return null; }
    pr = (await github.rest.pulls.get({ owner, repo, pull_number: number })).data;
  }
  const number = pr.number;
  const writer = makeWriter(github, env.BOT_TOKEN, getOctokit, core);

  const isMaintainer = await isMaintainerLogin(github, owner, repo, pr.user.login, pr.author_association, core);
  const files = await github.paginate(github.rest.pulls.listFiles, { owner, repo, pull_number: number, per_page: 100 });
  const result = evaluate({ files, isMaintainer, body: pr.body || '' });
  const comment = renderComment(result);

  core.info('Author: ' + pr.user.login + (isMaintainer ? ' (maintainer)' : ''));
  core.info('Files: ' + files.length + ', findings: ' + result.findings.length
    + ', failures: ' + result.failures.length + ', warnings: ' + result.warnings.length);
  core.info(comment);
  if (dry) {
    try {
      const rl = await writer.rest.rateLimit.get();
      core.info('Write client works (' + (writer === github ? 'default token' : 'bot token') + ', rate limit '
        + rl.data.rate.limit + ').');
    } catch (e) {
      core.warning('Write client check failed: ' + e.message);
    }
    core.info('Dry run: nothing was posted.');
    return result;
  }

  // labels
  for (const [name, def] of Object.entries(LABELS)) {
    try { await writer.rest.issues.getLabel({ owner, repo, name }); }
    catch (e) { if (e.status === 404) { try { await writer.rest.issues.createLabel({ owner, repo, name, ...def }); } catch (_) { /* raced */ } } }
  }
  const current = (pr.labels || []).map((l) => (typeof l === 'string' ? l : l.name));
  for (const name of Object.keys(LABELS)) {
    if (!result.labels.includes(name) && current.includes(name)) {
      try { await writer.rest.issues.removeLabel({ owner, repo, issue_number: number, name }); } catch (_) { /* already gone */ }
    }
  }
  const toAdd = result.labels.filter((n) => !current.includes(n));
  if (toAdd.length) await writer.rest.issues.addLabels({ owner, repo, issue_number: number, labels: toAdd });

  // one comment, updated in place (nothing to say for a clean maintainer pull request)
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: number, per_page: 100 });
  const mine = comments.find((c) => c.body && c.body.includes(MARKER));
  const quiet = isMaintainer && !result.failures.length && !result.warnings.length;
  if (mine) {
    if (mine.body !== comment) await writer.rest.issues.updateComment({ owner, repo, comment_id: mine.id, body: comment });
  } else if (!quiet) {
    await writer.rest.issues.createComment({ owner, repo, issue_number: number, body: comment });
  }

  if (CLOSE_ON_PROTECTED_EDIT && result.protectedHits.length && !isMaintainer && pr.state === 'open') {
    await writer.rest.pulls.update({ owner, repo, pull_number: number, state: 'closed' });
  }
  if (result.failures.length) core.setFailed(result.failures.length + ' problem(s) found. See the pull request comment.');
  return result;
}

module.exports = {
  run, evaluate, scanFile, checkTemplate, isProtectedPath, addedLines, renderComment, extOf,
  REQUIRED_CHECKS, REQUIRED_HEADINGS, MARKER, LABELS,
};
