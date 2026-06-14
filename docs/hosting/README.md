# Service & Persistence Hosting Evaluation

## Table of Contents

- [Context](#context)
- [Service Hosting (Scala/Play on JVM)](#service-hosting-scalaplay-on-jvm)
- [Persistence Options](#persistence-options)
- [Comparison Matrix](#comparison-matrix)
- [Recommendation](#recommendation)
- [Architecture (Cloud Run + Neon)](#architecture-cloud-run--neon)
- [Next Steps](#next-steps)
- [Deep Dive: Cloud Run + Neon](#deep-dive-cloud-run--neon)
  - [Cost Breakdown](#cost-breakdown)
  - [Billing & Budget Alerts](#billing--budget-alerts)
  - [Single Instance Scale](#single-instance-scale)
  - [Deployment Steps](#deployment-steps)
  - [Summary](#summary)
  - [Encryption in Transit (HTTPS)](#encryption-in-transit-https)
- [Securing Cloud Run → Neon Connectivity](#securing-cloud-run--neon-connectivity)
  - [Options Evaluated](#options-evaluated)
  - [Option 1: Secret Manager + Password Auth](#option-1-secret-manager--password-auth-recommended-start)
  - [Option 3: Automated Rotation (Future)](#option-3-automated-rotation-future-upgrade)
  - [Additional Hardening](#additional-hardening-low-effort)
  - [Decision Summary](#decision-summary)
- [Design: Implementation Plan for Cloud Run + Neon Deployment](#design-implementation-plan-for-cloud-run--neon-deployment)
  - [High-Level Changes Required](#high-level-changes-required)
  - [Risk Register](#risk-register)
  - [Blockers](#blockers-must-complete-before-first-deploy)
  - [Delivery Plan](#delivery-plan)
    - [Milestone 1: Deploy with H2 In-Memory](#milestone-1-deploy-with-h2-in-memory-validate-deployment--frontend-integration)
    - [Milestone 2: Production Persistence (Cloud Run + Neon)](#milestone-2-production-persistence-cloud-run--neon-postgresql)
  - [Estimated Effort](#estimated-effort)

---

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
| Memory | 512 MiB | 32 GiB |
| Concurrency | 80 (Play is async, handles many concurrent requests) | 1000 |
| Request timeout | 300s (default) | 3600s |
| Min instances | 0 (scale to zero) | — |
| Max instances | 1 (single server for now) | 100+ |

**JVM considerations:**
- Cold start: ~5–10s (JVM boot + Play init). Acceptable at your traffic level.
- If cold starts become annoying, set `min-instances=1` (costs ~$5/month idle).
- 512 MiB container memory is sufficient for a low-traffic Play 3 app (256MB heap + 256MB JVM overhead). Bump to 768Mi–1GiB if you add heavy caching or concurrent load grows.
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

### Encryption in Transit (HTTPS)

#### TL;DR

**Cloud Run gives you HTTPS for free. No dev work, no certificates to buy, no config changes.**

Every Cloud Run service gets a `https://<service>-<hash>.run.app` URL with a Google-managed TLS certificate. This is automatic, free, and requires zero setup on your part.

#### How It Works

```
Browser / Frontend
       │
       │  HTTPS (TLS 1.3, Google-managed cert)
       ▼
┌─────────────────────┐
│  Google Front End   │  ← terminates TLS here
│  (GFE / load balancer)│
└────────┬────────────┘
         │  HTTP (plain, internal Google network)
         ▼
┌─────────────────────┐
│  Cloud Run container │  ← your app listens on port 9000 (HTTP)
└─────────────────────┘
```

- Google's front-end infrastructure handles TLS termination — your container receives plain HTTP internally
- The certificate covers `*.run.app` (wildcard) — it's not per-service, so there's nothing to provision or renew
- TLS 1.3 is supported by default; TLS 1.2 as fallback for older clients
- Certificate renewal is fully automated by Google — you never see or manage it

#### Cost

| Item | Cost |
|------|------|
| HTTPS on `*.run.app` domain | **$0** — included with Cloud Run |
| Google-managed cert for custom domain | **$0** — free if you map a custom domain via Cloud Run domain mapping |
| Self-managed cert (bring your own) | **$0** — you can upload your own, but there's no reason to since Google provides one |

There is **no certificate registry fee, no annual renewal cost, and no need to self-sign**. Google issues and rotates the certificates automatically using their own CA.

#### Custom Domain (Future)

If you later add a custom domain (e.g. `api.smeckles.app`):

```bash
gcloud run domain-mappings create --service smeckles-api \
  --domain api.smeckles.app --region europe-west1
```

Google provisions a free managed certificate for your domain via Let's Encrypt (takes ~15 minutes on first setup). No cost, no manual renewal.

#### Do You Need to Do Anything?

| Question | Answer |
|----------|--------|
| Do I need to buy a certificate? | No |
| Do I need to configure TLS in Play? | No — Play receives plain HTTP; TLS is terminated upstream |
| Do I need to self-sign? | No — and you shouldn't; browsers reject self-signed certs |
| Is `*.run.app` cert trusted by browsers? | Yes — signed by Google Trust Services CA |
| Does the frontend need any config? | No — just call `https://` URLs (which it already does) |
| Do I need to set `play.http.forwarded` or X-Forwarded-Proto? | Not required for basic operation; useful if you want to enforce HTTPS redirects in Play, but Cloud Run only exposes HTTPS externally anyway |

#### Summary

Cloud Run HTTPS is zero-effort, zero-cost, and production-grade out of the box. No action items for Milestone 1 or 2.

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

---

## Design: Implementation Plan for Cloud Run + Neon Deployment

### Overview

This section captures the concrete config and code changes required to go from the current local-dev H2 setup to a deployed Cloud Run service backed by Neon serverless PostgreSQL. It also identifies cost/availability risks and their mitigations.

---

### High-Level Changes Required

#### Configuration Changes

| File | Change | Purpose |
|------|--------|---------|
| `conf/production.conf` | **Create** — PostgresProfile, env-var-driven JDBC URL/creds, connection pool tuning | Production DB config |
| `conf/application.conf` | **No change** — remains the local dev/H2 config | Keep dev workflow unchanged |
| `build.sbt` | Add `sbt-native-packager` plugin (`PlayScala` already enables it) | Produce `sbt stage` artifact for Docker |
| `project/plugins.sbt` | Possibly add Docker plugin if `PlayScala` doesn't cover `sbt stage` | Packaging |
| `Dockerfile` | **Create** — Temurin 21 JRE Alpine, copies `target/universal/stage/`, runs with production.conf | Container image |
| `.dockerignore` | **Create** — exclude `.git`, `target`, `.idea`, logs | Lean image |
| `conf/evolutions/default/1.sql` | **Review** — ensure all DDL is PostgreSQL-compatible (no H2-only syntax) | Clean migration on Neon |
| GCP Secret Manager | Store `DB_URL`, `DB_USER`, `DB_PASSWORD`, `APPLICATION_SECRET` | Secure credential delivery |
| GCP Budget Alert | $1/month budget with 50%/90%/100% thresholds | Cost safety net |
| Cloud Run service config | `--max-instances=1`, `--memory=1Gi`, `--cpu=1`, `--min-instances=0` | Hard compute ceiling |

#### Code Changes

| Area | Change | Purpose |
|------|--------|---------|
| `conf/production.conf` | HikariCP pool: `maxConnections=5`, `numThreads=5`, `connectionTimeout=5000` | Respect Neon free-tier 100 connection limit via pooler |
| `app/Module.scala` | No change expected — DI bindings already wire Slick repos | — |
| `conf/routes` | Add a `GET /health` → 200 OK endpoint | Cloud Run health check (required for min-instances / startup probe) |
| Controller | Add `HealthController.scala` returning `Ok("ok")` | Liveness/readiness probe target |
| `play.filters.hosts.allowed` | Set to `[".run.app"]` in production.conf | Reject requests with spoofed Host headers |

---

### Risk Register

#### 1. Database Connection Exhaustion (billing + availability risk)

| Aspect | Detail |
|--------|--------|
| **Risk** | Neon free-tier pooler allows 100 connections. If the app leaks connections or a traffic spike opens more than configured, Neon rejects new connections → 500 errors. On a paid plan, excess compute hours from sustained wake time directly increase the bill. |
| **Likelihood** | Low (traffic is ~5–10 req/week), but misconfigured pool or connection leak makes it medium. |
| **Impact** | Free plan: hard rejection (no cost, but service down). Paid plan: billing surprise from sustained compute. |
| **Mitigation** | **🚫 BLOCKER — must implement before deploy:**<br>• HikariCP pool capped at `maxConnections=5` in `production.conf`<br>• Use Neon's **pooled endpoint** (`-pooler.*.neon.tech`) which adds PgBouncer in front<br>• Set `connectionTimeout=5000` so requests fail fast instead of queuing<br>• Set `idleTimeout=300000` and `maxLifetime=600000` to reclaim idle connections<br>• On paid plan: configure Neon's **consumption limit** (hard cap on CU-hours) |

#### 2. Client → Server Request Abuse / Runaway Traffic (billing risk)

| Aspect | Detail |
|--------|--------|
| **Risk** | The Cloud Run endpoint is publicly accessible. A bot, crawler, or accidental loop could generate thousands of requests, burning through the free tier (2M requests, 360k vCPU-seconds) and incurring charges. |
| **Likelihood** | Low for a personal app with no public listing, but non-zero (bots scan `*.run.app` domains). |
| **Impact** | Exceeding free tier → pay-per-use billing with no hard cap unless mitigated. |
| **Mitigation** |<br>• **`--max-instances=1`** on Cloud Run — hard ceiling on compute. One instance can serve ~80 concurrent requests; beyond that, requests queue/fail rather than spawning new billable instances.<br>• **GCP Budget Alert at $1/month** — email notification at 50%/90%/100%.<br>• **Cloud Run request timeout = 60s** (reduce from 300s default) — prevents slow-loris style resource exhaustion.<br>• **Consider Cloud Armor or rate-limiting** in future if exposed publicly (adds cost, not needed at launch).<br>• **`play.filters.hosts.allowed`** — rejects requests not targeting your actual domain, blocking generic scanners. |

#### 3. Neon Cold Start Latency (UX risk)

| Aspect | Detail |
|--------|--------|
| **Risk** | Neon auto-suspends after 5 min of inactivity. First request after suspend adds ~500ms DB wake time on top of Cloud Run's ~5–10s JVM cold start. Total first-request latency: **~6–11 seconds**. |
| **Likelihood** | High — at 5–10 requests/week, the service will almost always be cold. |
| **Impact** | Poor first-request UX, not a cost risk. |
| **Mitigation** |<br>• Accept it for now (personal project, not customer-facing).<br>• If UX matters: set Cloud Run `min-instances=1` (~$5/month) + Neon paid plan with always-on endpoint.<br>• Frontend can show a loading spinner / "waking up..." message. |

#### 4. Uncontrolled Schema Migrations in Production (data risk)

| Aspect | Detail |
|--------|--------|
| **Risk** | `play.evolutions.db.default.autoApply = true` in production means any code deploy with a new evolution file will immediately mutate the production schema — no review step. |
| **Likelihood** | Medium (you will add evolutions as features grow). |
| **Impact** | Destructive migration (e.g. `DROP COLUMN`) applied without confirmation → data loss. |
| **Mitigation** |<br>• Set `autoApply = true` for initial deploy (bootstrapping), then switch to `autoApply = false` and apply evolutions manually via a deploy step or Neon's SQL console.<br>• Use Neon **branching** — create a branch, test evolution there, merge to main branch after verification. |

#### 5. Secret / Credential Compromise (security risk)

| Aspect | Detail |
|--------|--------|
| **Risk** | DB credentials stored in Secret Manager. If GCP service account is compromised, attacker gets full DB access. Neon free plan has no IP allowlisting. |
| **Likelihood** | Very low for a single-user personal project. |
| **Impact** | Full read/write access to production data. |
| **Mitigation** |<br>• Dedicated least-privilege service account for Cloud Run (only `secretmanager.secretAccessor`).<br>• Least-privilege DB role: `SELECT, INSERT, UPDATE, DELETE` only — no `CREATE`, `DROP`, `SUPERUSER`.<br>• Rotate credentials every 90 days (manual initially, automated via Cloud Function later).<br>• `sslmode=require` on all JDBC connections. |

---

### Blockers (Must Complete Before First Deploy)

| # | Item | Why it's blocking |
|---|------|-------------------|
| 1 | **Connection pool configuration in `production.conf`** | Without explicit HikariCP limits and the pooled Neon endpoint, the app could exhaust connections on the first concurrent burst, causing 500s and (on paid plan) billing spikes. |
| 2 | **Use Neon pooler endpoint** | Direct connections bypass PgBouncer — ephemeral Cloud Run containers would leak connections on scale-down. Pooler is mandatory for serverless compute. |
| 3 | **`--max-instances=1` on Cloud Run deploy** | Without this hard cap, a traffic spike could auto-scale to many instances, each with its own connection pool, compounding both compute cost and DB connection pressure. |
| 4 | **GCP billing alert configured** | No automated spend cap exists on GCP. Budget alerts are the only notification mechanism before charges accumulate. |

---

### Delivery Plan

---

#### Milestone 1: Deploy with H2 In-Memory (Validate Deployment + Frontend Integration)

**Goal:** Get the service running on Cloud Run with the existing H2 in-memory configuration. Validates the full deployment pipeline, network accessibility, and enables UI ↔ service ↔ persistence (e2e) integration testing. Data persists for the lifetime of the container instance — acceptable for this phase.

**Unblocks (can run concurrently with Milestone 2):**
- Frontend → service API integration coding and testing
- Firebase Auth token flow validation
- CORS and networking verification

---

##### Phase 1.1: Health Check Endpoint [COMPLETED ✅]

**Plain English:** A simple endpoint that Cloud Run (and you) can hit to confirm the app is alive and ready. Also serves as a smoke test that your test harness is working before you tackle the auth handler.

- [ ] **Add route to `conf/routes`**

```routes
GET     /health     controllers.HealthController.check()
```

- [ ] **Create `app/controllers/HealthController.scala`** — returns `Ok("ok")`

- [ ] **Unit test** — verify the controller returns 200 with body "ok"

- [ ] **Functional test** — boot the full app, `GET /health` → assert 200

---

##### Phase 1.2: Firebase Auth Token Verification [COMPLETED ✅]

**Plain English:** The frontend signs in with Google via Firebase Auth and gets an ID token (a signed JWT). It sends this as `Authorization: Bearer <token>` on every API call. The backend verifies the token is genuine by checking its signature against Google's public keys, confirming the issuer/audience match your Firebase project, and rejecting expired tokens. This is standard JWT verification — no Firebase Admin SDK needed on the backend.

- [ ] **Add dependencies to `build.sbt`**

```scala
"com.auth0" % "java-jwt" % "4.4.0",   // JWT decode + verify
"com.auth0" % "jwks-rsa" % "0.22.1"   // Fetches Google's public signing keys
```

- [ ] **Create `app/auth/AuthenticatedAction.scala`**

A Play `ActionBuilder` that:
1. Extracts Bearer token from the `Authorization` header
2. Uses `UrlJwkProvider` to fetch keys from `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`
3. Verifies with `JWT.require(Algorithm.RSA256(publicKey, null))` — checking issuer (`https://securetoken.google.com/<projectId>`) and audience (`<projectId>`)
4. Extracts `sub` (Firebase UID) and `email` claim from verified token
5. Returns `401` if token is missing or invalid

Wrap into an `AuthenticatedRequest[A]` that carries `userId` and `email` for controllers to use.

- [ ] **Add config to `application.conf`**

```hocon
# Firebase project ID — used to validate token issuer and audience.
# This is NOT a secret. Find it in Firebase Console → Project Settings → General.
# Firebase projects ARE GCP projects — same project hosts Cloud Run, Auth, and Hosting.
auth.firebase.projectId = "smeckles-app-11ca3"
```

- [ ] **Wire into controllers**

Replace `Action.async` with `authenticated.async` on protected endpoints. Keep health check public.

- [ ] **Unit tests** — mock the JWT verification layer; test that:
  - Missing `Authorization` header → 401
  - Malformed token → 401
  - Valid token → request proceeds with correct `userId`/`email`

- [ ] **Functional tests** — boot the full app; test that:
  - `GET /health` still returns 200 (no auth required)
  - Protected endpoints without token → 401
  - Protected endpoints with a test token → pass through (use a self-signed test JWT or mock the JWKS provider in test config)

**Frontend side** (for reference):
```typescript
const token = await getAuth().currentUser.getIdToken();
fetch(url, { headers: { "Authorization": `Bearer ${token}` } });
```

---

##### Phase 1.3: Application Configuration for Cloud Run

**Plain English:** We need the app to work both locally (hardcoded dev values) and on Cloud Run (config from environment variables). Play's HOCON config supports `${?ENV_VAR}` syntax — "use this env var if set, otherwise fall through to the value above." We also add CORS (so the browser allows the frontend to call the API across domains) and a host header filter (rejects requests with spoofed `Host` headers).

- [ ] **Add to `conf/application.conf`**

```hocon
# App secret — "changeme" for local dev, overridden by env var on Cloud Run
play.http.secret.key = "changeme"
play.http.secret.key = ${?APPLICATION_SECRET}

# Host header filter — rejects requests not targeting these hosts
play.filters.hosts.allowed = ["localhost", ".run.app"]

# CORS — allows the frontend (different origin) to call the API.
# Without this, the browser blocks your frontend from making fetch() calls to the API
# because they're on different domains.
#
# Firebase Hosting URL for project "smeckles-app-11ca3" will be:
#   https://smeckles-app-11ca3.web.app       (primary)
#   https://smeckles-app-11ca3.firebaseapp.com (legacy alias — also works)
#
# We hardcode both plus localhost for dev. No env var needed — these are known at build time.
play.filters.enabled += "play.filters.cors.CORSFilter"
play.filters.cors {
  allowedOrigins = [
    "http://localhost:3000",                         # local frontend dev server
    "https://smeckles-app-11ca3.web.app",           # Firebase Hosting (primary)
    "https://smeckles-app-11ca3.firebaseapp.com"    # Firebase Hosting (legacy alias)
  ]
  allowedHttpMethods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
  allowedHttpHeaders = ["Authorization", "Content-Type"]
}
```

> **Note on env vars and HOCON lists:** HOCON's `${?ENV_VAR}` substitutes a single string — it can't inject a list. Since your Firebase Hosting origins are known at build time (they're derived from the project name), just hardcode them. If you later need a custom domain, add it to this list and redeploy.

---

##### Phase 1.4: Dockerfile & Packaging

**Plain English:** We need to package the Play app into a Docker image that Cloud Run can run. We use a two-stage build — stage 1 compiles the code using a full JDK+sbt image, stage 2 copies just the compiled output into a tiny JRE-only image (~80MB). This keeps the deployed image small and secure (no build tools in production).

The Play sbt-plugin already includes sbt-native-packager, so `sbt stage` produces a self-contained distribution (launcher script + all JARs) in `target/universal/stage/` — no extra plugins needed.

- [ ] **Create `Dockerfile`**

```dockerfile
# Stage 1: Compile with full build toolchain (discarded after build)
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.6_7_1.10.11_3.3.7 AS builder
WORKDIR /app

# Deps layer — cached until build.sbt or project/ changes
COPY build.sbt build.sbt
COPY project/  project/
RUN sbt update

# Source layer — rebuilds on code changes only
COPY app/    app/
COPY conf/   conf/
COPY public/ public/
RUN sbt stage

# Stage 2: Minimal runtime (JRE only, no sbt/JDK/source)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/universal/stage/ .
EXPOSE 9000
ENTRYPOINT ["bin/simpleshoppinglistapp", "-Dhttp.port=9000", "-J-Xmx256m"]
```

**Key points:**
- Dependencies are cached in a separate Docker layer — code changes don't re-download the internet
- `-J-Xmx256m` caps heap at 256MB (container gets 512Mi total; leaves ~256MB for JVM metaspace, thread stacks, and off-heap). A minimal Play 3 app idles at ~150MB heap — 256MB gives comfortable headroom for 5–10 requests/week without over-provisioning.
- No `-Dplay.http.secret.key` here — it comes from the `APPLICATION_SECRET` env var at runtime (see Phase 1.3)

- [ ] **Create `.dockerignore`**

```dockerignore
.git/
target/
.idea/
.bsp/
*.log
node_modules/
```

- [ ] **Verify locally**

```bash
sbt stage
./target/universal/stage/bin/simpleshoppinglistapp -Dhttp.port=9000
# → http://localhost:9000/health should respond 200
```

---

##### Phase 1.5: GCP Setup & Deploy (H2 Mode)

**Plain English:** We set up a GCP project, store the Play app secret securely in Secret Manager, create a least-privilege service account for the container, and deploy. Cloud Run builds the Docker image for us (via Cloud Build from the Dockerfile) and runs it as a serverless container. The `--max-instances 1` flag keeps H2 state consistent (one instance = one in-memory DB).

- [ ] **Enable APIs**

```bash
gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
  secretmanager.googleapis.com artifactregistry.googleapis.com
```

- [ ] **Store app secret in Secret Manager**

```bash
printf "$(openssl rand -base64 64)" | gcloud secrets create play-app-secret --data-file=-
```

- [ ] **Create service account with minimal permissions**

```bash
gcloud iam service-accounts create smeckles-api \
  --display-name="Smeckles API"

gcloud secrets add-iam-policy-binding play-app-secret \
  --member="serviceAccount:smeckles-api@smeckles-app-11ca3.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

- [ ] **Deploy**

```bash
gcloud run deploy smeckles-api \
  --source . \
  --region europe-west1 \
  --memory 512Mi --cpu 1 \
  --max-instances 1 --min-instances 0 \
  --set-secrets "APPLICATION_SECRET=play-app-secret:latest" \
  --service-account smeckles-api@smeckles-app-11ca3.iam.gserviceaccount.com \
  --allow-unauthenticated \
  --port 9000 \
  --timeout 60
```

| Flag | Why |
|------|-----|
| `--source .` | Builds the Dockerfile via Cloud Build — no local Docker push needed |
| `--max-instances 1` | One container = one H2 DB = consistent state |
| `--min-instances 0` | Scale to zero when idle (free) |
| `--set-secrets` | Injects Secret Manager value as env var at startup |
| `--allow-unauthenticated` | Public URL — auth is handled at the app layer (Firebase token check) |
| `--timeout 60` | Kills requests >60s — prevents resource exhaustion |

- [ ] **Configure budget alert**

```bash
gcloud billing budgets create \
  --billing-account=YOUR_BILLING_ACCOUNT_ID \
  --display-name="Smeckles safety net" \
  --budget-amount=1.00USD \
  --threshold-rule=percent=0.5 \
  --threshold-rule=percent=0.9 \
  --threshold-rule=percent=1.0
```

---

##### Phase 1.6: Verification

```bash
# Get the service URL
SERVICE_URL=$(gcloud run services describe smeckles-api \
  --region europe-west1 --format='value(status.url)')

# 1. Health check (public, no auth)
curl $SERVICE_URL/health
# → 200 ok

# 2. Unauthenticated request to protected endpoint
curl $SERVICE_URL/api/v1/customers/test@example.com
# → 401 {"error":"..."}

# 3. Authenticated request (paste a real Firebase token)
curl -H "Authorization: Bearer $TOKEN" \
  $SERVICE_URL/api/v1/customers/test@example.com
# → 200 or 404 (not 401)

# 4. Create + retrieve (confirms H2 persists within instance)
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}' \
  $SERVICE_URL/api/v1/customers

curl -H "Authorization: Bearer $TOKEN" \
  $SERVICE_URL/api/v1/customers/test@example.com
# → 200 {"email":"test@example.com"}

# 5. Cold start — wait 15+ min, then:
time curl $SERVICE_URL/health
# → ~5-10s (JVM boot). Data from step 4 is gone (expected with H2).
```

**Frontend check:** Sign in on the web app, trigger an API call, confirm no CORS errors in browser console and data round-trips correctly.

---

##### Milestone 1 — Acceptance Criteria

| Criterion | How to verify |
|-----------|---------------|
| Service accessible on Cloud Run | `curl /health` → 200 |
| Auth working end-to-end | Frontend sign-in → Bearer token → API responds |
| CORS configured | Browser fetch from frontend origin succeeds |
| Data persists within instance lifetime | Create customer → get customer (same instance) |
| Cost = $0 | GCP billing dashboard after 1 week |

##### Milestone 1 — Known Limitations (Accepted)

| Limitation | Impact | Resolved in |
|------------|--------|-------------|
| Data lost on cold start / redeploy | Acceptable for integration testing | Milestone 2 (Neon) |
| Single instance only | No HA — fine for personal dev | Milestone 2 |
| No persistent storage | Cannot demo to others with long-lived data | Milestone 2 |

---

#### Milestone 2: Production Persistence (Cloud Run + Neon PostgreSQL)

**Goal:** Replace H2 with Neon serverless PostgreSQL for durable persistence. Can be developed concurrently with frontend work unblocked by Milestone 1.

**Prerequisites:** Milestone 1 deployed and verified. Neon account created.

##### Phase 2.1: Production Configuration

- [ ] **Create `conf/production.conf`**
  - Slick profile → `PostgresProfile$`, driver → `org.postgresql.Driver`
  - DB URL, user, password read from env vars (`${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`)
  - HikariCP pool: `maxConnections=5`, `numThreads=5`, `connectionTimeout=5000`, `idleTimeout=300000`, `maxLifetime=600000`
  - `play.http.secret.key = ${APPLICATION_SECRET}`
  - `play.filters.hosts.allowed = [".run.app"]`
  - `play.evolutions.db.default.autoApply = true` (bootstrap only)

- [ ] **Verify evolution SQL is PostgreSQL-compatible**
  - Review `conf/evolutions/default/1.sql` for H2-only syntax
  - Test against a local PostgreSQL or Neon dev branch

- [ ] **Update Dockerfile entrypoint** to use `-Dconfig.resource=production.conf`

##### Phase 2.2: Neon Database Setup

- [ ] **Create Neon project in `eu-central-1`**
  - Note the pooled connection string (`-pooler.*.neon.tech`)
  - Create a dedicated role with least-privilege (`SELECT`, `INSERT`, `UPDATE`, `DELETE` on app tables only)

- [ ] **Test Neon connectivity locally**
  - Point local Play app at Neon pooled endpoint (temporary override via `-D` flag)
  - Confirm evolutions apply cleanly and CRUD operations work

- [ ] **Create a Neon dev branch (optional but recommended)**
  - Use for testing schema migrations before applying to main branch

##### Phase 2.3: Secrets & Redeploy

- [ ] **Store DB secrets in Secret Manager**
  - `db-url` — full JDBC pooled connection string with `?sslmode=require`
  - `db-user` — Neon role username
  - `db-password` — Neon role password

- [ ] **Redeploy to Cloud Run with DB secrets**
  ```bash
  gcloud run deploy smeckles-api \
    --source . \
    --region europe-west1 \
    --memory 1Gi --cpu 1 \
    --max-instances 1 --min-instances 0 \
    --set-secrets "DB_URL=db-url:latest,DB_USER=db-user:latest,DB_PASSWORD=db-password:latest,APPLICATION_SECRET=play-app-secret:latest" \
    --service-account smeckles-api@YOUR_PROJECT.iam.gserviceaccount.com \
    --allow-unauthenticated \
    --port 9000 \
    --timeout 60
  ```

- [ ] **Verify deployment with Neon**
  - CRUD operations persist across cold starts
  - Confirm Neon wake-up latency is acceptable (~500ms)

##### Phase 2.4: Post-Deploy Hardening

- [ ] **Switch `play.evolutions.db.default.autoApply` to `false`**
  - After initial schema is bootstrapped, disable auto-apply
  - Document the manual evolution workflow (Neon SQL console or CI step)

- [ ] **Set up Cloud Run request logging / alerting (optional)**
  - Cloud Logging filter for 5xx responses
  - Alert policy: >5 errors in 5 minutes → email

- [ ] **Document credential rotation procedure**
  - Manual steps: rotate in Neon console → update Secret Manager version → `gcloud run services update`

- [ ] **Consider future enhancements (backlog, not blocking)**
  - Automated secret rotation via Cloud Function + Cloud Scheduler
  - Custom domain (map via Cloud Run domain mapping)
  - Cloud Armor rate limiting if publicly listed

---

### Estimated Effort

| Phase | Effort | Dependencies |
|-------|--------|--------------|
| **Milestone 1** | | |
| Phase 1.1: Health Check | ~30 min | None |
| Phase 1.2: Firebase Auth | ~2–3 hours | Firebase project |
| Phase 1.3: App Config (CORS, hosts, secret) | ~30 min | None |
| Phase 1.4: Dockerfile & Packaging | ~1 hour | None |
| Phase 1.5: GCP Setup & Deploy | ~1 hour | GCP account with billing |
| Phase 1.6: Verification | ~30 min | Phases 1.1–1.5 complete |
| **Milestone 1 Total** | **~5–6 hours** | |
| | | |
| **Milestone 2** | | |
| Phase 2.1: Production Config | ~1 hour | Milestone 1 complete |
| Phase 2.2: Neon Setup | ~1 hour | Neon account |
| Phase 2.3: Secrets & Redeploy | ~1 hour | Phases 2.1–2.2 complete |
| Phase 2.4: Hardening | ~1 hour | Phase 2.3 complete |
| **Milestone 2 Total** | **~4 hours** | |
| | | |
| **Grand Total** | **~9–10 hours** | — |
