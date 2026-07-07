# Async Resource Cleanup

## Overview

A scheduled, event-driven cleanup process for expiring stale resources belonging to **active** customers. Designed for the future state where the app tracks budgets, expenses, categories, and shopping history over time — data that loses operational value after 2–3 months but may be useful for analysis before deletion.

## Key Distinction: Deleted vs Active Customers

### Deleted Customers → CASCADE DELETE (in-time)

When a customer is explicitly deleted, all their owned resources are removed immediately via `ON DELETE CASCADE` foreign keys. There is no reason to delay cleanup because:

- The customer no longer uses the app
- No budget analysis, recommendations, or reporting will be generated for them
- Orphaned resources have zero value
- CASCADE is transactional, simple, and leaves no dead data

**This is the current implementation.** No queue or async processing is needed for this case.

### Active Customers → Async Cleanup (deferred, post-analysis)

For customers who still use the app, old resources (shopping lists, budget entries, expense records) should be retained temporarily for:

- Spending trend analysis and reporting
- Shopping list auto-completion recommendations (frequently bought items)
- Budget category insights

Once analysis is complete (or a TTL expires), the raw rows are no longer needed and can be cleaned up in bulk. This is where the async queue design applies.

## When To Build This

Build the async cleanup when:

1. The app tracks data over time (budgets, expenses, purchase history)
2. There is an analysis/reporting service that consumes historical data
3. Raw data older than N months has been processed and is no longer needed
4. The volume of stale data would meaningfully impact database performance or cost

**Not needed today** — the app only stores shopping lists, which are actively used until manually removed.

## Proposed Design

### Dead Letters / Cleanup Queue

A `cleanup_queue` table tracks resources scheduled for deferred deletion:

```sql
CREATE TABLE cleanup_queue (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(320) NOT NULL,
    resource    VARCHAR(50) NOT NULL,        -- e.g. 'shopping_lists', 'budget_entries'
    status      VARCHAR(10) NOT NULL DEFAULT 'PENDING',  -- PENDING | COMPLETED | FAILED
    attempts    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Scheduled Cleanup Worker

- Runs once per hour (Pekko Scheduler in single-instance; Cloud Scheduler for multi-instance)
- Picks the top N `PENDING` entries ordered by `created_at` (FIFO)
- For each entry: deletes the target resources, increments `attempts`, marks `COMPLETED`
- On failure: increments `attempts`. If `attempts >= 3`, marks as `FAILED`
- `FAILED` entries trigger an alert to service owners (future integration)

### Flow

```
┌─────────────────────────────┐
│  TTL Expiry Check           │
│  (scheduled daily/weekly)   │
│  Finds resources older      │
│  than N months for active   │
│  customers                  │
└──────────────┬──────────────┘
               │ inserts
               ▼
┌─────────────────────────────┐
│  cleanup_queue              │
│  status: PENDING            │
└──────────────┬──────────────┘
               │ polled hourly
               ▼
┌─────────────────────────────┐
│  Cleanup Worker             │
│  • Picks top N PENDING      │
│  • Deletes target resources │
│  • Marks COMPLETED/FAILED   │
└─────────────────────────────┘
```

### Design Principles

- **Idempotent** — cleanup operations are safe to retry (`DELETE WHERE id = X` is a no-op if already deleted)
- **Batched** — processes top N per run to avoid long-running transactions
- **Fault tolerant** — retries on transient failures, gives up after 3 attempts
- **Observable** — FAILED entries provide visibility into cleanup issues
- **Decoupled** — new resource types are added by teaching the worker about new tables, not by changing the TTL check

### Considerations for Multi-Instance Deployment

When running multiple Cloud Run instances:

- Use a distributed lock (e.g. `SELECT ... FOR UPDATE SKIP LOCKED`) or an external trigger (Cloud Scheduler → dedicated cleanup endpoint) to prevent duplicate work
- Alternatively, design the worker to be idempotent and tolerate concurrent runs (worst case: two workers claim the same entry, one succeeds, the other no-ops)

## Housekeeping: Cleaning the Cleanup Queue Itself

COMPLETED entries are retained for 30 days (audit trail / debugging), then purged by a separate scheduled task:

```sql
DELETE FROM cleanup_queue
WHERE status = 'COMPLETED'
  AND updated_at < NOW() - INTERVAL '30 days';
