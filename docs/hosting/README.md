# Service & Persistence Hosting Evaluation

## Context

The Smeckles backend is a Scala Play Framework service currently using H2 in-memory for local/dev testing. This document evaluates options for hosting the service alongside a persistence layer within the same GCP/Firebase ecosystem as the frontend.

---

## Service Hosting (Scala/Play on JVM)

### Option 1: Cloud Run (Recommended)

| Aspect | Detail |
|--------|--------|
| What | Containerised JVM service deployed as a Docker image |
| Scaling | Scales to zero — no cost when idle |
| Cold start | ~5–10s for JVM (mitigated with min-instances=1 if needed) |
| Deploy | `gcloud run deploy` with Dockerfile |
| Cost | Free tier: 2M requests/month, 360k vCPU-seconds |
| Fits because | Low traffic (5–10 requests/week), container-friendly, same GCP project |

### Option 2: Compute Engine (VM)

| Aspect | Detail |
|--------|--------|
| What | Always-on VM running the Play service |
| Scaling | Manual — always running |
| Cost | ~$5–15/month for smallest instance (even when idle) |
| Fits because | Full control, no cold starts |
| Drawback | Overkill for your traffic pattern, costs money idle |

### Verdict: Cloud Run

Best fit — free at your scale, scales to zero, same project as Firebase.

---

## Persistence Options

### Option 1: Cloud SQL (PostgreSQL)

| Aspect | Detail |
|--------|--------|
| What | Managed PostgreSQL instance |
| Cost | ~$7–9/month minimum (db-f1-micro, always-on) |
| Scaling | Vertical (instance size) |
| Connect from Cloud Run | Via Cloud SQL Auth Proxy (built-in) |
| Fits because | Full SQL, familiar, Play/Slick/JDBC compatible |
| Drawback | Always-on cost even with zero traffic |

### Option 2: Firestore (NoSQL document DB)

| Aspect | Detail |
|--------|--------|
| What | Serverless NoSQL document database |
| Cost | Free tier: 1GB storage, 50k reads/20k writes per day |
| Scaling | Automatic, serverless |
| Connect from Cloud Run | Firebase Admin SDK (Java/Scala) |
| Fits because | Zero cost at your scale, no instance to manage |
| Drawback | NoSQL — no joins, schema-less, different query model |

### Option 3: Firebase Realtime Database

| Aspect | Detail |
|--------|--------|
| What | JSON tree database with real-time sync |
| Cost | Free tier: 1GB storage, 10GB/month transfer |
| Scaling | Automatic |
| Connect from Cloud Run | Firebase Admin SDK |
| Fits because | Free, real-time updates to frontend |
| Drawback | Limited querying, no relational model, harder for complex data |

### Option 4: Neon (Serverless PostgreSQL)

| Aspect | Detail |
|--------|--------|
| What | Serverless Postgres — scales to zero |
| Cost | Free tier: 0.5GB storage, 190 compute hours/month |
| Scaling | Auto-suspends after inactivity |
| Connect from Cloud Run | Standard JDBC connection string |
| Fits because | Full PostgreSQL, zero cost idle, Play/Slick compatible |
| Drawback | External to GCP (adds network hop), free tier limits |

### Option 5: SQLite on Cloud Run (ephemeral)

| Aspect | Detail |
|--------|--------|
| What | SQLite file bundled with the container |
| Cost | Free (part of container) |
| Scaling | N/A — data lost on container restart |
| Fits because | Zero config, identical to H2 dev workflow |
| Drawback | Not persistent across deploys/restarts — dev/testing only |

### Option 6: AlloyDB Omni / Cloud SQL with auto-stop (Preview)

| Aspect | Detail |
|--------|--------|
| What | Managed Postgres with start/stop scheduling |
| Cost | ~$0 when stopped, ~$7/month when active |
| Fits because | Real Postgres, can schedule off-hours shutdown |
| Drawback | Still preview, requires scheduling setup |

