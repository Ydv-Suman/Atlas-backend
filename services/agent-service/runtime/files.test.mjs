import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,readFile,symlink,rm,chmod,lstat} from 'node:fs/promises';
import os from 'node:os';import path from 'node:path';
import {sessionFiles} from './files.mjs';
test('project-user file helper preserves drafts and prevents stale or out-of-project saves',async()=>{
 const directory=await mkdtemp(path.join(os.tmpdir(),'atlas-files-'));const root=path.join(directory,'repo');await mkdir(root);
 try {
  await writeFile(path.join(root,'hello.py'),'one');
  await chmod(path.join(root,'hello.py'),0o755);
  const original=await sessionFiles({root,operation:'read',filePath:'hello.py'});
  await sessionFiles({root,operation:'save',filePath:'hello.py',content:'two',etag:original.etag});
  await assert.rejects(()=>sessionFiles({root,operation:'save',filePath:'hello.py',content:'stale',etag:original.etag}),error=>error.status===409);
  assert.equal(await readFile(path.join(root,'hello.py'),'utf8'),'two');
  assert.equal((await lstat(path.join(root,'hello.py'))).mode & 0o111,0o111);
  await assert.rejects(()=>sessionFiles({root,operation:'read',filePath:'../outside'}),error=>error.status===400);
  await symlink(directory,path.join(root,'link'));
  await assert.rejects(()=>sessionFiles({root,operation:'read',filePath:'link/outside'}),error=>error.status===400);
  await assert.rejects(()=>sessionFiles({root,operation:'read',filePath:'.git/config'}),error=>error.status===403);
 }finally{await rm(directory,{recursive:true,force:true});}
});

test('manual creation is exclusive and deleting a nonempty folder can be undone',async()=>{
 const directory=await mkdtemp(path.join(os.tmpdir(),'atlas-entries-'));const root=path.join(directory,'repo');await mkdir(root);
 try {
  const folder=await sessionFiles({root,operation:'create-dir',filePath:'manual'});
  assert.equal(folder.type,'dir');
  const file=await sessionFiles({root,operation:'create-file',filePath:'manual/hello.py'});
  assert.equal(file.content,'');assert.ok(file.etag);
  await writeFile(path.join(root,'manual/hello.py'),'print("kept")');
  await assert.rejects(()=>sessionFiles({root,operation:'create-file',filePath:'manual/hello.py'}),error=>error.status===409);
  await assert.rejects(()=>sessionFiles({root,operation:'create-dir',filePath:'manual'}),error=>error.status===409);
  const deleted=await sessionFiles({root,operation:'delete',filePath:'manual'});
  await assert.rejects(()=>lstat(path.join(root,'manual')),error=>error.code==='ENOENT');
  assert.equal(await readFile(path.join(directory,'trash',deleted.trashId,'item/hello.py'),'utf8'),'print("kept")');
  await sessionFiles({root,operation:'restore',filePath:deleted.trashId});
  assert.equal(await readFile(path.join(root,'manual/hello.py'),'utf8'),'print("kept")');
  await assert.rejects(()=>sessionFiles({root,operation:'delete',filePath:''}),error=>error.status===400);
  await assert.rejects(()=>sessionFiles({root,operation:'create-file',filePath:'.git/config'}),error=>error.status===403);
 }finally{await rm(directory,{recursive:true,force:true});}
});

test('undo never overwrites a new item with the same name',async()=>{
 const directory=await mkdtemp(path.join(os.tmpdir(),'atlas-undo-'));const root=path.join(directory,'repo');await mkdir(root);
 try {
  await writeFile(path.join(root,'file.py'),'original');
  const deleted=await sessionFiles({root,operation:'delete',filePath:'file.py'});
  await writeFile(path.join(root,'file.py'),'replacement');
  await assert.rejects(()=>sessionFiles({root,operation:'restore',filePath:deleted.trashId}),error=>error.status===409);
  assert.equal(await readFile(path.join(root,'file.py'),'utf8'),'replacement');
  assert.equal(await readFile(path.join(directory,'trash',deleted.trashId,'item'),'utf8'),'original');
 }finally{await rm(directory,{recursive:true,force:true});}
});

test('undo restores binary files without trying to open them as text',async()=>{
 const directory=await mkdtemp(path.join(os.tmpdir(),'atlas-binary-'));const root=path.join(directory,'repo');await mkdir(root);
 try {
  const bytes=Buffer.from([0,255,1]);await writeFile(path.join(root,'image.bin'),bytes);
  const deleted=await sessionFiles({root,operation:'delete',filePath:'image.bin'});
  const restored=await sessionFiles({root,operation:'restore',filePath:deleted.trashId});
  assert.equal(restored.type,'file');assert.equal(restored.size,3);
  assert.deepEqual(await readFile(path.join(root,'image.bin')),bytes);
 }finally{await rm(directory,{recursive:true,force:true});}
});