```

FAILED entries are retained indefinitely until manually reviewed or resolved.

This housekeeping task can run daily (low frequency, low urgency). It uses the index on `(status, updated_at)` — see below.

## Efficient Querying at Scale

### Indexes

The worker and housekeeping queries always filter by `status` and order by time. A composite index makes both operations index-only scans:

```sql
-- Worker picks PENDING items oldest-first
CREATE INDEX idx_cleanup_queue_pending ON cleanup_queue (status, created_at)
  WHERE status = 'PENDING';

-- Housekeeping finds old COMPLETED items
CREATE INDEX idx_cleanup_queue_completed ON cleanup_queue (status, updated_at)
  WHERE status = 'COMPLETED';
```

Partial indexes (the `WHERE` clause) keep the index small — only rows matching the predicate are indexed. As entries move to COMPLETED/FAILED, they drop out of the PENDING index automatically.

### Query Patterns

Worker (hourly):
```sql
SELECT * FROM cleanup_queue
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 10
FOR UPDATE SKIP LOCKED;
```

Housekeeping (daily):
```sql
DELETE FROM cleanup_queue
WHERE status = 'COMPLETED'
  AND updated_at < NOW() - INTERVAL '30 days';
```

Both hit their respective partial index directly — no full table scan regardless of table size.

## Capacity Analysis: ~100 Users, TTL-Based Cleanup

This analysis covers the **active customer TTL cleanup** case — resources older than 2 months are queued for deletion after analysis.

### Resource Volume Per User Per Month

| Resource Type | Entries/Month | Notes |
|---------------|---------------|-------|
| shopping_lists | 4–5 | Weekly grocery runs, ad-hoc lists |
| bills | 4–5 | Utilities, phone, internet, etc. |
| subscriptions | 5 | Streaming, gym, software |
| one_time_expense | ~5 | Variable — estimated average |
| rent_mortgage | 1 | Single monthly payment |
| **Total** | **~20** | Per user, per month |

### Inflow to Cleanup Queue (After 2-Month TTL)

Once resources pass the 2-month threshold, they become eligible for cleanup. After initial ramp-up (first 2 months produce nothing), the queue receives a steady stream:

| Metric | Value |
|--------|-------|
| Resources per user per month | ~20 |
| Users | 100 |
| New cleanup entries per month | ~2,000 |
| New cleanup entries per week | ~500 |
| New cleanup entries per day | ~70 |

### Table Size Over Time

Assuming the worker runs hourly and processes entries within 24 hours, and COMPLETED entries are purged after 30 days:

| State | Typical Count | Notes |
|-------|---------------|-------|
| PENDING | 0–70 | Cleared within hours of creation |
| COMPLETED (retained 30d) | ~2,000 | One month's worth before purge |
| FAILED (cumulative) | 0–20 | Rare; reviewed manually |
| **Total at steady state** | **~2,000–2,100** | Dominated by 30-day COMPLETED retention |

### Query Cost

- **Worker (hourly):** `SELECT ... WHERE status = 'PENDING' ORDER BY created_at LIMIT 50` — hits partial index, returns 0–70 rows. Sub-millisecond.
- **Housekeeping (daily):** `DELETE ... WHERE status = 'COMPLETED' AND updated_at < 30 days` — deletes ~70 rows/day. Negligible.
- **Total table size:** ~2,000 rows at steady state. PostgreSQL handles this without breaking a sweat — no partitioning or archiving needed.

### Scaling Projections

| Users | Entries/Month | Steady-State Table Size | Acceptable? |
|-------|---------------|------------------------|-------------|
| 100 | 2,000 | ~2,000 | ✅ Trivial |
| 500 | 10,000 | ~10,000 | ✅ Fine |
| 1,000 | 20,000 | ~20,000 | ✅ Fine |
| 10,000 | 200,000 | ~200,000 | ⚠️ Consider partitioning or shorter retention |

### When to Evolve

- **<1,000 users:** Current design is more than sufficient
- **1,000–10,000 users:** Monitor query latency; likely still fine with indexes
- **10,000+ users:** Consider time-based table partitioning, shorter COMPLETED retention, or moving to a dedicated job queue (e.g. Cloud Tasks)

**Bottom line:** At 100 users with ~20 resources/month each, the table stays around 2,000 rows at steady state. Partial indexes make all queries efficient. This design scales comfortably to thousands of users before needing any architectural changes.

## Summary

| Scenario | Strategy | Why |
|----------|----------|-----|
| Customer deleted | CASCADE DELETE (immediate) | No future value in orphaned data; no analysis needed |
| Active customer, stale resources | Async queue (deferred) | Resources may be useful for analysis before deletion |
| Active customer, resources within TTL | No action | Data is still operationally relevant |