---

## Comparison Matrix

| Option | Cost (your scale) | Persistence | SQL | Scale to zero | Play/JDBC compatible |
|--------|-------------------|-------------|-----|---------------|---------------------|
| Cloud SQL (Postgres) | ~$7–9/month | ✅ | ✅ | ❌ | ✅ |
| Firestore | Free | ✅ | ❌ | ✅ | ⚠️ (NoSQL SDK) |
| Realtime DB | Free | ✅ | ❌ | ✅ | ⚠️ (NoSQL SDK) |
| Neon (Serverless PG) | Free | ✅ | ✅ | ✅ | ✅ |
| SQLite on Cloud Run | Free | ❌ | ✅ | ✅ | ✅ |
| AlloyDB with stop | ~$0–7/month | ✅ | ✅ | ⚠️ (scheduled) | ✅ |

---

## Recommendation

### For now (personal, minimal cost, SQL):

**Neon (Serverless PostgreSQL)** — free tier covers your usage, scales to zero, standard JDBC so your Play service works with Slick/JDBC unchanged. Keep H2 for local/test and Neon for deployed.

### If you prefer all-in-GCP:

**Firestore** — free, serverless, same project. But requires adapting your data access layer to NoSQL (no SQL/Slick).

### If budget allows (~$7/month):

**Cloud SQL PostgreSQL** — simplest migration path from H2, full Postgres, native Cloud Run integration.

---

## Architecture (Cloud Run + Neon)

```
┌─────────────────────────────────────────────────────┐
│                    GCP Project                       │
│                                                     │
│  ┌──────────────┐       ┌──────────────────┐       │
│  │   Firebase   │       │    Cloud Run     │       │
│  │   Hosting    │──────▶│  (Scala/Play)    │───┐   │
│  │  (React SPA) │       │                  │   │   │
│  └──────────────┘       └──────────────────┘   │   │
│                                                 │   │
└─────────────────────────────────────────────────│───┘
                                                  │
                                          JDBC connection
                                                  │
                                    ┌─────────────▼───┐
                                    │   Neon          │
                                    │  (Serverless    │
                                    │   PostgreSQL)   │
                                    └─────────────────┘
```

---

## Next Steps

1. Choose persistence option
2. Set up connection config (JDBC URL as a Secret in Secret Manager)
3. Update Play service `application.conf` to read DB URL from environment
4. Dockerise the Play service
5. Deploy to Cloud Run with `--set-secrets` for DB credentials

---

## Deep Dive: Cloud Run + Neon

### Cost Breakdown

#### Cloud Run (europe-west1 / Belgium — closest to Ireland, request-based billing)

> **No regional price premium.** `europe-west1` is classified under GCP's **Tier 1 pricing** — the same tier as `us-central1`. All unit rates and free tier limits are identical regardless of which Tier 1 region you deploy to.

| Resource | Free tier (monthly) | Unit price after free tier |
|----------|---------------------|---------------------------|
| CPU | 180,000 vCPU-seconds | $0.000024 / vCPU-second |
| Memory | 360,000 GiB-seconds | $0.0000025 / GiB-second |
| Requests | 2,000,000 | $0.40 / 1M requests |
| Egress (Europe) | 1 GiB | Standard networking rates |

**At your traffic (~5–10 requests/week):** $0.00/month. You will never leave the free tier.

Even a moderate workload (1,000 requests/day, 500ms avg latency, 1 vCPU + 512 MiB) stays comfortably within free tier limits.

#### Neon (Free plan, eu-central-1 / Frankfurt)

> **No regional price premium.** Neon's pricing is uniform across all regions. Free plan limits (100 CU-hours, 0.5 GB) and paid rates ($0.106/CU-hour Launch, $0.35/GB-month storage) are identical in `eu-central-1` and US regions.

