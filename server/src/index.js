import { openDatabase } from './database.js';
import { createApp } from './app.js';
const db = await openDatabase();
const server = createApp(db).listen(process.env.PORT || 3000, '0.0.0.0', () => console.info('TaskFlow API ready'));
for (const signal of ['SIGTERM','SIGINT']) process.on(signal, () => server.close(async () => { await db.close(); process.exit(0); }));
