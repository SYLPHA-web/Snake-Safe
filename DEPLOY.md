# Going live: Supabase + backend host + Vercel

## 1. How the pieces fit (read this first)

| Piece | What it does | Where it runs |
|---|---|---|
| **Frontend** (website + installable app) | The screens | **Vercel** (you already deployed it) |
| **Backend** (Spring Boot, Java 25) | Hospitals, alerts, login, chat, identify | **A host that can run a Java/Docker app** (see below) |
| **Database + photo storage** | Stores all data | **Supabase** |

**Supabase and Vercel cannot run the Java backend.** Supabase is a database; Vercel only hosts static files and short serverless functions. So the backend needs one small always-on host. You said not Render, so here are the options (free tiers change, check current pricing):

| Host | Notes |
|---|---|
| **Google Cloud Run** | Runs the Dockerfile as is. Set *minimum instances = 1* so the first emergency request is never slow. Has a free allowance, a card is needed |
| **Render** | Works: see section 4b. The free plan sleeps when unused, so use the cheapest paid plan for anything people rely on |
| **Fly.io**, **Railway**, **Koyeb** | Deploy straight from the GitHub repo using the `Dockerfile` |
| **Hugging Face Spaces (Docker)** | Free, runs the Dockerfile, but sleeps when unused |
| **A small VPS** (Oracle Cloud free tier, Hetzner, ...) | Install Docker and run the image yourself |

Whatever you pick, it needs: build from the `Dockerfile` in the repo root, expose the port from the `PORT` variable, and the environment variables in section 3. I could not run Docker on this computer, so the `Dockerfile` has not been test-built here: if the build fails, send me the log.

## 2. Supabase setup (about 10 minutes)

1. **Create a project** at supabase.com. Pick a region close to India (for example Mumbai). Choose a strong **database password** and save it.
2. **Get the connection details.** Click **Connect** at the top of the dashboard, choose **Session pooler** (not Direct, not Transaction). You need:
   - host, like `aws-0-ap-south-1.pooler.supabase.com`
   - port `5432`
   - user, like `postgres.<project-ref>`
   - your database password

   Put them in one JDBC address:
   ```
   DB_URL=jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require
   DB_USER=postgres.<project-ref>
   DB_PASSWORD=<your database password>
   ```
   Use the **session pooler**: the direct connection only works over IPv6, and many hosts are IPv4 only.
3. **Photo storage.** Storage, then **New bucket**, name it exactly `snake-photos`, keep it **private**.
4. **Storage keys.** Project Settings, then API:
   - Project URL becomes `SUPABASE_URL`
   - the **service_role** key becomes `SUPABASE_SERVICE_KEY`. This key is a secret with full access: it goes only in the backend host's settings, never in Vercel, JavaScript or Git.
5. **Start the backend once** (section 4). It creates all the tables by itself.
6. **Lock the tables.** Supabase exposes tables to the internet through its own public key unless row level security is on. SQL Editor, paste and run:
   ```sql
   do $$
   declare r record;
   begin
     for r in select tablename from pg_tables where schemaname = 'public' loop
       execute format('alter table public.%I enable row level security', r.tablename);
     end loop;
   end $$;
   ```
   The backend is not affected (it connects as the database owner). Run this again after the first start, and whenever new tables appear.

## 3. Backend environment variables

Set these on the backend host (not on Vercel).

