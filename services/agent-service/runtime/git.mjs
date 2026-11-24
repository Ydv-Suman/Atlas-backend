import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { lstat, readFile, readlink } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
const exec = promisify(execFile);
const problem = (status, message) => Object.assign(new Error(message), { status });

export async function sessionGit({ root, operation = 'status', target, message, filePath, expectedRevision, expectedHead, repoUrl, token, identity, allowLocal = false }) {
  root = path.resolve(root);
  if ((await lstat(root)).isSymbolicLink() || (await lstat(path.join(root, '.git'))).isSymbolicLink()) throw problem(400, 'Invalid workspace repository.');
  const base = ['-c', `safe.directory=${root}`, '-c', 'core.hooksPath=/dev/null', '-c', 'core.fsmonitor=false', '-c', 'commit.gpgsign=false', '-c', 'tag.gpgsign=false', '-c', 'core.pager=cat', '-c', 'credential.helper=', '-c', 'submodule.recurse=false', '-c', 'protocol.ext.allow=never'];
  const env = { PATH: process.env.PATH, HOME: path.dirname(root), LANG: 'C.UTF-8', GIT_TERMINAL_PROMPT: '0', GIT_EDITOR: 'true', GIT_SEQUENCE_EDITOR: 'true', GIT_CONFIG_NOSYSTEM: '1', GIT_CONFIG_GLOBAL: '/dev/null', GIT_OPTIONAL_LOCKS: '0', GIT_LITERAL_PATHSPECS: '1', GIT_ATTR_NOSYSTEM: '1' };
  if (identity) {
    for (const key of ['name', 'email']) if (typeof identity[key] !== 'string' || !identity[key].trim() || /[\r\n\0<>]/.test(identity[key])) throw problem(400, 'Your Git author name and email are required.');
    Object.assign(env, { GIT_AUTHOR_NAME: identity.name, GIT_AUTHOR_EMAIL: identity.email, GIT_COMMITTER_NAME: identity.name, GIT_COMMITTER_EMAIL: identity.email });
  }
  const run = async (args, { optional = false, network = false } = {}) => {
    const extra = {};
    if (network) {
      if (!(allowLocal && path.isAbsolute(repoUrl ?? '')) && !/^https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+(?:\.git)?$/.test(repoUrl ?? '')) throw problem(400, 'Connect a GitHub repository before syncing.');
      if (!allowLocal && !token) throw problem(401, 'Reconnect GitHub before syncing.');
      const pairs = [['http.followRedirects', 'false'], ['protocol.allow', allowLocal ? 'always' : 'never'], ['protocol.https.allow', 'always']];
      if (token) pairs.push([`http.${repoUrl}.extraHeader`, `Authorization: Basic ${Buffer.from('x-access-token:' + token).toString('base64')}`]);
      extra.GIT_CONFIG_COUNT = String(pairs.length);
      pairs.forEach(([key, value], index) => { extra[`GIT_CONFIG_KEY_${index}`] = key; extra[`GIT_CONFIG_VALUE_${index}`] = value; });
    }
    try { return (await exec('git', [...base, ...args], { cwd: root, env: { ...env, ...extra }, timeout: 90000, maxBuffer: 8_000_000 })).stdout; }
    catch (error) {
      if (optional && !error.killed && !error.code?.startsWith?.('ERR_')) return null;
      if (error.killed) throw problem(504, 'Git timed out. Refresh the panel before retrying.');
      const output = String(error.stderr ?? '') + String(error.stdout ?? '');
      if (/Authentication failed|403|401|could not read Username|Repository not found/i.test(output)) throw problem(403, 'GitHub access was denied. Reconnect GitHub and check repository permissions.');
      if (/Not possible to fast-forward|divergent branches/i.test(output)) throw problem(409, 'The branches diverged. Fetch, then merge or rebase before pulling again.');
      if (/non-fast-forward|fetch first|rejected/i.test(output)) throw problem(409, 'The remote branch has newer commits. Fetch and pull before pushing.');
      if (/would be overwritten|untracked working tree|local changes/i.test(output)) throw problem(409, 'Commit your local changes before changing branches or pulling.');
      if (/shallow|unrelated histories/i.test(output)) throw problem(409, 'Fetch the repository history before merging or rebasing.');
      throw problem(409, 'Git could not complete this action. Refresh the panel and check for conflicts.');
    }
  };
  const exists = async name => !!await lstat(path.join(root, '.git', name)).catch(() => null);
  const state = async () => {
    const branch = (await run(['symbolic-ref', '--quiet', '--short', 'HEAD'], { optional: true }))?.trim() ?? null;
    const head = (await run(['rev-parse', '--verify', 'HEAD'])).trim();
    const raw = await run(['status', '--porcelain=v1', '-z', '--untracked-files=all']);
    const parts = raw.split('\0'), changes = [];
    for (let index = 0; index < parts.length && parts[index]; index++) {
      const code = parts[index].slice(0, 2), file = parts[index].slice(3);
      const renamed = /[RC]/.test(code), originalPath = renamed ? parts[++index] : undefined;
      const conflict = ['DD', 'AU', 'UD', 'UA', 'DU', 'AA', 'UU'].includes(code);
      changes.push({ path: file, ...(originalPath ? { originalPath } : {}), status: conflict ? 'C' : code === '??' || code.includes('A') ? 'A' : code.includes('D') ? 'D' : renamed ? 'R' : 'M' });
    }
    const upstream = branch ? (await run(['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{upstream}'], { optional: true }))?.trim() ?? null : null;
    const counts = upstream ? (await run(['rev-list', '--left-right', '--count', `HEAD...${upstream}`])).trim().split(/\s+/).map(Number) : [0, 0];
    const refs = await run(['for-each-ref', '--format=%(refname)%00%(symref)', 'refs/heads', 'refs/remotes/origin']);
    const branches = refs.trim().split('\n').filter(Boolean).flatMap(line => {
      const [ref, symbolic] = line.split('\0'); if (symbolic) return [];
      const remote = ref.startsWith('refs/remotes/');
      return [{ name: ref.replace(remote ? 'refs/remotes/' : 'refs/heads/', ''), remote }];
    });
    const pending = await exists('rebase-merge') || await exists('rebase-apply') ? 'rebase' : await exists('MERGE_HEAD') ? 'merge' : null;
    const hash = createHash('sha256').update(head).update(branch ?? '').update(pending ?? '').update(raw).update(await run(['diff', '--no-ext-diff', '--no-textconv', '--binary', 'HEAD', '--']));
    for (const entry of changes.filter(entry => entry.status === 'A')) {
      const file = path.join(root, entry.path), stat = await lstat(file).catch(() => null);
      if (stat?.isSymbolicLink()) hash.update(await readlink(file));
      else if (stat?.isFile()) hash.update((await run(['hash-object', '--no-filters', '--', entry.path])).trim());
    }
    return { branch, head, upstream, ahead: counts[0], behind: counts[1], changes, branches, operation: pending, revision: hash.digest('hex') };
  };
  let current = await state();
  if (operation === 'status') return current;
  if (operation === 'history') {
    const output = await run(['log', '-30', '--format=%h%x00%s%x00%an%x00%aI']);
    return output.trim().split('\n').filter(Boolean).map(line => { const [sha, subject, author, date] = line.split('\0'); return { sha, subject, author, date }; });
  }
  if (operation === 'diff') {
    const entry = current.changes.find(entry => entry.path === filePath);
    if (!entry) throw problem(404, 'This file has no uncommitted changes.');
    if (entry.status === 'A') {
      const stat = await lstat(path.join(root, entry.path));
      if (stat.isSymbolicLink()) return { path: entry.path, diff: `Added symbolic link: ${await readlink(path.join(root, entry.path))}` };
      if (stat.size > 1_000_000) return { path: entry.path, diff: 'This file is too large to preview.' };
      const bytes = await readFile(path.join(root, entry.path));
      let text; try { text = new TextDecoder('utf-8', { fatal: true }).decode(bytes); } catch { text = null; }
      return { path: entry.path, diff: text == null || text.includes('\0') ? 'Binary file added.' : `--- /dev/null\n+++ b/${entry.path}\n` + text.split('\n').map(line => '+' + line).join('\n') };
    }
    return { path: entry.path, diff: await run(['diff', '--no-ext-diff', '--no-textconv', 'HEAD', '--', ...(entry.originalPath ? [entry.originalPath] : []), entry.path]) };
  }
  if (!['commit', 'create-branch', 'switch', 'fetch', 'pull', 'push', 'merge', 'rebase', 'continue', 'abort'].includes(operation)) throw problem(400, 'Unknown Git action.');
  if (current.operation && !['continue', 'abort'].includes(operation)) throw problem(409, `Finish or abort the ${current.operation} first.`);
  if (['continue', 'abort'].includes(operation) && !current.operation) throw problem(409, 'No merge or rebase is in progress.');
  if (!current.branch && !current.operation && !['switch', 'create-branch', 'fetch'].includes(operation)) throw problem(409, 'Switch to a branch before changing the repository.');
  if (['switch', 'pull', 'merge', 'rebase'].includes(operation) && current.changes.length) throw problem(409, 'Commit your local changes before changing branches, pulling, merging, or rebasing.');
  if (['commit', 'switch', 'create-branch', 'pull', 'push', 'merge', 'rebase', 'continue', 'abort'].includes(operation) && expectedRevision !== current.revision) throw problem(409, 'The workspace changed. Refresh and review the latest changes first.');
  const checkBranch = async value => {
    if (typeof value !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$/.test(value) || !(await run(['check-ref-format', '--branch', value], { optional: true }))) throw problem(400, 'Enter a valid branch name.');
    return value;
  };
  const checkTarget = async value => {
    await checkBranch(value);
    const ref = current.branches.find(ref => ref.name === value);
    if (!ref) throw problem(404, 'Branch not found. Fetch to load remote branches.');
    return ref.remote ? `refs/remotes/${value}` : `refs/heads/${value}`;
  };
  const fetch = async () => {
    const shallow = (await run(['rev-parse', '--is-shallow-repository'])).trim() === 'true';
    await run(['fetch', '--no-tags', '--no-recurse-submodules', ...(shallow ? ['--unshallow'] : []), '--', repoUrl, '+refs/heads/*:refs/remotes/origin/*'], { network: true });
  };
  let note = 'Completed.';
  if (operation === 'commit') {
    if (typeof message !== 'string' || !message.trim() || message.length > 2000 || message.includes('\0')) throw problem(400, 'Enter a commit message of up to 2000 characters.');
    if (!current.changes.length) throw problem(409, 'There are no changes to commit.');
    if (current.changes.some(entry => entry.status === 'C')) throw problem(409, 'Resolve conflicts before committing.');
    await run(['add', '-A', '--', '.']); await run(['commit', '-m', message.trim()]); note = 'Changes committed locally.';
  }
  if (operation === 'create-branch') { await checkBranch(target); await run(['switch', '-c', target]); note = `Created ${target}.`; }
  if (operation === 'switch') {
    const ref = await checkTarget(target);
    if (ref.startsWith('refs/remotes/')) {
      const local = target.slice('origin/'.length);
      if (current.branches.some(branch => !branch.remote && branch.name === local)) await run(['switch', local]);
      else await run(['switch', '--track', '-c', local, target]);
    } else await run(['switch', target]);
    note = `Switched to ${target}.`;
  }
  if (operation === 'fetch') { await fetch(); note = 'Remote branches refreshed.'; }
  if (operation === 'pull') {
    if (!current.upstream?.startsWith('origin/')) throw problem(409, 'Push this branch first to set its remote tracking branch.');
    await fetch();
    await run(['merge', '--ff-only', `refs/remotes/${current.upstream}`]); note = 'Pulled latest commits.';
  }
  if (operation === 'push') {
    if (expectedHead !== current.head) throw problem(409, 'The branch changed. Refresh before pushing.');
    await checkBranch(current.branch);
    await run(['push', '--porcelain', '--no-verify', '--', repoUrl, `HEAD:refs/heads/${current.branch}`], { network: true });
    await run(['config', `branch.${current.branch}.remote`, 'origin']); await run(['config', `branch.${current.branch}.merge`, `refs/heads/${current.branch}`]);
    await run(['update-ref', `refs/remotes/origin/${current.branch}`, current.head]); note = `Pushed ${current.branch}.`;
  }
  if (operation === 'merge' || operation === 'rebase') {
    const ref = await checkTarget(target);
    try { await run(operation === 'merge' ? ['merge', '--no-edit', ref] : ['rebase', '--no-autostash', ref]); note = `${operation === 'merge' ? 'Merged' : 'Rebased onto'} ${target}.`; }
    catch (error) { const next = await state(); if (!next.operation) throw error; note = 'Resolve the conflicting files, then continue or abort.'; }
  }
  if (operation === 'continue') {
    for (const entry of current.changes.filter(entry => entry.status === 'C')) {
      const content = await readFile(path.join(root, entry.path), 'utf8').catch(error => { if (error.code === 'ENOENT') return ''; throw error; });
      if (/^(<<<<<<<|>>>>>>>) /m.test(content)) throw problem(409, 'Remove the conflict markers in the conflicting files before continuing.');
    }
    await run(['add', '-A', '--', '.']);
    try { await run(current.operation === 'rebase' ? ['rebase', '--continue'] : ['commit', '--no-edit']); note = 'Continued successfully.'; }
    catch (error) { if (!(await state()).operation) throw error; note = 'More conflicts need to be resolved before continuing.'; }
  }
  if (operation === 'abort') { await run([current.operation, '--abort']); note = `Aborted ${current.operation}.`; }
  return { message: note, status: await state() };
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.umask(0o077);
  let input = ''; for await (const chunk of process.stdin) { input += chunk; if (input.length > 2_000_000) process.exit(1); }
  try { console.log(JSON.stringify({ ok: true, data: await sessionGit(JSON.parse(input)) })); }
  catch (error) { console.log(JSON.stringify({ ok: false, status: error.status ?? 400, message: error.status ? error.message : 'Could not access this Git workspace.' })); }
}
