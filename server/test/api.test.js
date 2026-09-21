import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';
import { openDatabase } from '../src/database.js';
import { createApp } from '../src/app.js';
let db, app, alice, bob, board, task;
const auth = token => ({ Authorization: `Bearer ${token}` });
const draft = (changes={}) => ({ boardId: board.id, title:'Submit Part 2', description:'Record a demo', status:'TODO', priority:'HIGH', dueDate:'2026-10-01', checklist:[{text:'Test app',done:false}], ...changes });
before(async () => { db = await openDatabase({memory:true}); app = createApp(db,{logging:false,limits:false}); });
after(async () => { await db.close(); });
test('health checks the database',async()=>{ await request(app).get('/health').expect(200); });
test('invalid credentials and short passwords are rejected',async()=>{
 await request(app).post('/api/auth/register').send({name:'',email:'bad',password:'123'}).expect(400);
});
test('registration hashes passwords and stores only a token hash',async()=>{
 const r=await request(app).post('/api/auth/register').send({name:'Alice',email:'Alice@example.com',password:'ExamplePass123!'}).expect(201); alice=r.body.token;
 assert.equal(r.body.user.email,'alice@example.com'); assert.ok(!r.body.user.password_hash);
 const row=(await db.query('SELECT password_hash FROM users')).rows[0];
 assert.notEqual(row.password_hash,'ExamplePass123!'); assert.match(row.password_hash,/^[0-9a-f]{32}:[0-9a-f]{128}$/);
 const s=(await db.query('SELECT token_hash FROM sessions')).rows[0]; assert.notEqual(s.token_hash,alice);
});
test('duplicate registration and wrong password have safe errors',async()=>{
 await request(app).post('/api/auth/register').send({name:'Alice',email:'alice@example.com',password:'ExamplePass123!'}).expect(409);
 await request(app).post('/api/auth/login').send({email:'alice@example.com',password:'WrongPassword'}).expect(401);
});
test('login accepts normalized email; missing/invalid tokens fail',async()=>{
 await request(app).post('/api/auth/login').send({email:'ALICE@example.com',password:'ExamplePass123!'}).expect(200);
 await request(app).get('/api/boards').expect(401);
 await request(app).get('/api/boards').set(auth('invalid')).expect(401);
});
test('boards can be created, listed and renamed',async()=>{
 board=(await request(app).post('/api/boards').set(auth(alice)).send({name:'Study'}).expect(201)).body;
 assert.equal((await request(app).get('/api/boards').set(auth(alice))).body.length,1);
 await request(app).put(`/api/boards/${board.id}`).set(auth(alice)).send({name:'Semester'}).expect(200);
});
test('tasks preserve fields and checklist; invalid dates are rejected',async()=>{
 await request(app).post('/api/tasks').set(auth(alice)).send(draft({dueDate:'2026-02-30'})).expect(400);
 await request(app).post('/api/tasks').set(auth(alice)).send(draft({title:'  '})).expect(400);
 await request(app).post('/api/tasks').set(auth(alice)).send(draft({priority:'URGENT'})).expect(400);
 task=(await request(app).post('/api/tasks').set(auth(alice)).send(draft()).expect(201)).body;
 assert.equal(task.dueDate,'2026-10-01'); assert.equal(task.checklist[0].done,false);
 const rows=(await request(app).get(`/api/boards/${board.id}/tasks`).set(auth(alice))).body;
 assert.equal(rows[0].title,'Submit Part 2');
});
test('another user cannot list, alter or delete private records',async()=>{
 bob=(await request(app).post('/api/auth/register').send({name:'Bob',email:'bob@example.com',password:'ExamplePass123!'})).body.token;
 assert.deepEqual((await request(app).get('/api/boards').set(auth(bob))).body,[]);
 await request(app).get(`/api/boards/${board.id}/tasks`).set(auth(bob)).expect(404);
 await request(app).put(`/api/boards/${board.id}`).set(auth(bob)).send({name:'Stolen'}).expect(404);
 await request(app).delete(`/api/boards/${board.id}`).set(auth(bob)).expect(404);
 await request(app).post('/api/tasks').set(auth(bob)).send(draft()).expect(404);
 await request(app).put(`/api/tasks/${task.id}`).set(auth(bob)).send(draft()).expect(404);
 await request(app).delete(`/api/tasks/${task.id}`).set(auth(bob)).expect(404);
});
test('completion awards exactly once despite repeated updates',async()=>{
 for(const status of ['DOING','DONE','DONE','TODO','DONE']) await request(app).put(`/api/tasks/${task.id}`).set(auth(alice)).send(draft({status})).expect(200);
 const s=(await request(app).get('/api/stats').set(auth(alice))).body;
 assert.equal(s.points,10);assert.equal(s.completed,1);assert.equal(s.streak,1);assert.deepEqual(s.badges,['First Task']);
});
test('settings persist and reject invalid values',async()=>{
 await request(app).put('/api/me').set(auth(alice)).send({name:'Alice Student',theme:'dark',reminders:false,defaultPriority:'LOW'}).expect(200);
 const u=(await request(app).get('/api/me').set(auth(alice))).body;
 assert.equal(u.theme,'dark');assert.equal(u.reminders,false);assert.equal(u.defaultPriority,'LOW');
 await request(app).put('/api/me').set(auth(alice)).send({name:'Alice',theme:'rainbow'}).expect(400);
});
test('task and board deletion cascade while earned rewards remain',async()=>{
 await request(app).delete(`/api/tasks/${task.id}`).set(auth(alice)).expect(204);
 assert.equal((await request(app).get('/api/stats').set(auth(alice))).body.points,10);
 await request(app).post('/api/tasks').set(auth(alice)).send(draft()).expect(201);
 await request(app).delete(`/api/boards/${board.id}`).set(auth(alice)).expect(204);
 assert.equal((await db.query('SELECT * FROM tasks')).rows.length,0);
});
test('expired sessions and signed-out tokens cannot be reused',async()=>{
 await db.query("UPDATE sessions SET expires_at=now()-interval '1 hour' WHERE user_id=(SELECT id FROM users WHERE email='bob@example.com')");
 await request(app).get('/api/me').set(auth(bob)).expect(401);
 await request(app).post('/api/auth/logout').set(auth(alice)).expect(204);
 await request(app).get('/api/me').set(auth(alice)).expect(401);
});