| Resource | Included |
|----------|----------|
| Projects | Up to 100 |
| Storage per project | 0.5 GB |
| Compute | 100 CU-hours/month (≈400 hours at 0.25 CU) |
| Auto-suspend | After 5 minutes of inactivity |
| Wake-up time | ~500ms on first connection |
| Regions | AWS us-east-1, us-west-2, **eu-central-1** (closest to Ireland), ap-southeast-1 |

**At your traffic:** $0.00/month. The database sleeps most of the time and wakes on demand.

#### Total monthly cost: $0

Both services scale to zero and are deployed in European regions with no price difference versus US regions. You only start paying if you exceed the free tiers, which would require orders of magnitude more traffic than your current usage.

---

### Billing & Budget Alerts

#### GCP Budget Alerts

GCP supports budget alerts at the billing account or project level:

1. **Console** → Billing → Budgets & alerts → Create budget
2. Set budget amount (e.g. $1/month as a safety net)
3. Configure alert thresholds (e.g. 50%, 90%, 100% of budget)
4. Notifications are sent to:
   - Billing account admins and users (email, automatic)
   - Up to 5 custom email addresses
   - Cloud Monitoring (optional)
   - Pub/Sub topic (for programmatic responses, e.g. auto-disable billing)

**Recommended setup for this project:**
- Budget: $1.00/month
- Thresholds: 50% ($0.50), 90% ($0.90), 100% ($1.00)
- Action: Email notification (default)

> **Note:** There is a 24–48h delay between resource usage and billing data. In an emergency runaway scenario, costs could slightly exceed the budget before alerts fire.

#### Neon Billing Alerts

Neon Free plan has hard limits — it does not charge overages. If you exceed limits:
- Compute is suspended until the next billing cycle
- No surprise bills possible on the Free plan

If you upgrade to a paid plan later, Neon provides:
- Configurable consumption limits in the dashboard
- Email notifications when approaching limits

---

### Single Instance Scale

For a single Cloud Run instance with a JVM/Play Framework service:

| Setting | Recommended | Max supported |
|---------|-------------|---------------|
| vCPU | 1 | 8 |
| Memory | 512 MiB – 1 GiB | 32 GiB |
| Concurrency | 80 (Play is async, handles many concurrent requests) | 1000 |
| Request timeout | 300s (default) | 3600s |
| Min instances | 0 (scale to zero) | — |
| Max instances | 1 (single server for now) | 100+ |

**JVM considerations:**
- Cold start: ~5–10s (JVM boot + Play init). Acceptable at your traffic level.
- If cold starts become annoying, set `min-instances=1` (costs ~$5/month idle).
- Set container memory ≥ 512 MiB for a JVM workload. 1 GiB is comfortable.
- Use `-XX:+UseContainerSupport` (default in modern JDKs) so the JVM respects container memory limits.

**Neon connection limits (Free plan):**
- Max connections: 100 (via connection pooling endpoint)
- Use the pooled connection string (`-pooler.*.neon.tech`) for Cloud Run since containers are ephemeral.

---

### Deployment Steps

#### Prerequisites

- GCP account with billing enabled (free tier still requires a billing account)
- `gcloud` CLI installed
- Docker installed locally
- Neon account (sign up at neon.tech)

#### 1. Create Neon database

```bash
# Via Neon Console (console.neon.tech):
# 1. Create a project in eu-central-1 (Frankfurt — closest to Ireland/europe-west1)
# 2. Note the pooled connection string:
#    postgresql://<user>:<password>@<endpoint>-pooler.<region>.aws.neon.tech/<dbname>?sslmode=require
```

#### 2. Create production config

Create `conf/production.conf`:

```hocon
include "application.conf"

slick.dbs.default {
  profile = "slick.jdbc.PostgresProfile$"
  db {
    driver = "org.postgresql.Driver"
    url = ${DB_URL}
    user = ${DB_USER}
    password = ${DB_PASSWORD}
  }
}

play.evolutions.db.default.autoApply = true
play.http.secret.key = ${APPLICATION_SECRET}
play.filters.hosts.allowed = [".run.app"]
```

