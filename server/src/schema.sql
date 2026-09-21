CREATE TABLE IF NOT EXISTS users (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL,
 password_hash TEXT NOT NULL, theme TEXT NOT NULL DEFAULT 'system',
 reminders BOOLEAN NOT NULL DEFAULT true, default_priority TEXT NOT NULL DEFAULT 'MEDIUM',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS sessions (
 token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS boards (
 id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 name TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS tasks (
 id TEXT PRIMARY KEY, board_id TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
 title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
 status TEXT NOT NULL CHECK (status IN ('TODO','DOING','DONE')),
 priority TEXT NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH')),
 due_date DATE, checklist JSONB NOT NULL DEFAULT '[]',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- A unique task id makes rewards idempotent when cards are reopened and completed again.
CREATE TABLE IF NOT EXISTS completions (
 task_id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 completed_on DATE NOT NULL DEFAULT CURRENT_DATE, points INTEGER NOT NULL DEFAULT 10
);
CREATE INDEX IF NOT EXISTS boards_owner ON boards(user_id);
CREATE INDEX IF NOT EXISTS tasks_board ON tasks(board_id);
CREATE INDEX IF NOT EXISTS completions_owner ON completions(user_id);
