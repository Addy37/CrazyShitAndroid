# ZeroChill feedback setup

The Android screen is already wired to a Supabase Edge Function. Users do not create accounts.
Each app installation gets a random ID. The function hashes that ID before storing it.

## One-time Supabase setup

1. Create a Supabase project.
2. Run `supabase/migrations/202609120001_create_feedback.sql` in the SQL editor.
3. Deploy `supabase/functions/feedback/index.ts` as an Edge Function named `feedback`.
4. Add a long random `FEEDBACK_ID_SALT` secret to the Edge Function.
5. Add these GitHub Actions repository secrets:
   - `FEEDBACK_ENDPOINT`: `https://YOUR_PROJECT.supabase.co/functions/v1/feedback`
   - `FEEDBACK_ANON_KEY`: the project's publishable or anon key
6. Pass those secrets into the Gradle build environment used for release builds.

The Supabase URL and publishable key are client configuration, not administrator credentials.
Never place the service-role key in the app or GitHub Actions build environment.

## Review and reply

Open Supabase, then **Table Editor > app_feedback**. You can filter by type, rating, version,
device, section, or status. Change `status` and `developer_reply` in the row. The user sees the
changes under **Feedback > My feedback** the next time they open it.

Allowed statuses are `submitted`, `reviewing`, `planned`, `in_progress`, `completed`, and
`declined`.