#### 3. Create Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/universal/stage/ /app/

EXPOSE 9000

ENTRYPOINT ["bin/smeckles-web-app", \
  "-Dconfig.resource=production.conf", \
  "-Dhttp.port=9000", \
  "-J-XX:+UseContainerSupport", \
  "-J-XX:MaxRAMPercentage=75.0"]
```

#### 4. Build the production artifact

```bash
sbt stage
```

This creates a self-contained distribution in `target/universal/stage/`.

#### 5. Deploy to Cloud Run

```bash
# Set project
gcloud config set project YOUR_PROJECT_ID

# Store secrets in Secret Manager
echo -n "postgresql://user:pass@host/db?sslmode=require" | \
  gcloud secrets create db-url --data-file=-

echo -n "your-db-user" | \
  gcloud secrets create db-user --data-file=-

echo -n "your-db-password" | \
  gcloud secrets create db-password --data-file=-

echo -n "$(head -c 64 /dev/urandom | base64)" | \
  gcloud secrets create app-secret --data-file=-

# Deploy (builds container via Cloud Build and deploys)
gcloud run deploy smeckles-api \
  --source . \
  --region europe-west1 \
  --platform managed \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 1 \
  --max-instances 1 \
  --min-instances 0 \
  --set-secrets "DB_URL=db-url:latest,DB_USER=db-user:latest,DB_PASSWORD=db-password:latest,APPLICATION_SECRET=app-secret:latest" \
  --port 9000
```

#### 6. Set up budget alert

```bash
# Create a $1 budget for the project
gcloud billing budgets create \
  --billing-account=YOUR_BILLING_ACCOUNT_ID \
  --display-name="Smeckles safety net" \
  --budget-amount=1.00USD \
  --threshold-rule=percent=0.5 \
  --threshold-rule=percent=0.9 \
  --threshold-rule=percent=1.0
```

#### 7. Verify

```bash
# Get the service URL
gcloud run services describe smeckles-api --region europe-west1 --format='value(status.url)'