| Variable | Value | Needed? |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `supabase` (already the default in the Dockerfile) | Yes |
| `DB_URL` | the JDBC address above | Yes |
| `DB_USER` | `postgres.<project-ref>` | Yes |
| `DB_PASSWORD` | your database password | Yes |
| `JWT_SECRET` | 32 or more random characters (`openssl rand -hex 32`) | **Yes**: without it the app refuses to start |
| `ADMIN_USERNAME` | your admin login name | **Yes**, so you can sign in to the admin page |
| `ADMIN_PASSWORD` | at least 10 characters | **Yes** |
| `TRUST_PROXY` | `true` (already in the Dockerfile) | Yes behind Vercel / a load balancer. Without it every visitor shares one IP and the rate limits block people |
| `SUPABASE_URL` | `https://<project-ref>.supabase.co` | For photo uploads |
| `SUPABASE_SERVICE_KEY` | the service_role key | For photo uploads |
| `SEED_SAMPLE_DATA` | `true` for a showcase (8 sample hospitals, 6 sample catchers, demo staff); leave unset for real data only | Optional |
| `SEED_STAFF_PASSWORD` | password for the demo staff accounts (default `staff123`, **change it** when seeding on a public site) | Optional |
| `CHAT_MODE` | `AI` for the Claude chatbot (default `STUB` = fixed answers) | Optional |
| `IDENTIFY_MODE` | `AI` for Claude photo identification (default `STUB` = sample guesses) | Optional |
| `AI_API_KEY` | your Anthropic API key (needed for the two above) | Only for AI |
| `AI_MODEL` | default `claude-opus-5-5`; a cheaper model is fine for chat | Optional |
| `OTP_MODE` | leave `DEMO` (see the warning below) | Optional |
| `PORT` | set by the host | Automatic |
| `DB_POOL_SIZE` | default 5 | Optional |

**Phone login warning.** With `OTP_MODE=DEMO` the code is always `123456`, so anyone can sign in as any phone number and see that person's saved family details. That is fine for a showcase. Real SMS needs an SMS provider (for example MSG91 or Twilio) and a small `SmsSender` class: ask me and I will add it. Emergency help never needs a login.

## 4. Deploy the backend

1. Push the repo to GitHub (done: `SYLPHA-web/Snake-Safe`).
2. On your chosen host, create a service from the repo using the **Dockerfile**, add the variables from section 3, and deploy.
3. Open `https://<your-backend-host>/api/config`. You should see JSON with `"name":"SnakeSafe"`. In the host's logs look for the line `Chat mode: ... | AI key present: ...` that shows how it started.
4. Run the SQL from step 2.6 in Supabase.
5. Set the host's health check to `/api/config`.

Example for Google Cloud Run (after `gcloud auth login` and choosing a project):
```bash
gcloud run deploy snakesafe --source . --region asia-south1 --allow-unauthenticated \
  --min-instances 1 --memory 1Gi \
  --set-env-vars "DB_URL=...,DB_USER=...,JWT_SECRET=...,ADMIN_USERNAME=...,SUPABASE_URL=..." \
  --set-secrets "DB_PASSWORD=db-password:latest,ADMIN_PASSWORD=admin-password:latest,SUPABASE_SERVICE_KEY=supabase-key:latest"
```
(Create the secrets first in Secret Manager. Keep passwords and keys out of plain `--set-env-vars` if you can.)


### 4b. Render (if you choose it)

1. Dashboard, **New, Blueprint**, pick the repo. Render reads `render.yaml` (Docker, Singapore region, health check `/api/config`) and asks you for the secret values from section 3. `JWT_SECRET` is generated for you.
2. The plan in `render.yaml` is `starter` (always on). The `free` plan sleeps after about 15 minutes idle and takes about a minute to wake: only for a demo.
3. Entry plans have 512 MB of memory. The Dockerfile already uses small Java settings. If the service restarts with "out of memory" in the logs, move up one plan.
4. Do not set `PORT`: Render sets it and the app reads it.
5. When it is live, open `https://<name>.onrender.com/api/config`, then put `<name>.onrender.com` (no `https://`) in `vercel.json`.

## 5. Connect Vercel

1. In `vercel.json` replace both `YOUR-BACKEND-HOST` with your backend's host name only, for example `snakesafe-abc123-el.a.run.app` (no `https://`).
2. Commit and push. Vercel redeploys. **Vercel needs no environment variables.**
3. Open your Vercel site. If you see "The server is not reachable", the host name in `vercel.json` is wrong or the backend is not running.

