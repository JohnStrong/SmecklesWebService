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

## Summary

| Scenario | Strategy | Why |
|----------|----------|-----|
| Customer deleted | CASCADE DELETE (immediate) | No future value in orphaned data; no analysis needed |
| Active customer, stale resources | Async queue (deferred) | Resources may be useful for analysis before deletion |
| Active customer, resources within TTL | No action | Data is still operationally relevant |
