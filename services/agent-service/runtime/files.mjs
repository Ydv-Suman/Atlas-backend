import { readFile, writeFile, mkdir, rename, unlink, lstat, readdir, rmdir } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const problem = (status, message) => Object.assign(new Error(message), { status });

export async function sessionFiles({ root, operation, filePath = '', content, etag }) {
  root = path.resolve(root);
  if ((await lstat(root)).isSymbolicLink()) throw problem(400, 'The workspace must not be a symbolic link.');
  const trashRoot = path.join(path.dirname(root), 'trash');
  let restoring;
  if (operation === 'restore') {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(filePath)) throw problem(400, 'Invalid trash item.');
    restoring = path.join(trashRoot, filePath);
    for (const directory of [trashRoot, restoring]) if ((await lstat(directory)).isSymbolicLink()) throw problem(400, 'Invalid trash item.');
    const metadata = JSON.parse(await readFile(path.join(restoring, 'entry.json'), 'utf8'));
    filePath = metadata.path;
    if ((await lstat(path.join(restoring, 'item'))).isSymbolicLink()) throw problem(400, 'Symbolic links cannot be restored.');
  }
  if (typeof filePath !== 'string' || path.isAbsolute(filePath) || filePath.includes('\0')) throw problem(400, 'Invalid project path.');
  const file = path.resolve(root, filePath);
  if (file !== root && !file.startsWith(root + path.sep)) throw problem(400, 'Stay inside the project.');
  let current = root;
  for (const part of path.relative(root, file).split(path.sep).filter(Boolean)) {
    if (part === '.git') throw problem(403, 'Git internals are not editable.');
    current = path.join(current, part);
    const stat = await lstat(current).catch(error => { if (error.code === 'ENOENT') return null; throw error; });
    if (stat?.isSymbolicLink()) throw problem(400, 'Symbolic links are not editable.');
  }
  if (['create-file', 'create-dir', 'delete', 'restore'].includes(operation) && file === root) throw problem(400, 'Choose an item inside the project.');
  if (restoring) {
    if (await lstat(file).catch(error => { if (error.code === 'ENOENT') return null; throw error; })) throw problem(409, 'An item with this name already exists.');
    await rename(path.join(restoring, 'item'), file);
    await unlink(path.join(restoring, 'entry.json')).catch(() => {});
    await rmdir(restoring).catch(() => {});
    const restored = await lstat(file);
    return { path: filePath, name: path.basename(file), type: restored.isDirectory() ? 'dir' : 'file', size: restored.isDirectory() ? 0 : restored.size };
  }
  if (operation === 'create-file' || operation === 'create-dir') {
    try {
      if (operation === 'create-dir') {
        await mkdir(file, { mode: 0o700 });
        return { path: filePath, name: path.basename(file), type: 'dir', size: 0 };
      }
      await writeFile(file, '', { mode: 0o600, flag: 'wx' });
    } catch (error) {
      if (error.code === 'EEXIST') throw problem(409, 'An item with this name already exists.');
      if (error.code === 'ENOENT') throw problem(404, 'The parent folder no longer exists.');
      throw error;
    }
  }
  if (operation === 'delete') {
    const stat = await lstat(file);
    await mkdir(trashRoot, { recursive: true, mode: 0o700 });
    if ((await lstat(trashRoot)).isSymbolicLink()) throw problem(400, 'Invalid workspace trash.');
    const trashId = randomUUID(), slot = path.join(trashRoot, trashId);
    await mkdir(slot, { mode: 0o700 });
    await writeFile(path.join(slot, 'entry.json'), JSON.stringify({ path: filePath }), { mode: 0o600, flag: 'wx' });
    try { await rename(file, path.join(slot, 'item')); }
    catch (error) { await unlink(path.join(slot, 'entry.json')).catch(() => {}); await rmdir(slot).catch(() => {}); throw error; }
    return { path: filePath, name: path.basename(file), type: stat.isDirectory() ? 'dir' : 'file', trashId };
  }
  // This process runs as the project UID, so concurrent path swaps cannot read service or other-project secrets.
  if (operation === 'tree') {
    const entries = await readdir(file, { withFileTypes: true });
    const visible = entries.filter(entry => entry.name !== '.git' && !entry.isSymbolicLink()).sort((a,b) =>
      Number(b.isDirectory()) - Number(a.isDirectory()) || a.name.localeCompare(b.name));
    return Promise.all(visible.slice(0, 2000).map(async entry => ({ name: entry.name,
      path: path.relative(root, path.join(file,entry.name)), type: entry.isDirectory() ? 'dir' : 'file',
      size: entry.isDirectory() ? 0 : (await lstat(path.join(file,entry.name))).size })));
  }
  if (operation === 'save') {
    if (typeof content !== 'string' || Buffer.byteLength(content) > 1000000) throw problem(400,'Invalid file content.');
    const previous = await readFile(file).catch(error => { if (error.code === 'ENOENT') return null; throw error; });
    if ((previous ? hash(previous) : '') !== etag) throw problem(409,'The file changed. Reopen it before saving your edits.');
    await mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
    const mode = (await lstat(file).catch(() => null))?.mode;
    const temporary = path.join(path.dirname(file),`.atlas-save-${randomUUID()}`);
    try { await writeFile(temporary,content,{mode:mode == null ? 0o600 : mode & 0o777,flag:'wx'}); await rename(temporary,file); }
    finally { await unlink(temporary).catch(()=>{}); }
  }
  const stat = await lstat(file);
  if (!stat.isFile()) throw problem(404,'File not found.');
  if (stat.size > 1000000) throw problem(413,'This file is too large to edit.');
  const bytes = await readFile(file);
  const text = new TextDecoder('utf-8',{fatal:true}).decode(bytes);
  if (text.includes('\0')) throw problem(415,'This file is not text.');
  return { path: filePath, name: path.basename(file), type: 'file', size: stat.size, content: text, etag: hash(bytes) };
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.umask(0o077);
  let input='';
  for await (const chunk of process.stdin) { input+=chunk; if(input.length>2000000)throw problem(413,'Input too large.'); }
  try { console.log(JSON.stringify({ok:true,data:await sessionFiles(JSON.parse(input))})); }
  catch(error) { console.log(JSON.stringify({ok:false,status:error.status ?? (error.code==='ENOENT'?404:error.code==='EACCES'?403:400),message:error.status?error.message:'Could not access this workspace file.'})); }
}
