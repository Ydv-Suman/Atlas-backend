import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const instructions = `You are Atlas, a coding assistant in a persistent project workspace.
Answer questions directly. For requested edits, inspect the real files, edit them, and verify with appropriate commands.
Your working directory contains the current unpublished changes from earlier turns. Preserve unrelated work.
Never push, publish, discard changes, or commit unless the user explicitly requests it.
Stay inside the project directory. Do not read credentials or other projects.
For every file creation or edit, inspect the resulting content and verify that it matches the request before reporting completion. Empty placeholders do not satisfy requests for code. If a tool fails or permission is denied, say so.
Ask clarifying questions when needed. Explain actual results; never invent command output.`;

export function safePath(root, target) {
  const resolved = path.resolve(root, target);
  return resolved === root || resolved.startsWith(root + path.sep);
}

export function selectCodexModel(available, catalog = []) {
  const defaultModel = catalog.find(model => model.isDefault && available.includes(model.model ?? model.id));
  if (defaultModel) return defaultModel.model ?? defaultModel.id;
  for (const model of ['gpt-5.3-codex', 'gpt-5.2-codex', 'gpt-5.1-codex-mini', 'gpt-5.4-mini']) if (available.includes(model)) return model;
  const compatible = catalog.find(model => available.includes(model.model ?? model.id));
  if (compatible) return compatible.model ?? compatible.id;
  for (const model of ['gpt-5.4', 'gpt-5.2', 'gpt-5-mini', 'gpt-4.1', 'gpt-4o']) if (available.includes(model)) return model;
  throw new Error('This API key has no supported coding model. Check model access in your OpenAI account.');
}

