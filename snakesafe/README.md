# SnakeSafe

A snakebite emergency app for Karnataka, India. When someone is bitten, SnakeSafe finds the nearest hospital with **verified** antivenom stock, alerts that hospital, and guides the person there. It also has snake information, first aid, snake catchers, snake photo identification, a chatbot and a hospital staff portal.

> **Treat every bite as an emergency. Call 108.**

The app has a complete backend (12 modules) and a plain JavaScript frontend served by the same Spring Boot app. Open http://localhost:8080 for the website, installable on Android as a PWA.

- Java 25, Spring Boot 3.5, Maven, one backend and one database for every client
- Demo profile = in-memory H2 with sample data. Nothing else to install.
- Supabase profile = PostgreSQL via environment variables, no code change

## Run it

Needs only a JDK (25, or 21 - see below) and Maven.

```bash
mvn spring-boot:run
```

Open http://localhost:8080 . API docs (Swagger UI): http://localhost:8080/swagger-ui.html

Run the tests:

```bash
mvn test
```

**If a library ever fails on Java 25**, change one line in `pom.xml`: `<java.version>25</java.version>` to `21`. (Everything here builds and passes on 25.)

## Demo logins (demo profile only)

| Who | How | Details |
|---|---|---|
| Guest | nothing | Emergency endpoints (best hospital, alerts, first aid, snakes, identify, chat) work without login |
| Patient / user | OTP | any phone number, code **123456** (also written to the server log) |
| Hospital staff | `POST /api/staff/login` | `staff-bengaluru`, `staff-mysuru`, `staff-mangaluru`, `staff-hubballi`, `staff-belagavi`, `staff-kalaburagi`, `staff-shivamogga`, `staff-davanagere` - password **staff123** |
| Admin | `POST /api/admin/login` | `admin` / **admin123** |

Sample hospitals are named "Sample Hospital <city>". Their stock is stamped relative to start-up time (some fresh, some hours old, Belagavi has none). Stock counts as **Verified** for `verified-hours` (2) after staff last updated it, so after 2 hours everything shows Unverified until staff press update (`PUT /api/staff/stock`). That is the feature working, not a bug.

These demo passwords exist only when `app.seed-sample-data` is true. It is **false** by default on the Supabase profile.


## Frontend

Plain JavaScript + HTML + CSS in `src/main/resources/static`, no Node and no build step. A hash router (`#/...`), mobile-first and responsive: bottom nav with 5 tabs on phones, a top pill nav on desktop. English / ಕನ್ನಡ toggle works on every screen (all text is in `js/i18n.js`).

| Route | Page |
|---|---|
| `#/` | Home: live status, big Emergency button, Call 108, toolkit cards, how it works, snakes, helpline |
| `#/emergency` | Emergency flow (bottom nav hidden): who was bitten, finding location, best hospital, 5-second cancel countdown, waiting, confirmed with call and directions, reached. Also: no verified stock, location off, cancelled, no reply, choose another hospital. Refresh-safe: `#/emergency/alert/<id>` |
| `#/hospitals`, `#/hospitals/<id>` | Hospital list (search, verified-only, district, map) and detail |
| `#/snakes`, `#/snakes/<id>` | Snake list with filters and detail |
| `#/first-aid` | First aid steps (cached for offline) |
| `#/catchers`, `#/catchers/<id>`, `#/rescues/<id>` | Catchers, rescue request, request status |
| `#/identify` | Photo upload and result |
| `#/login`, `#/profile` | OTP login, family members and contacts |
| `#/staff` | Hospital staff portal: login, update stock, live incoming alerts, accept or decline, "Patient on the way" |
| `#/admin` | Simple admin: hospitals, staff, snakes, catchers, alerts, rescues, photos (+ CSV export) |

The chat button (bottom right) is on every normal page.

**Animation.** Scroll reveals, count-ups, progress rings and the scroll-scrubbed "how it works" line use GSAP + ScrollTrigger (`js/motion.js`). The desktop pill nav uses the circle-fill hover with label swap idea from React Bits' PillNav, rebuilt in plain JS (`js/nav.js`). Animations are skipped when the device asks for reduced motion.

**Libraries and fonts** are served from `/vendor` and `/fonts` (GSAP 3.12.5, ScrollTrigger, Plus Jakarta Sans, Noto Sans Kannada), so the strict security policy (`script-src 'self'`) and offline mode both work.

**PWA / offline.** `sw.js` caches the app shell, `/api/first-aid`, `/api/snakes` and `/api/config`. Hospital stock and alerts are never cached, so an old stock count can never be shown as current. `tel:` links always work. The install manifest is generated from `app:` config at `/manifest.webmanifest`.

