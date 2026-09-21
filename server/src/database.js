import { readFile } from 'node:fs/promises';
import pg from 'pg';
import { PGlite } from '@electric-sql/pglite';

export async function openDatabase({ memory = false } = {}) {
  let client;
  if (process.env.DATABASE_URL && !memory) {
    client = new pg.Pool({ connectionString: process.env.DATABASE_URL,
      ssl: process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: true } : undefined });
  } else {
    if (process.env.NODE_ENV === 'production' && !memory) throw new Error('DATABASE_URL is required in production');
    client = new PGlite(memory ? undefined : './data');
  }
  const schema = await readFile(new URL('./schema.sql', import.meta.url), 'utf8');
  if (client.exec) await client.exec(schema); else await client.query(schema);
  return { query: (sql, args) => client.query(sql, args), close: () => client.close ? client.close() : client.end() };
}