# Test
curl https://YOUR_SERVICE_URL/api/v1/customers
```

---

### Summary

| Concern | Answer |
|---------|--------|
| Monthly cost | $0 at current traffic |
| When does it stop being free? | Cloud Run: >2M requests or >50 vCPU-hours/month. Neon: >0.5GB storage or >100 CU-hours/month |
| Cold start | ~5–10s (JVM) + ~500ms (Neon wake). First request after idle is slow. |
| Can I avoid cold starts? | Set `min-instances=1` (~$5/month) and Neon paid plan with always-on compute |
| Billing protection | GCP budget alerts + Neon hard limits on free plan |
| Max scale (single instance) | 8 vCPU, 32 GiB RAM, 1000 concurrent requests |
| Persistence guarantee | Neon is durable PostgreSQL — data survives restarts, redeploys, and container recycling |

---

## Securing Cloud Run → Neon Connectivity

### The Problem

By default, Neon databases accept connections from any IP address as long as valid credentials (user/password + SSL) are presented. We want to ensure only our Cloud Run service can connect, and that credential compromise has limited blast radius.

---

### Options Evaluated

| # | Approach | Security | Cost | Dev Effort | Credential Rotation |
|---|----------|----------|------|------------|---------------------|
| 1 | **Secret Manager + password auth (TLS enforced)** | Good — secrets encrypted at rest, IAM-scoped access, audit logged | $0 (free tier: 6 secret versions) | Low | Requires redeploy or volume mount for pickup |
| 2 | **IP Allow (Neon) + static IP (Cloud NAT)** | Better — network-layer restriction + credential auth | ~$32/month (Cloud NAT gateway) | Medium | Same as #1 for credentials |
| 3 | **Secret Manager + volume mount + rotation Cloud Function** | Best practical — auto-rotation, hot reload, no IP cost | ~$0 (Cloud Function invocations negligible) | Medium-High | Automatic, no downtime |
| 4 | **Neon Private Networking (AWS PrivateLink)** | Best — traffic never touches public internet | Requires Neon Scale plan ($69+/month) + AWS PrivateLink | High (cross-cloud) | Same as #1 |

---

### Recommendation: Option 1 (now) → Option 3 (when you want rotation)

**Start with Option 1** — it's $0 extra cost, provides strong security for a personal project, and takes 10 minutes to set up. Upgrade to Option 3 when you want hands-free credential rotation.

**Why not Option 2 (IP Allow)?**
- Neon's IP Allow feature requires the **Scale plan** ($69+/month) — not available on Free
- Even if it were free, a static IP via Cloud NAT costs ~$32/month
- Disproportionate cost for your threat model

**Why not Option 4 (Private Networking)?**
- Requires Neon Scale plan + AWS PrivateLink setup
- Your Cloud Run is on GCP, Neon is on AWS — cross-cloud private networking adds complexity
- Overkill for personal use

---

### Option 1: Secret Manager + Password Auth (Recommended Start)

#### Security properties

- ✅ TLS enforced (`sslmode=require`) — credentials never sent in plaintext
- ✅ Secrets encrypted at rest in Secret Manager (envelope encryption with Google-managed keys)
- ✅ IAM-scoped — only your Cloud Run service account can read the secrets
- ✅ Audit trail — Cloud Audit Logs record every secret access
- ✅ No secrets in source code, env vars, or deployment configs
- ⚠️ No network-layer restriction (anyone with valid credentials can connect from any IP)

#### How it works

```
Cloud Run Service Account
        │
        │ IAM: roles/secretmanager.secretAccessor
        ▼
┌─────────────────────┐
│  Secret Manager     │
│  • db-url           │
│  • db-user          │
│  • db-password      │
└────────┬────────────┘
         │ Injected at instance startup (env var)
         │ or read on file access (volume mount)
         ▼
┌─────────────────────┐          TLS (sslmode=require)
│  Cloud Run          │ ─────────────────────────────────▶  Neon PostgreSQL
│  (Play/Slick)       │                                     (pooler endpoint)
└─────────────────────┘
```

#### Setup

```bash
# 1. Create a dedicated service account for Cloud Run
gcloud iam service-accounts create smeckles-api \
  --display-name="Smeckles API Service Account"

# 2. Store secrets
echo -n "jdbc:postgresql://<project>-pooler.<region>.aws.neon.tech/neondb?sslmode=require" | \
  gcloud secrets create db-url --data-file=-

echo -n "your-neon-user" | \
  gcloud secrets create db-user --data-file=-

echo -n "your-neon-password" | \
  gcloud secrets create db-password --data-file=-

# 3. Grant ONLY the service account access to secrets
gcloud secrets add-iam-policy-binding db-url \
  --member="serviceAccount:smeckles-api@YOUR_PROJECT.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

gcloud secrets add-iam-policy-binding db-user \
  --member="serviceAccount:smeckles-api@YOUR_PROJECT.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

gcloud secrets add-iam-policy-binding db-password \
  --member="serviceAccount:smeckles-api@YOUR_PROJECT.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

# 4. Deploy with secrets as env vars, pinned to the service account
gcloud run deploy smeckles-api \
  --source . \
  --region europe-west1 \
  --service-account=smeckles-api@YOUR_PROJECT.iam.gserviceaccount.com \
  --set-secrets="DB_URL=db-url:latest,DB_USER=db-user:latest,DB_PASSWORD=db-password:latest" \
  --max-instances=1 \
  --allow-unauthenticated
```

#### Credential rotation (manual)

When you rotate the Neon password:

```bash
# 1. Update the secret in Secret Manager (creates a new version)
echo -n "new-password-here" | \
  gcloud secrets versions add db-password --data-file=-