**Needs HTTPS in production.** Phones only share location, allow camera capture and install the PWA on `https://` pages (`http://localhost` is fine for testing).

**Changing the look.** Colours, radii and fonts are CSS variables at the top of `css/app.css`; markup is class-based, so a new design can replace it page by page.

## Settings in one place

`src/main/resources/application.yml`, block `app:`

| Key | Meaning |
|---|---|
| `name`, `logo`, `tagline` | App name, logo path (`/img/logo.svg`), tagline. Rename the app by editing here and replacing one image |
| `emergency-number`, `poison-helpline` | `108`, `1800-116-117` |
| `verified-hours` | Stock updated within this many hours is Verified (2) |
| `default-language` | `en` or `kn` |
| `otp-mode` | `DEMO` (fixed code 123456) or `SMS` (needs an `SmsSender` implementation, see below) |
| `identify-mode` | `STUB` (sample guesses) or `AI` (Claude vision) |
| `chat-mode` | `STUB` or `AI` |
| `cancel-window-seconds` | 5 - patient can cancel; nothing reaches the hospital |
| `no-reply-minutes` | 2 - then the alert becomes NO_REPLY |
| `log-chats` | false - chat messages are not stored unless true |
| `seed-sample-data` | Fill an empty database with sample data |

Public ones are served at `GET /api/config`. Secrets are never in this file: they come from environment variables.

## Environment variables

| Variable | Used for | Needed |
|---|---|---|
| `JWT_SECRET` | Signs login tokens (32+ characters) | **Required** on the Supabase profile; demo has a built-in dev value |
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres` (session pooler) | Supabase profile |
| `DB_USER` | e.g. `postgres.<project-ref>` | Supabase profile |
| `DB_PASSWORD` | Database password | Supabase profile |
| `SUPABASE_URL` | `https://<project-ref>.supabase.co` | Photo storage on Supabase |
| `SUPABASE_SERVICE_KEY` | Service role key (server only) | Photo storage on Supabase |
| `AI_API_KEY` | Anthropic API key | Only when `identify-mode` or `chat-mode` is `AI` |
| `SEED_SAMPLE_DATA` | `true` to seed sample data on Supabase | Optional (default false there) |

## Switch to Supabase

1. In Supabase, copy the **session pooler** connection details (host, port 5432, user `postgres.<ref>`).
2. In Storage, create a bucket named `snake-photos`.
3. Set variables and start with the `supabase` profile:

```bash
export SPRING_PROFILES_ACTIVE=supabase
export DB_URL="jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres"
export DB_USER="postgres.<project-ref>"
export DB_PASSWORD="..."
export JWT_SECRET="$(openssl rand -hex 32)"
export SUPABASE_URL="https://<project-ref>.supabase.co"
export SUPABASE_SERVICE_KEY="..."
export SEED_SAMPLE_DATA=true      # first run only, if you want sample data
mvn spring-boot:run
```

Tables are created and updated automatically (`ddl-auto=update`). The profile also switches training photos to the Supabase bucket. Windows PowerShell: use `$env:DB_URL="..."` instead of `export`.

To use Claude for photo identification and the chatbot: `export AI_API_KEY=...` and set `identify-mode: AI` / `chat-mode: AI` (or pass `--app.identify-mode=AI --app.chat-mode=AI`). The default model is `claude-opus-5-5` (`app.ai.model`). The key stays on the server.

## How an emergency works

1. `GET /api/emergency/best-hospital?lat&lng` returns the nearest hospital with **Verified** stock. If none: `found=false` plus the other options. No location: `400 LOCATION_OFF`.
2. `POST /api/alerts` starts the alert in `PENDING_CANCEL` (5 s). The hospital knows nothing yet.
3. After 5 s a scheduler moves it to `WAITING` and the hospital is notified (WebSocket). "The hospital will confirm".
4. Staff accept (`CONFIRMED`, "Antivenom available (confirmed HH:mm)" for the patient, "Patient on the way" for staff) or decline (`DECLINED`). No answer in 2 minutes gives `NO_REPLY`. Both include other hospitals to try.
5. Patient taps arrived: `ARRIVED`. The patient can cancel in the window or any time before the hospital confirms (`CANCELLED`).

Real time: `ws://localhost:8080/ws/alerts/{id}` (patient) and `ws://localhost:8080/ws/hospital/{id}?token=<staff JWT>` (staff). Polling `GET /api/alerts/{id}` always works too.

## API with example calls