export async function runCodex(config, emit, controls) {
  const child = spawn(config.codexCommand ?? path.join(path.dirname(fileURLToPath(import.meta.url)), 'node_modules/.bin/codex'),
    ['app-server', '--listen', 'stdio://'], { cwd: config.cwd, detached: process.platform !== 'win32', env: { ...process.env, CODEX_HOME: config.stateDir } });
  const kill = () => { try { if (process.platform !== 'win32') process.kill(-child.pid, 'SIGTERM'); else child.kill('SIGTERM'); } catch {} };
  let next = 0;
  const pending = new Map();
  let thread, turn, completed = false;
  const messages = new Map();
  const toolItems = new Map();
  const send = value => child.stdin.write(JSON.stringify(value) + '\n');
  const rpc = (method, params) => new Promise((resolve, reject) => {
    const id = ++next; pending.set(id, { resolve, reject }); send({ id, method, params });
  });
  let finish, fail;
  const done = new Promise((resolve, reject) => { finish = resolve; fail = reject; });
  done.catch(() => {});
  const requests = new Map();
  const stop = controls(control => {
    if (control.type === 'cancel') {
      if (thread && turn) rpc('turn/interrupt', { threadId: thread, turnId: turn }).catch(() => {});
      kill();
      fail(new Error('Stopped by you.'));
    } else if (control.type === 'answer') {
      const request = requests.get(control.requestId);
      if (!request) return;
      requests.delete(control.requestId);
      if (request.method === 'item/tool/requestUserInput') {
        send({ id: request.id, result: { answers: control.answers ?? {} } });
      } else send({ id: request.id, result: { decision: control.approved ? 'accept' : 'decline' } });
      emit({ type: 'request.resolved', requestId: control.requestId });
    }
  });
  child.stderr.on('data', () => {}); // The runtime may log credentials; only protocol errors are shown.
  child.on('error', fail);
  child.on('exit', code => { const error = new Error(`Agent runtime exited before finishing (${code ?? 'stopped'}).`); if (!completed) fail(error); for (const request of pending.values()) request.reject(error); pending.clear(); });
  createInterface({ input: child.stdout }).on('line', line => {
    try {
      const event = JSON.parse(line);
      if (event.id != null && !event.method) {
        const request = pending.get(event.id); pending.delete(event.id);
        if (event.error) request?.reject(new Error(event.error.message)); else request?.resolve(event.result);
        return;
      }
      const p = event.params ?? {};
      if (event.id != null) {
        const requestId = String(event.id);
        if (event.method === 'item/commandExecution/requestApproval' || event.method === 'item/fileChange/requestApproval') {
          const item = toolItems.get(p.itemId);
          const command = p.command ?? item?.command;
          const files = item?.changes?.map(change => `${change.path}\n${change.diff ?? ''}`).join('\n');
          if (!command && !files) {
            send({ id: event.id, result: { decision: 'decline' } });
            emit({ type: 'notice', text: 'A tool request could not be described for review and was declined.' });
            return;
          }
          requests.set(requestId, event);
          emit({ type: 'approval', requestId, text: command ?? files, detail: p.reason ?? '', cwd: p.cwd });
        } else if (event.method === 'item/tool/requestUserInput') {
          requests.set(requestId, event);
          emit({ type: 'question', requestId, questions: p.questions });
        } else { // Unhandled permission grants are declined rather than silently widened.
          send({ id: event.id, error: { code: -32601, message: 'This permission is not supported by Atlas.' } });
        }
        return;
      }
      if (event.method === 'turn/started') turn = p.turn?.id;
      if (event.method === 'item/agentMessage/delta') emit({ type: 'text.delta', itemId: p.itemId, text: p.delta });
      if (event.method === 'item/commandExecution/outputDelta') emit({ type: 'tool.delta', itemId: p.itemId, text: p.delta });
      if (event.method === 'item/started' || event.method === 'item/completed') {
        const item = p.item ?? {};
        toolItems.set(item.id, item);
        if (item.type === 'agentMessage' && event.method === 'item/completed') {
          messages.set(item.id, item.text);
          emit({ type: 'text.complete', itemId: item.id, text: item.text });
        }
        if (item.type === 'commandExecution') emit({ type: 'tool', itemId: item.id, text: item.command,
          output: item.aggregatedOutput ?? '', status: item.status, exitCode: item.exitCode });
        if (item.type === 'fileChange') emit({ type: 'files', itemId: item.id, text: item.changes?.map(change => change.path).join('\n') ?? '', status: item.status });
      }
      if (event.method === 'error' && !p.willRetry) emit({ type: 'notice', text: p.error?.message ?? 'The agent encountered an error.' });
      if (event.method === 'turn/completed') {
        completed = true;
        if (p.turn?.status === 'completed') finish([...messages.values()].join('\n\n'));
        else fail(new Error(p.turn?.error?.message ?? (p.turn?.status === 'interrupted' ? 'Stopped by you.' : 'The agent could not finish this turn.')));
      }
    } catch (error) { fail(error); }
  });
  try {
    await rpc('initialize', { clientInfo: { name: 'atlas_mobile', version: '1.0.0' }, capabilities: { experimentalApi: true } });
    send({ method: 'initialized', params: {} });
    await rpc('account/login/start', { type: 'apiKey', apiKey: config.apiKey });
    const threadFile = path.join(config.stateDir, 'atlas-thread-id');
    const saved = await readFile(threadFile, 'utf8').catch(() => null);
    let model = config.model;
    if (!model) {
      const catalog = await rpc('model/list', { includeHidden: false });
      const response = await fetch('https://api.openai.com/v1/models', { headers: { Authorization: `Bearer ${config.apiKey}` } });
      if (response.ok) model = selectCodexModel((await response.json()).data.map(item => item.id), catalog.data ?? []);
      else if (response.status === 403) model = (catalog.data ?? []).find(item => item.isDefault)?.model;
      if (!model) throw new Error('Could not discover an available coding model. Check the OpenAI API key and model permissions.');
    }
    emit({ type: 'session', model });
    const params = { cwd: config.cwd, approvalPolicy: 'on-request', sandbox: 'workspace-write',
      model, developerInstructions: instructions };
    const result = saved ? await rpc('thread/resume', { ...params, threadId: saved.trim() }) : await rpc('thread/start', params);
    thread = result.thread.id;
    await writeFile(threadFile, thread, { mode: 0o600 });
    const started = await rpc('turn/start', { threadId: thread, input: [{ type: 'text', text: config.prompt }] });
    turn = started.turn.id;
    const response = await done;
    emit({ type: 'result', text: response, sessionId: thread });
  } finally {
    completed = true;
    stop();
    child.stdin.end();
    kill();
    for (const request of pending.values()) request.reject(new Error('Agent session closed.'));
  }
}

