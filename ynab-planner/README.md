# YNAB Budget Planner

A small local web app for **planning** a month's YNAB category targets as a whole —
the step YNAB itself doesn't help with.

## What it does, and why

YNAB is excellent for *managing* money day to day: giving every peso a job, tracking
spending, knowing what each category holds. Where it falls short is the **planning** of the
targets themselves — deciding, across dozens of categories and competing goals, what each
monthly target *should be* so that:

1. **Every target adds up to your base income** — no more, no less. Every peso (including
   savings, getting a month ahead, and payroll deductions) has a job.
2. **The plan stays roughly distributed across four pillars** — the 10/20/30/40 split of
   **Donaciones / Ahorro / Fun / Necesario**.
3. **You can rebalance quickly** when something changes — a new goal, a new recurring
   payment, or a target that no longer matches reality — without losing 1 and 2.

Doing this by hand (a spreadsheet, then copying each target into YNAB) is slow and fragile.
This tool is the **planning brain**: it pulls your real budget in, lets you model targets,
proves in real time that they balance to income and the distribution is right, and then hands
you a **checklist of exactly what to change in YNAB**.

It is deliberately **read-only**: it never writes to your budget. The output is always a
human punch list you apply yourself.

## How it works

```
YNAB API (read-only)          ── one explicit "Sync" pulls a snapshot
      │
      ▼
   cache.edn  (snapshot: groups, categories, goals, balances)
      │
      ├──────────────┐
      ▼              ▼
   plan.edn        engine (pure)   target → distribution → balancing → diff → view
   (your data:       │
    income, pillar   ▼
    tags, target    server-rendered page  +  live recompute on edit
    overrides)
```

- The **engine** is pure: a function of `(cache, plan) → view`. It derives each category's
  **monthly contribution** from its target spec (fixed monthly, savings-balance-by-date, etc.),
  sums them against income, computes the pillar distribution, and diffs your plan against
  what's currently in YNAB.
- **Offline-first:** the engine never touches the network. Only *Sync* does. The app works
  fully offline against the last snapshot (shown with a timestamp).
- **Editing** a category amount sets a monthly override; tagging a pillar updates the split.
  Each edit is a fast round-trip to the local engine, which re-renders totals, the
  distribution bars, and the "what to change in YNAB" checklist.

## Running it

Requires a JDK (17+) and the Clojure CLI.

```bash
cd ynab-planner
clojure -M:run          # serves http://localhost:3000  (Ctrl-C to stop)
clojure -M:test         # run the test suite (Kaocha)
```

Configuration comes from the environment, or a `.env` file in this directory
(copy `.env.example`). Real env vars take precedence over `.env`.

| Var | Purpose |
|-----|---------|
| `YNAB_TOKEN` | Read-only Personal Access Token (only needed to Sync). |
| `YP_DATA_DIR` | Where `plan.edn` / `cache.edn` live (default: current dir). |
| `YP_PLAN_MONTH` | Plan a specific month `YYYY-MM` (default: current month). |

The **budget id** lives in `plan.edn` under `:budget-id` (the alias `"last-used"` works).
`plan.edn` and `cache.edn` are git-ignored — they hold your personal data.

## Project structure

```
src/ynab_planner/
  engine/            pure planning logic (no I/O)
    target.clj         target spec → monthly contribution
    distribution.clj   sum by pillar vs the 10/20/30/40 ideal
    balance.clj        Σ contributions vs income (balanced/over/under)
    diff.clj           plan vs YNAB → change checklist
    view.clj           composes the above into one view map
  ynab/
    mapping.clj        YNAB goal type → internal target spec
    client.clj         fetch (network) + parse JSON → cache (pure)
  store/files.clj      edn persistence (plan.edn, cache.edn)
  config.clj           env vars + .env loading
  sync.clj             fetch → parse → write cache
  web/
    views.clj          Hiccup server-rendered HTML
    server.clj         http-kit handler + routing
  main.clj             entry point
resources/public/      vanilla CSS (CUBE + design tokens) + JS Web Components
test/                  mirrors src/ — the engine is exhaustively tested
```

## Tech

Clojure (deps.edn, Kaocha) · http-kit (server + client) · Cheshire (JSON) · Hiccup (HTML) ·
vanilla CSS in the CUBE style with design tokens · vanilla-JS Web Components. No build step
for the frontend; no JS frameworks.

## Status

v1: single-month planning, read-only. Not for multi-month timelines/phases yet.