Note: WebSockets do not pass through Vercel, so live updates arrive by polling (patients about 3 seconds, staff about 5). That is built in and needs nothing from you.

## 6. First run checklist

1. Open `https://<your-vercel-site>/#/admin` and sign in with `ADMIN_USERNAME` / `ADMIN_PASSWORD`.
2. **Hospitals**: add real hospitals one by one, or **Import CSV** (template: `docs/hospitals-template.csv`; columns `name, district, address, phone, latitude, longitude, vials`). I did not invent hospital phone numbers or stock: use real, verified details.
3. **Staff**: create one account per hospital (username, password of at least 8 characters, hospital).
4. Each hospital's staff sign in at `#/staff` and press **Update stock now**. Stock is only "Verified" for 2 hours after a staff update; that is the point of the app.
5. Test with a second phone: raise an alert, accept it on the staff screen.

## 7. Snake data

- **30 real species** (scientific names, venom status, habitat, range) are loaded automatically on first start. Kannada names are common usage or transliterations: have a native speaker review them.
- **Kaggle data.** Kaggle downloads need your own Kaggle login, so I could not fetch them. Download a snake CSV there, then Admin, **Snakes**, **Import CSV**. The importer finds columns by loose name: `scientific_name` (or `species`, `binomial`), `common_name` (or `name`), `venomous` (or `venom`, `poisonous`), and optionally `kannada_name`, `description`, `habitat`, `districts` (or `distribution`, `range`), `image`. Species already in the app are updated, new ones are added, rows with an unclear venom status are listed and skipped. Template: `docs/snakes-template.csv`.
- Check a dataset's licence before using it publicly, and check its venom labels: this app tells people what to do about snakes.

## 8. If something does not work

| Problem | Likely cause and fix |
|---|---|
| **Staff cannot sign in** | (a) No staff account exists: with `SEED_SAMPLE_DATA` off you must create staff in the admin page. (b) "Server is not reachable": the backend host in `vercel.json` is wrong or asleep. (c) "Too many requests": `TRUST_PROXY` is not `true`, so all visitors share one limit. (d) Wrong password: usernames are not case sensitive, passwords are |
| **Admin cannot sign in** | `ADMIN_USERNAME` and `ADMIN_PASSWORD` (10+ characters) must both be set. The admin is created at start-up, only if that username does not exist yet |
| **Chatbot gives fixed answers** | `CHAT_MODE` is not `AI` or `AI_API_KEY` is empty. Look for the `Chat mode:` line in the logs |
| **Chatbot says "quick answer"** | The AI call failed (wrong key, no credit, rate limit). The backend log has a `Chat assistant failed (...)` line with the reason. Fixed safe answers are used meanwhile, and a bite message always starts with "Call 108 now" |
| **Camera does not open** | Browsers allow the camera and location only on `https://` pages (Vercel is fine) and after you allow it. On a phone the native camera opens; on a computer a camera window opens |
| **Nothing loads, first request slow** | The backend host sleeps when unused: choose a setting that keeps one instance running |
| **Data disappears after a restart** | The backend is running on the demo profile (in-memory database): check `SPRING_PROFILES_ACTIVE=supabase` and the DB variables |

## 9. Safety checklist before sharing the link

- Change every demo password (`admin123`, `staff123`) or do not seed sample data.
- Keep `JWT_SECRET`, `DB_PASSWORD`, `SUPABASE_SERVICE_KEY`, `AI_API_KEY` only in the backend host's settings.
- Rotate any key that was ever pasted in a chat, ticket or commit.
- The chat and identify endpoints are public. They are rate limited per visitor, but an AI key can still be used up: set a spending limit in your Anthropic account.
- Have a doctor review `first-aid.json` and the snake texts before real use.