export async function runClaude(config, emit, controls) {
  const { query } = await import('@anthropic-ai/claude-agent-sdk');
  const requests = new Map();
  const abort = new AbortController();
  const stateFile = path.join(config.stateDir, 'atlas-claude-session');
  const resume = await readFile(stateFile, 'utf8').catch(() => undefined);
  let sequence = 0;
  const stop = controls(control => {
    if (control.type === 'cancel') abort.abort();
    if (control.type === 'answer') {
      requests.get(control.requestId)?.(control); requests.delete(control.requestId);
      emit({ type: 'request.resolved', requestId: control.requestId });
    }
  });
  let reply = '', sessionId, streamingId = 'claude';
  const stream = query({ prompt: config.prompt, options: {
    cwd: config.cwd, resume, env: { ...process.env, ANTHROPIC_API_KEY: config.apiKey },
    abortController: abort, includePartialMessages: true, maxTurns: 40,
    settingSources: [], systemPrompt: { type: 'preset', preset: 'claude_code', append: instructions },
    permissionMode: 'default', tools: ['Read', 'Glob', 'Grep', 'Edit', 'Write', 'Bash', 'AskUserQuestion'],
    canUseTool: async (tool, input) => {
      const file = input.file_path ?? input.path;
      if (file && !safePath(config.cwd, file)) return { behavior: 'deny', message: 'Stay inside the project workspace.' };
      if (['Read', 'Glob', 'Grep', 'Edit', 'Write'].includes(tool)) return { behavior: 'allow', updatedInput: input };
      const requestId = `claude-${++sequence}`;
      emit(tool === 'AskUserQuestion' ? { type: 'question', requestId, questions: input.questions }
        : { type: 'approval', requestId, text: input.command ?? tool, detail: input.description ?? '' });
      const answer = await new Promise(resolve => {
        requests.set(requestId, resolve);
        abort.signal.addEventListener('abort', () => resolve({ approved: false }), { once: true });
      });
      if (tool === 'AskUserQuestion') {
        const answers = {};
        for (const question of input.questions ?? []) answers[question.question] = answer.answers?.[question.question]?.answers?.join(', ') ?? '';
        return { behavior: 'allow', updatedInput: { ...input, answers } };
      }
      return answer.approved ? { behavior: 'allow', updatedInput: input } : { behavior: 'deny', message: 'Declined by the user.' };
    },
  } });
  try {
    for await (const message of stream) {
      sessionId = message.session_id ?? sessionId;
      if (sessionId) await writeFile(stateFile, sessionId, { mode: 0o600 });
      if (message.type === 'stream_event') {
        const e = message.event;
        if (e.type === 'message_start') streamingId = e.message.id;
        if (e.type === 'content_block_delta' && e.delta?.type === 'text_delta')
          emit({ type: 'text.delta', itemId: streamingId, text: e.delta.text });
      }
      if (message.type === 'assistant') {
        for (const block of message.message.content) {
          if (block.type === 'text') { reply = block.text; emit({ type: 'text.complete', itemId: message.message.id, text: block.text }); }
          if (block.type === 'tool_use') emit({ type: 'tool', itemId: block.id, text: block.input?.command ?? `${block.name} ${block.input?.file_path ?? block.input?.pattern ?? ''}`, status: 'inProgress' });
        }
      }
      if (message.type === 'user') for (const block of message.message?.content ?? []) {
        if (block.type === 'tool_result') emit({ type: 'tool', itemId: block.tool_use_id, text: '', output: typeof block.content === 'string' ? block.content : JSON.stringify(block.content), status: block.is_error ? 'failed' : 'completed' });
      }
      if (message.type === 'result') {
        if (message.is_error || message.subtype !== 'success') throw new Error(message.errors?.join('\n') ?? 'The agent could not finish this turn.');
        reply = message.result ?? reply;
      }
    }
    if (abort.signal.aborted) throw new Error('Stopped by you.');
    emit({ type: 'result', text: reply, sessionId });
  } finally { stop(); stream.close(); }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.umask(0o077);
  const lines = createInterface({ input: process.stdin });
  let started = false;
  const listeners = new Set();
  lines.on('line', async line => {
    try {
      const config = JSON.parse(line);
      if (started) { for (const listener of listeners) listener(config); return; }
      started = true;
      await mkdir(config.stateDir, { recursive: true, mode: 0o700 });
      const emit = event => process.stdout.write(JSON.stringify(event).replaceAll(config.apiKey, '[redacted]') + '\n');
      const controls = listener => { listeners.add(listener); return () => listeners.delete(listener); };
      try {
        if (config.provider === 'openai') await runCodex(config, emit, controls);
        else if (config.provider === 'claude') await runClaude(config, emit, controls);
        else throw new Error('This provider does not have an interactive agent runtime. Select OpenAI or Claude.');
      } catch (error) { emit({ type: 'error', text: error.message }); process.exitCode = 1; }
      lines.close(); process.stdin.destroy();
    } catch { process.stdout.write(JSON.stringify({ type: 'error', text: 'Invalid agent session input.' }) + '\n'); process.exit(1); }
  });
}
