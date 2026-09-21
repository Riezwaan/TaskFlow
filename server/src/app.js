import express from 'express';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { randomUUID } from 'node:crypto';
import { hashPassword, verifyPassword, tokenHash, newToken } from './security.js';
import { registerSchema, loginSchema, boardSchema, settingsSchema, taskSchema } from './validation.js';

const userView = u => ({ id: u.id, name: u.name, email: u.email, theme: u.theme, reminders: u.reminders, defaultPriority: u.default_priority });
const taskView = t => ({ id: t.id, boardId: t.board_id, title: t.title, description: t.description,
  status: t.status, priority: t.priority, dueDate: t.due_date ? String(t.due_date instanceof Date ? t.due_date.toISOString() : t.due_date).slice(0,10) : null,
  checklist: t.checklist, createdAt: t.created_at, updatedAt: t.updated_at });
const fail = (status, message) => Object.assign(new Error(message), { status });
export function createApp(db, { logging = true, limits = true } = {}) {
  const app = express();
  app.set('trust proxy', 1);
  app.use(helmet());
  app.use(express.json({ limit: '64kb' }));
  if (logging) app.use((req,res,next) => {
    const start = Date.now();
    res.on('finish', () => console.info(JSON.stringify({ method: req.method, path: req.path, status: res.statusCode, ms: Date.now()-start })));
    next(); // Never log credentials, request bodies or Authorization headers.
  });
  app.get('/health', async (_req,res) => { await db.query('SELECT 1'); res.json({ status: 'ok', service: 'TaskFlow API' }); });
  if (limits) app.use('/api/auth', rateLimit({ windowMs: 15 * 60 * 1000, limit: 30, message: { error: 'Too many attempts. Try again in 15 minutes.' } }));
  const issue = async user => {
    const token = newToken();
    await db.query('DELETE FROM sessions WHERE expires_at < now()');
    await db.query("INSERT INTO sessions VALUES ($1,$2,now() + interval '7 days')", [tokenHash(token),user.id]);
    return { token, user: userView(user) };
  };
  app.post('/api/auth/register', async (req,res) => {
    const v = registerSchema.parse(req.body);
    const id = randomUUID();
    const hash = await hashPassword(v.password);
    let result;
    try { result = await db.query('INSERT INTO users(id,name,email,password_hash) VALUES($1,$2,$3,$4) RETURNING *',[id,v.name,v.email,hash]); }
    catch (e) { if (e.code === '23505') throw fail(409,'An account with this email already exists.'); throw e; }
    res.status(201).json(await issue(result.rows[0]));
  });
  app.post('/api/auth/login', async (req,res) => {
    const v = loginSchema.parse(req.body);
    const u = (await db.query('SELECT * FROM users WHERE email=$1',[v.email])).rows[0];
    // Perform an equal-cost hash for unknown accounts to reduce account-enumeration timing.
    const valid = u ? await verifyPassword(v.password,u.password_hash) : (await hashPassword(v.password),false);
    if (!valid) throw fail(401,'Email or password is incorrect.');
    res.json(await issue(u));
  });
  app.use('/api', async (req,_res,next) => {
    const token = req.headers.authorization?.match(/^Bearer (.+)$/)?.[1];
    if (!token) throw fail(401,'Please sign in.');
    const u = (await db.query('SELECT u.* FROM users u JOIN sessions s ON s.user_id=u.id WHERE s.token_hash=$1 AND s.expires_at>now()',[tokenHash(token)])).rows[0];
    if (!u) throw fail(401,'Your session has expired. Please sign in again.');
    req.user = u; req.token = token; next();
  });
  app.post('/api/auth/logout', async (req,res) => { await db.query('DELETE FROM sessions WHERE token_hash=$1',[tokenHash(req.token)]); res.status(204).end(); });
  app.get('/api/me', (req,res) => res.json(userView(req.user)));
  app.put('/api/me', async (req,res) => {
    const v = settingsSchema.parse(req.body);
    const r = await db.query('UPDATE users SET name=$1,theme=$2,reminders=$3,default_priority=$4 WHERE id=$5 RETURNING *',[v.name,v.theme,v.reminders,v.defaultPriority,req.user.id]);
    res.json(userView(r.rows[0]));
  });
  app.get('/api/boards', async (req,res) => res.json((await db.query('SELECT id,name FROM boards WHERE user_id=$1 ORDER BY created_at,id',[req.user.id])).rows));
  app.post('/api/boards', async (req,res) => {
    const v = boardSchema.parse(req.body);
    res.status(201).json((await db.query('INSERT INTO boards(id,user_id,name) VALUES($1,$2,$3) RETURNING id,name',[randomUUID(),req.user.id,v.name])).rows[0]);
  });
  app.put('/api/boards/:id', async (req,res) => {
    const v = boardSchema.parse(req.body);
    const r = await db.query('UPDATE boards SET name=$1 WHERE id=$2 AND user_id=$3 RETURNING id,name',[v.name,req.params.id,req.user.id]);
    if (!r.rows.length) throw fail(404,'Board not found.'); res.json(r.rows[0]);
  });
  app.delete('/api/boards/:id', async (req,res) => {
    const r = await db.query('DELETE FROM boards WHERE id=$1 AND user_id=$2 RETURNING id',[req.params.id,req.user.id]);
    if (!r.rows.length) throw fail(404,'Board not found.'); res.status(204).end();
  });
  const ownBoard = async (id,userId) => {
    if (!(await db.query('SELECT id FROM boards WHERE id=$1 AND user_id=$2',[id,userId])).rows.length) throw fail(404,'Board not found.');
  };
  app.get('/api/boards/:id/tasks', async (req,res) => {
    await ownBoard(req.params.id,req.user.id);
    res.json((await db.query('SELECT * FROM tasks WHERE board_id=$1 ORDER BY created_at DESC,id',[req.params.id])).rows.map(taskView));
  });
  // A single SQL statement atomically saves the task and awards a first-completion reward.
  const saveTask = async (req,res,creating) => {
    const v = taskSchema.parse(req.body);
    await ownBoard(v.boardId,req.user.id);
    const id = creating ? randomUUID() : req.params.id;
    const args = [id,v.boardId,v.title,v.description,v.status,v.priority,v.dueDate,JSON.stringify(v.checklist),req.user.id];
    const mutation = creating
      ? 'INSERT INTO tasks(id,board_id,title,description,status,priority,due_date,checklist) VALUES($1,$2,$3,$4,$5,$6,$7,$8::jsonb) RETURNING *'
      : 'UPDATE tasks SET title=$3,description=$4,status=$5,priority=$6,due_date=$7,checklist=$8::jsonb,updated_at=now() WHERE id=$1 AND board_id=$2 RETURNING *';
    const r = await db.query(`WITH saved AS (${mutation}), reward AS (
      INSERT INTO completions(task_id,user_id) SELECT id,$9 FROM saved WHERE status='DONE' ON CONFLICT(task_id) DO NOTHING
    ) SELECT * FROM saved`,args);
    if (!r.rows.length) throw fail(404,'Task not found.');
    res.status(creating ? 201 : 200).json(taskView(r.rows[0]));
  };
  app.post('/api/tasks', (req,res) => saveTask(req,res,true));
  app.put('/api/tasks/:id', (req,res) => saveTask(req,res,false));
  app.delete('/api/tasks/:id', async (req,res) => {
    const r = await db.query('DELETE FROM tasks WHERE id=$1 AND board_id IN (SELECT id FROM boards WHERE user_id=$2) RETURNING id',[req.params.id,req.user.id]);
    if (!r.rows.length) throw fail(404,'Task not found.'); res.status(204).end();
  });
  app.get('/api/stats', async (req,res) => {
    const rows = (await db.query('SELECT completed_on,points FROM completions WHERE user_id=$1 ORDER BY completed_on DESC',[req.user.id])).rows;
    const days = new Set(rows.map(r => String(r.completed_on instanceof Date ? r.completed_on.toISOString() : r.completed_on).slice(0,10)));
    let day = new Date(); day.setUTCHours(0,0,0,0);
    if (!days.has(day.toISOString().slice(0,10))) day.setUTCDate(day.getUTCDate()-1);
    let streak = 0;
    while (days.has(day.toISOString().slice(0,10))) { streak++; day.setUTCDate(day.getUTCDate()-1); }
    const badges = [];
    if (rows.length) badges.push('First Task'); if (rows.length>=10) badges.push('10 Tasks Done'); if (streak>=7) badges.push('7-Day Streak');
    res.json({ completed: rows.length, points: rows.reduce((sum,r)=>sum+r.points,0), streak, badges });
  });
  app.use((_req,_res,next) => next(fail(404,'Endpoint not found.')));
  app.use((err,_req,res,_next) => {
    if (err.name === 'ZodError') return res.status(400).json({ error: 'Check the form fields and try again.', fields: err.issues.map(i=>i.path.join('.')) });
    const status = err.status || 500;
    if (status>=500 && logging) console.error('API operation failed',err.code || err.name);
    res.status(status).json({ error: status>=500 ? 'The server could not complete the request. Please try again.' : err.message });
  });
  return app;
}