# 2. Force Cloud Run to pick up the new secret
#    (env var secrets are resolved at instance startup)
gcloud run services update smeckles-api --region europe-west1
```

The `update` command triggers a new revision → new instances start → they read the latest secret version. Old instances drain gracefully.

---

### Option 3: Automated Rotation (Future Upgrade)

When manual rotation feels like a chore, upgrade to volume-mounted secrets + a Cloud Function that auto-rotates:

#### Architecture

```
Cloud Scheduler (e.g. every 90 days)
        │
        ▼
Cloud Function (rotation handler)
  1. Generate new Neon password (Neon API)
  2. Update Neon role password (SQL: ALTER ROLE)
  3. Add new Secret Manager version
        │
        ▼
Secret Manager (new version created)
        │
        │ Volume mount (reads latest on each file access)
        ▼
Cloud Run (picks up new password on next DB connection)
```

#### Key differences from Option 1

| Aspect | Option 1 (env var) | Option 3 (volume mount) |
|--------|---------------------|-------------------------|
| Secret delivery | Environment variable | Mounted file (`/secrets/db-password`) |
| When resolved | Instance startup only | Every file read |
| Rotation pickup | Requires `gcloud run services update` | Automatic (next connection pool refresh) |
| Downtime on rotation | Brief (new revision rollout) | None |
| Setup complexity | Low | Medium (Cloud Function + Scheduler + Neon API) |

#### Volume mount deployment

```bash
gcloud run deploy smeckles-api \
  --source . \
  --region europe-west1 \
  --service-account=smeckles-api@YOUR_PROJECT.iam.gserviceaccount.com \
  --set-secrets="/secrets/db-url=db-url:latest,/secrets/db-user=db-user:latest,/secrets/db-password=db-password:latest" \
  --max-instances=1
```

Your Play app reads credentials from files instead of env vars:

```hocon
# production.conf
slick.dbs.default.db {
  url = ${?DB_URL}      # falls back to file read if not in env
  user = ${?DB_USER}
  password = ${?DB_PASSWORD}
}
```

Or read from files directly in a custom config loader at startup.

---

### Additional Hardening (Low Effort)

These are complementary measures regardless of which option you pick:

| Measure | What | Effort |
|---------|------|--------|
| **Least-privilege DB role** | Create a Neon role with only `SELECT, INSERT, UPDATE, DELETE` on your tables (no `CREATE`, `DROP`, `SUPERUSER`) | 5 min |
| **Separate DB users per environment** | Dev/prod use different credentials — compromise of one doesn't affect the other | 5 min |
| **Secret Manager audit logging** | Enabled by default — review in Cloud Audit Logs who accessed secrets and when | 0 min |
| **Cloud Run ingress restriction** | Set `--ingress=all` (public API) but consider `--ingress=internal-and-cloud-load-balancing` if fronted by a load balancer | 1 min |
| **Neon branch protection** | Mark your production branch as protected (prevents deletion, limits access) — available on Free plan | 2 min |

---

### Decision Summary

| Priority | Requirement | How it's met |
|----------|-------------|--------------|
| 🔒 Security (highest) | Only my service can access DB | IAM-scoped Secret Manager (only service account can read creds) + TLS-only connections + least-privilege DB role |
| 💰 Cost | $0 additional | Secret Manager free tier (6 active versions, 10k access ops/month) |
| 🔧 Dev effort | Minimal | 10 min setup, manual rotation ~5 min when needed |
| 🔄 Rotation story | Covered | Manual now (Option 1), automated later (Option 3) with no app code changes |

**Bottom line:** You cannot IP-allowlist on Neon Free plan, but IAM-scoped secrets + TLS + least-privilege DB role is strong enough security for a personal project. The attack surface is: someone would need to compromise your GCP service account OR intercept a TLS 1.3 connection — both extremely unlikely for a personal app.