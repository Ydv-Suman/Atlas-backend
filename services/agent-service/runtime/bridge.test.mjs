import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, chmod, mkdir, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { runCodex, safePath, selectCodexModel } from './bridge.mjs';

const fake = `#!/usr/bin/env node
const rl=require('node:readline').createInterface({input:process.stdin});
const send=x=>process.stdout.write(JSON.stringify(x)+'\\n');
rl.on('line',line=>{const m=JSON.parse(line);
 if(m.method==='initialized')return;
 if(m.method==='thread/start'||m.method==='thread/resume'){send({id:m.id,result:{thread:{id:m.params.threadId||'thread-1'}}});return;}
 if(m.method==='turn/start'){
  send({id:m.id,result:{turn:{id:'turn-1'}}});
  send({method:'item/started',params:{item:{id:'cmd',type:'commandExecution',command:'python3 hello.py',status:'inProgress'}}});
  send({id:900,method:'item/commandExecution/requestApproval',params:{itemId:'cmd',reason:'command failed; retry without sandbox?'}});return;}
 if(m.id===900){
  if(m.result.decision!=='accept') {send({method:'turn/completed',params:{turn:{status:'failed',error:{message:'Command declined'}}}});return;}
  send({method:'item/commandExecution/outputDelta',params:{itemId:'cmd',delta:'hello world\\n'}});
  send({method:'item/completed',params:{item:{id:'cmd',type:'commandExecution',command:'python3 hello.py',status:'completed',aggregatedOutput:'hello world\\n',exitCode:0}}});
  send({method:'item/agentMessage/delta',params:{itemId:'reply',delta:'It '}});
  send({method:'item/agentMessage/delta',params:{itemId:'reply',delta:'worked.'}});
  send({method:'item/completed',params:{item:{id:'reply',type:'agentMessage',text:'It worked.'}}});
  send({method:'turn/completed',params:{turn:{status:'completed'}}});return;}
 if(m.id!=null)send({id:m.id,result:{}});
});`;

test('streams actual deltas and command output, waits for approval, and resumes the saved thread', { timeout: 10000 }, async () => {
 const root=await mkdtemp(path.join(os.tmpdir(),'atlas-runtime-test-'));
 try {
  const cwd=path.join(root,'repo'), stateDir=path.join(root,'state');
  await mkdir(cwd);await mkdir(stateDir);
  const executable=path.join(root,'fake-codex.cjs');await writeFile(executable,fake);await chmod(executable,0o755);
  const events=[];let control;
  const emit=event=>{events.push(event);if(event.type==='approval')setTimeout(()=>control({type:'answer',requestId:event.requestId,approved:true}),5);};
  const config={cwd,stateDir,provider:'openai',model:'fake-model',apiKey:'test-key',prompt:'Run hello',codexCommand:executable};
  await runCodex(config,emit,listener=>{control=listener;return()=>{};});
  assert.equal(events.filter(e=>e.type==='text.delta').map(e=>e.text).join(''),'It worked.');
  assert.equal(events.find(e=>e.type==='approval').text,'python3 hello.py');
  assert.equal(events.find(e=>e.type==='tool.delta').text,'hello world\n');
  assert.equal(await readFile(path.join(stateDir,'atlas-thread-id'),'utf8'),'thread-1');
  assert.equal(events.at(-1).sessionId,'thread-1');
  await runCodex(config,emit,listener=>{control=listener;return()=>{};});
  assert.equal(events.at(-1).text,'It worked.');
 } finally { await rm(root,{recursive:true,force:true}); }
});

test('file access checks stay inside the workspace',()=>{
 assert.equal(safePath('/project','src/file.py'),true);
 assert.equal(safePath('/project','../credentials'),false);
 assert.equal(safePath('/project','/project-other/file'),false);
});

test('model selection uses this account’s available models instead of an unavailable hardcoded model',()=>{
 assert.equal(selectCodexModel(['gpt-4o','gpt-5.4-mini']), 'gpt-5.4-mini');
 assert.equal(selectCodexModel(['gpt-5.4-mini','gpt-6.2-sol'], [{model:'gpt-6.2-sol',isDefault:true}]), 'gpt-6.2-sol');
 assert.throws(()=>selectCodexModel(['tts-1']));
});
