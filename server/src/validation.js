import { z } from 'zod';
const name = z.string().trim().min(1).max(80);
export const registerSchema = z.object({ name, email: z.email().max(254).transform(v => v.toLowerCase()), password: z.string().min(8).max(128) });
export const loginSchema = registerSchema.omit({ name: true });
export const boardSchema = z.object({ name });
export const settingsSchema = z.object({ name, theme: z.enum(['system','light','dark']), reminders: z.boolean(), defaultPriority: z.enum(['LOW','MEDIUM','HIGH']) });
export const taskSchema = z.object({
  boardId: z.string().uuid(), title: z.string().trim().min(1).max(120), description: z.string().max(4000),
  status: z.enum(['TODO','DOING','DONE']), priority: z.enum(['LOW','MEDIUM','HIGH']),
  dueDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(s => { const d = new Date(s); return !isNaN(d) && d.toISOString().slice(0,10) === s; }).nullable().default(null),
  checklist: z.array(z.object({ text: z.string().trim().min(1).max(160), done: z.boolean() })).max(30)
});

