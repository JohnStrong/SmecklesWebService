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