Examples use bash and `curl`. Get tokens first:

```bash
B=http://localhost:8080
JSON='Content-Type: application/json'
```

### Config

```bash
curl $B/api/config
```

### Auth (OTP, no login needed to call these)

```bash
curl -X POST $B/api/auth/otp/send   -H "$JSON" -d '{"phone":"9876543210"}'
curl -X POST $B/api/auth/otp/verify -H "$JSON" -d '{"phone":"9876543210","code":"123456"}'
TOKEN=<token from verify>
```
Codes expire in 5 minutes, allow 5 attempts, and sending is rate limited (3 per number per 10 minutes).

### Profile (needs `Authorization: Bearer $TOKEN`)

```bash
AUTH="Authorization: Bearer $TOKEN"
curl $B/api/profile -H "$AUTH"
curl -X PUT $B/api/profile -H "$AUTH" -H "$JSON" -d '{"name":"Asha Rao","language":"kn"}'

curl $B/api/profile/members -H "$AUTH"
curl -X POST   $B/api/profile/members -H "$AUTH" -H "$JSON" -d '{"name":"Ravi","age":8,"relation":"son","allergies":"none","notes":""}'
curl -X PUT    $B/api/profile/members/1 -H "$AUTH" -H "$JSON" -d '{"name":"Ravi","age":9,"relation":"son"}'
curl -X DELETE $B/api/profile/members/1 -H "$AUTH"

curl $B/api/profile/contacts -H "$AUTH"
curl -X POST   $B/api/profile/contacts -H "$AUTH" -H "$JSON" -d '{"name":"Uncle","phone":"9000000009"}'
curl -X PUT    $B/api/profile/contacts/1 -H "$AUTH" -H "$JSON" -d '{"name":"Uncle","phone":"9000000010"}'
curl -X DELETE $B/api/profile/contacts/1 -H "$AUTH"
```

### Hospitals and emergency

```bash
curl "$B/api/hospitals?lat=12.97&lng=77.59"           # nearest first, with distance and ETA
curl "$B/api/hospitals/1?lat=12.97&lng=77.59"
curl "$B/api/emergency/best-hospital?lat=12.97&lng=77.59"
```
Each hospital has `stock.status` = `VERIFIED` / `UNVERIFIED` / `NONE` and `stock.minutesAgo`.

### Alerts (guests allowed)

```bash
curl -X POST $B/api/alerts -H "$JSON" \
  -d '{"patientType":"SELF","lat":12.97,"lng":77.59,"hospitalId":1}'      # patientType: SELF, OTHER, CHILD (+ optional patientName, patientAge)
ID=<id from response>
curl $B/api/alerts/$ID
curl -X POST $B/api/alerts/$ID/cancel
curl -X POST $B/api/alerts/$ID/arrived
```

### Hospital staff portal

```bash
curl -X POST $B/api/staff/login -H "$JSON" -d '{"username":"staff-bengaluru","password":"staff123"}'
STAFF="Authorization: Bearer <staff token>"
curl $B/api/staff/home -H "$STAFF"
curl -X PUT $B/api/staff/stock -H "$STAFF" -H "$JSON" -d '{"vials":10}'     # stamps "now": stock becomes Verified
curl $B/api/staff/alerts -H "$STAFF"
curl -X POST $B/api/staff/alerts/$ID/accept  -H "$STAFF"
curl -X POST $B/api/staff/alerts/$ID/decline -H "$STAFF" -H "$JSON" -d '{"reason":"No doctor free right now"}'
curl -X POST $B/api/staff/alerts/$ID/arrived -H "$STAFF"
```
Staff see who is coming and how far, never the patient's exact location, and no health details.

### Snakes and first aid

```bash
curl "$B/api/snakes"
curl "$B/api/snakes?search=cobra&venomous=true&district=Mysuru"
curl $B/api/snakes/1
curl $B/api/first-aid          # English + Kannada, also bundled for offline use
```

### Snake catchers and rescues

```bash
curl "$B/api/catchers?district=Mysuru"
curl "$B/api/catchers?lat=12.97&lng=77.59"        # nearest first
curl $B/api/catchers/1
curl -X POST $B/api/rescues -H "$JSON" \
  -d '{"catcherId":1,"lat":12.97,"lng":77.59,"notes":"Snake in the store room","phone":"9000000011"}'
curl $B/api/rescues/<id>
```

### Identify a snake by photo

