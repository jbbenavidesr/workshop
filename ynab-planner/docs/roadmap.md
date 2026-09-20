# Roadmap — where this is going, and why

Working notes for picking the tool back up. `README.md` says what it is;
`AGENTS.md` says how to work on it. This says what is left.

## The problem, restated

YNAB manages money well. What it does not help with is **planning the targets
themselves** — deciding, across ~80 categories, what each monthly target should
be so the whole thing fits your income and stays roughly distributed.

That planning session has four beats. The tool originally had only the last one.

1. **Which targets are wrong?** → needs evidence (history, slack)
2. **Add the thing I now have to pay** → needs categories that are not in YNAB yet
3. **I'm over — where does it come from?** → needs a ranked harvest
4. **Apply it** → the change checklist

## Status

| Step | | |
|---|---|---|
| 0 | Revert plan overrides back to YNAB | done |
| 1 | Spend history per category → `slack` | done |
| 2 | Virtual categories **+ skip** | **half** — skip done, virtual categories not started |
| 3 | Harvest screen | not started |
| 4 | Target-spec editing (dates, cadences) | not started |

Unplanned work that got done along the way: the `NEED` cadence-0 fix, filtering
YNAB's internal inflow row, and the two-tier category row.

---

## Step 2 — virtual categories (next, and self-contained)

Plan-only categories for money you have decided to spend but have not set up in
YNAB yet. This was the original ask: *"I need to pay something new."*

- They live entirely in `plan.edn` (unlike every other entry, which is a sparse
  patch over the cached YNAB category). Give them an id that cannot collide —
  a `v-` prefix is enough.
- `build-view` concatenates them onto the cached categories. The engine already
  maps over a seq, so the rest — pillars, distribution, balance — just works.
- The diff must render them as **"create this in YNAB"**, not as an amount
  change. `plan-diff` currently compares `:ynab-current` to `:planned`; a
  virtual category has no YNAB counterpart, so it needs its own branch.
- Reverting drops them, like any other override.

The intent is real, not hypothetical: they are things you mean to do, and the
checklist should say so. But YNAB stays the source of truth, so nothing is ever
lost by resetting.

**Does not need usage feedback. Do this one first if picking back up cold.**

## Step 3 — the harvest screen

The beat that turns a calculator into a planner: *you are 1.8M over, here is
where it comes from.*

- Rank categories by `slack` descending, scopable to one over-target pillar
  (that covers both "where do I take it from" and "is the split still right" —
  they are the same feature).
- Add a `:locked` flag to `plan.edn` so rent and other non-negotiables stay out
  of the suggestions.
- Not an optimizer. A ranked list with a nudge.

**Worth using the tool for one real planning session before building this.**
Its whole value is whether the ranking feels right with 79 rows in front of
you, and two open questions only usage can answer:

- Is `avg6` the right key? It is noisy for bursty categories — ✈️ Viajes has an
  average of 535k and a median of 0.
- Does the `:monthly` ambiguity below bite in practice?

## Step 4 — editing the target spec

Today every edit collapses to `{:type :monthly :amount n}`, so touching a
savings goal destroys its meaning: "reach 10M by January" becomes a flat
number and you lose the date.

The date is the strongest rebalancing lever there is — *pushing a trip from
July to December frees ~400k/month and cuts nothing*. The UI cannot express it.

Biggest UI job of the four, which is why it is last.

---

## Known issues and open tensions

**`:monthly` means two things at once.** For `:balance-by-date` it is "what to
fund this month" (it shrinks as the balance grows); for `:monthly` it is "what
this costs per month". Totals mix the two. It has not bitten hard enough to fix
speculatively, but it is the first thing to look at if the numbers feel off.

**Yearly goals (`cadence 13`) spread as target/12.** Deliberate: this is a
steady-monthly-target planner, not a this-month funding planner. But YNAB
disagrees — for 🛡️ Seguro de Vida, YNAB wants 1.13M *this* month (due in
September) while the tool says 458k/month. Same tension as above.

**The sparkline's target line lags until reload.** Slack updates live on every
edit, but the dashed reference line and the over-budget tint still show the
page-load amount. Marked with a `ponytail:` comment in `app.js`. Fix by sending
the geometry in the JSON response — never by reimplementing it in JS.

**Untagged categories count toward the total but sit in no pillar,** so the
distribution bars do not add up to the total. The plan page nudges about it.

---

## Hard-won facts about the real budget

Worth knowing before trusting a number or writing a fixture.

- **The budget starts 2025-11.** `/months` returns 12 entries and that is all
  there is, so "avg 12m" would be a lie — `:n` is reported for that reason.
- **`activity` is negative for outflow.** Sign-flipped at the client boundary,
  where milliunits already become pesos.
- **YNAB always returns a future month,** and the current month is partial.
  Both are excluded from stats; averaging them in understates everything.
- **Goal types present:** `NEED` cadence 1 (54), cadence 13 (10), cadence 0 (3);
  `TBD` (1); no goal (11). Cadence 0 is "have X by a date" — the one that read
  as a monthly amount and inflated the plan by ~18.5M.
- **`goal_under_funded` / `goal_months_to_budget` are YNAB's own arithmetic**
  and a good oracle. The engine's `:balance-by-date` agrees with them to within
  a peso (435.294 vs 435.293 on Fondo Mudanza).
- **Categories that never spend get no slack claim.** Without that guard, every
  untouched savings goal reads as pure padding and tops the harvest ranking.
- **Credit-card payment categories are plumbing,** not spending: negative
  average, no target. They are what `:skip` is for.
