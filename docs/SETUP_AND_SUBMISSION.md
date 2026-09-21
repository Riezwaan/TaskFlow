# Finish the online submission

## GitHub

Create/verify an account at https://github.com/signup. Create an empty TaskFlow repository without a second README. In Android Studio's terminal, follow GitHub's **push an existing repository** instructions using your actual URL. Alternatively add this existing repository to GitHub Desktop and publish it. Authenticate in your browser; do not put tokens in source. Select visibility according to lecturer rules and grant access if private.

Check the online repository contains app, server, Gradle wrapper, README and the workflow. Exclude secrets, database files, caches and local.properties. Verify all three Actions jobs before submitting.

## Render

Create an account at https://dashboard.render.com/ and connect GitHub. Choose New → Blueprint and select TaskFlow. Review render.yaml, resource names, region and plans. It requests free resources; do not select paid upgrades unless intended.

API root: server. Build: `npm ci --omit=dev`. Start: `npm start`. The linked database provides DATABASE_URL and NODE_ENV=production disables local fallback. After deployment, open the HTTPS URL plus /health. Enter the HTTPS root in the app's Server connection, register and create data. Restart/refresh to confirm persistence. Local accounts are separate.

Render documents idle sleeping for free web services and a 30-day expiry for free PostgreSQL. Check current limits and plan the marking window accordingly. Warm up /health before recording. https://render.com/docs/free

## Database evidence

Use the provider's PostgreSQL connection instructions and hide credentials. Read-only queries for demonstration:

```sql
SELECT name, email, theme, reminders, default_priority FROM users;
SELECT email, length(password_hash) AS hash_length FROM users;
SELECT id, name, user_id FROM boards;
SELECT title, status, priority, due_date, checklist FROM tasks;
SELECT completed_on, points FROM completions;
SELECT count(*) FROM sessions WHERE expires_at > now();
```

Authentication is supplied by the custom hosted API with hashes in PostgreSQL; Part 2 does not use Firebase authentication. Use demonstration data and never expose tokens/connection strings.

## Phone/video

Enable USB debugging, authorise your phone and run from Android Studio (Android 8.0+), or install the debug APK. Configure the hosted URL and verify features. Record the phone screen with narration and include desktop footage of hosted API/database and passing GitHub Actions. Follow DEMO_SCRIPT.md. Upload unlisted, check permissions and paste the actual link into README. Add actual screenshots and commit/push final changes. Submit repository/video links, **not a ZIP**. A local APK alone does not fulfil online/video requirements.