```bash
curl -F "file=@snake.jpg" $B/api/identify                 # JPG or PNG, max 5 MB; photo is not kept
curl -F "file=@snake.jpg" -F "keep=true" $B/api/identify  # only if the user agrees to share it for training
```
Always answers "This may be wrong. Treat every bite as an emergency." and "Call 108". Whether a snake is venomous comes from the app's own snake list, never from the AI, and the answer is `UNSURE` unless the top guess is confident.

### Contribute training photos

```bash
curl -F "file=@snake.jpg" -F "species=Indian Cobra" -F "source=user" -F "consent=true" $B/api/snake-photos
```
`consent` must be `true`. Stored on local disk (`./data/snake-photos`) in demo, in the Supabase bucket on the Supabase profile.

### Chatbot

```bash
curl -X POST $B/api/chat -H "$JSON" \
  -d '{"message":"My father was bitten","language":"en","history":[]}'      # language: en or kn
```
If a bite is mentioned the reply always starts with "Call 108 now" (enforced in code, not only in the prompt). The assistant's rules are in `src/main/resources/chat-system-prompt.txt`. Limits: 500 characters per message, 12 messages per minute per IP.

### Admin

```bash
curl -X POST $B/api/admin/login -H "$JSON" -d '{"username":"admin","password":"admin123"}'
ADMIN="Authorization: Bearer <admin token>"

curl $B/api/admin/hospitals -H "$ADMIN"
curl -X POST   $B/api/admin/hospitals -H "$ADMIN" -H "$JSON" \
  -d '{"name":"Sample Hospital Ballari","district":"Ballari","address":"Sample address","phone":"080-5550-0109","lat":15.14,"lng":76.92,"vials":3}'
curl -X PUT    $B/api/admin/hospitals/9 -H "$ADMIN" -H "$JSON" -d '{ ...same fields... }'
curl -X DELETE $B/api/admin/hospitals/9 -H "$ADMIN"          # hides it

curl $B/api/admin/staff -H "$ADMIN"
curl -X POST   $B/api/admin/staff -H "$ADMIN" -H "$JSON" \
  -d '{"username":"staff-ballari","password":"a-strong-password","name":"Sample Staff Ballari","hospitalId":9}'
curl -X PUT    $B/api/admin/staff/10 -H "$ADMIN" -H "$JSON" -d '{ ...same fields... }'
curl -X DELETE $B/api/admin/staff/10 -H "$ADMIN"             # switches the account off

curl $B/api/admin/snakes   -H "$ADMIN"                       # POST, PUT /{id}, DELETE /{id} too
curl $B/api/admin/catchers -H "$ADMIN"                       # POST, PUT /{id}, DELETE /{id} too
curl $B/api/admin/alerts   -H "$ADMIN"                       # no names or locations
curl $B/api/admin/rescues  -H "$ADMIN"
curl -X PUT $B/api/admin/rescues/<id>/status -H "$ADMIN" -H "$JSON" -d '{"status":"DONE"}'   # REQUESTED, ACCEPTED, DONE

curl $B/api/admin/snake-photos -H "$ADMIN"
curl $B/api/admin/snake-photos/export -H "$ADMIN" -o snake-photos.csv      # columns: path,label
curl -X PUT $B/api/admin/snake-photos/1/verify -H "$ADMIN" -H "$JSON" -d '{"species":"Indian Cobra"}'
```

### Errors

Every error has the same shape and a fitting HTTP code:

```json
{ "error": "LOCATION_OFF", "message": "We could not get your location. ..." }
```

## Code layout

`com.snakesafe.*`: `config` (settings, security, seed data, limits), `auth`, `profile`, `hospital`, `alert` (state machine, scheduler, WebSocket), `staff`, `snake` (snakes + first aid), `catcher`, `identify`, `chat`, `admin`, `web` (errors, `/api/config`).

Pluggable parts, so providers can change without touching controllers: `SmsSender`, `SnakeIdentifier` (STUB, AI; a self-trained ONNX model can be added as another implementation), `ChatProvider` (STUB, AI), `PhotoStorage` (local disk, Supabase).

## Notes

- Security: stateless JWT, BCrypt passwords, same-origin only (no CORS, WebSockets accept same-origin pages only), security headers, request log without query strings, IPs or bodies, HTML stripped from free-text input.
- The rate limiter is in memory, which is right for one server. If you run several servers, move it to a shared store.
- Kannada snake names and first-aid text should be reviewed by a native speaker and a doctor before real use. The first-aid file is `src/main/resources/first-aid.json`.
- The default phone numbers of sample hospitals and catchers are made up.
- Snake pictures are drawn placeholders until real photos exist: put `<slug>.jpg` files in `static/img/snakes/` (for example `indian-cobra.jpg`) and they fade in automatically.
