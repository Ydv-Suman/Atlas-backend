import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, readFile, rm } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';
import { sessionGit } from './git.mjs';
const identity = { name: 'Atlas Test', email: 'atlas@example.invalid' };
const git = (root, ...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
async function fixture() {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'atlas-git-'));
  const remote = path.join(directory, 'remote.git'), root = path.join(directory, 'repo');
  await mkdir(remote); git(remote, 'init', '--bare', '--initial-branch=main');
  git(directory, 'clone', remote, root); git(root, 'config', 'user.name', identity.name); git(root, 'config', 'user.email', identity.email);
  await writeFile(path.join(root, 'hello.py'), 'print("base")\n'); git(root, 'add', '.'); git(root, 'commit', '-m', 'base'); git(root, 'push', '-u', 'origin', 'main');
  const run = (operation, args = {}) => sessionGit({ root, operation, identity, repoUrl: remote, allowLocal: true, ...args });
  const action = async (operation, args = {}) => run(operation, { expectedRevision: (await run('status')).revision, ...args });
  return { directory, root, remote, run, action, cleanup: () => rm(directory, { recursive: true, force: true }) };
}
test('status, tracked/untracked diffs, commit, branches, and local push preserve real workspace changes', async () => {
  const f = await fixture();
  try {
    await writeFile(path.join(f.root, 'hello.py'), 'print("edited")\n');
    await writeFile(path.join(f.root, 'new file.py'), 'print("new")\n');
    let status = await f.run('status'); assert.equal(status.branch, 'main'); assert.equal(status.changes.length, 2);
    assert.match((await f.run('diff', { filePath: 'hello.py' })).diff, /edited/);
    assert.match((await f.run('diff', { filePath: 'new file.py' })).diff, /\+print\("new"\)/);
    await f.action('create-branch', { target: 'feat/test' });
    assert.equal((await f.run('status')).branch, 'feat/test');
    await f.action('commit', { message: 'Add changes' }); status = await f.run('status'); assert.equal(status.changes.length, 0);
    await f.run('push', { expectedHead: status.head, expectedRevision: status.revision }); assert.equal(git(f.remote, 'rev-parse', 'refs/heads/feat/test'), status.head);
    await f.action('switch', { target: 'main' }); assert.equal(await readFile(path.join(f.root, 'hello.py'), 'utf8'), 'print("base")\n');
    await f.action('merge', { target: 'feat/test' }); assert.match(await readFile(path.join(f.root, 'hello.py'), 'utf8'), /edited/);
    assert.equal((await f.run('history'))[0].subject, 'Add changes');
  } finally { await f.cleanup(); }
});
test('review revisions detect same-status content changes; dirty switches and invalid targets cannot discard work', async () => {
  const f = await fixture();
  try {
    await writeFile(path.join(f.root, 'hello.py'), 'first'); const before = await f.run('status');
    await writeFile(path.join(f.root, 'hello.py'), 'second');
    await assert.rejects(() => f.run('commit', { message: 'stale review', expectedRevision: before.revision }), error => error.status === 409);
    await assert.rejects(() => f.action('switch', { target: 'main' }), error => error.status === 409);
    await assert.rejects(() => f.action('create-branch', { target: '--orphan=bad' }), error => error.status === 400);
    await assert.rejects(() => f.run('diff', { filePath: '../outside' }), error => error.status === 404);
    assert.equal(await readFile(path.join(f.root, 'hello.py'), 'utf8'), 'second');
  } finally { await f.cleanup(); }
});
test('fetch discovers remote branches and pull fast-forwards without creating a merge commit', async () => {
  const f = await fixture();
  try {
    const other = path.join(f.directory, 'other'); git(f.directory, 'clone', f.remote, other);
    git(other, 'config', 'user.name', identity.name); git(other, 'config', 'user.email', identity.email);
    await writeFile(path.join(other, 'remote.txt'), 'remote'); git(other, 'add', '.'); git(other, 'commit', '-m', 'remote change'); git(other, 'push');
    git(other, 'branch', 'remote-feature'); git(other, 'push', 'origin', 'remote-feature');
    await f.run('fetch'); let status = await f.run('status'); assert.equal(status.behind, 1);
    assert.ok(status.branches.some(branch => branch.name === 'origin/remote-feature'));
    await f.action('switch', { target: 'origin/main' });
    await f.action('pull'); assert.equal(await readFile(path.join(f.root, 'remote.txt'), 'utf8'), 'remote');
    assert.equal((await f.run('status')).behind, 0);
  } finally { await f.cleanup(); }
});
test('rebase conflicts remain editable, refuse unresolved markers, and can continue or abort', async () => {
  const f = await fixture();
  try {
    await f.action('create-branch', { target: 'feature' });
    await writeFile(path.join(f.root, 'hello.py'), 'feature\n'); await f.action('commit', { message: 'feature' });
    await f.action('switch', { target: 'main' });
    await writeFile(path.join(f.root, 'hello.py'), 'main\n'); await f.action('commit', { message: 'main' });
    await f.action('switch', { target: 'feature' }); const previous = (await f.run('status')).head;
    let result = await f.action('rebase', { target: 'main' }); assert.equal(result.status.operation, 'rebase'); assert.equal(result.status.changes[0].status, 'C');
    await assert.rejects(() => f.action('continue'), error => error.status === 409);
    await f.action('abort'); assert.equal((await f.run('status')).head, previous); assert.equal(await readFile(path.join(f.root, 'hello.py'), 'utf8'), 'feature\n');
    await f.action('rebase', { target: 'main' }); await writeFile(path.join(f.root, 'hello.py'), 'resolved\n');
    result = await f.action('continue'); assert.equal(result.status.operation, null); assert.equal(result.status.branch, 'feature');
    assert.equal(await readFile(path.join(f.root, 'hello.py'), 'utf8'), 'resolved\n');
  } finally { await f.cleanup(); }
});


test('a branch change at the same HEAD invalidates an earlier push review', async () => {
  const f = await fixture();
  try {
    const before = await f.run('status');
    await f.action('create-branch', { target: 'other' });
    const after = await f.run('status');
    assert.equal(before.head, after.head); assert.notEqual(before.revision, after.revision);
    await assert.rejects(() => f.run('push', { expectedRevision: before.revision, expectedHead: before.head }), error => error.status === 409);
    assert.equal(git(f.remote, 'branch', '--list', 'other'), '');
  } finally { await f.cleanup(); }
});


test('a detached HEAD can be recovered by creating or switching to a branch', async () => {
  const f = await fixture();
  try {
    git(f.root, 'checkout', '--detach', 'HEAD');
    assert.equal((await f.run('status')).branch, null);
    await f.action('create-branch', { target: 'recovered' });
    assert.equal((await f.run('status')).branch, 'recovered');
    git(f.root, 'checkout', '--detach', 'HEAD');
    await f.action('switch', { target: 'main' });
    assert.equal((await f.run('status')).branch, 'main');
  } finally { await f.cleanup(); }
});
