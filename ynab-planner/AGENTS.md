# Working on the YNAB Budget Planner — guidelines for agents

Read `README.md` first for what this tool is and why. This file is *how* to work on it.

## Mindset

This is a **personal, single-user, low-stakes** tool in a workshop repo. **Fun and learning
are first-class** — favor clean structure and good craft, and treat novel or experimental
choices as welcome rather than risky. It is "a nicer spreadsheet": it does **not** touch
money and does **not** write to the budget.

## Hard constraints (don't break these)

- **Read-only, by design.** Never write anything back to YNAB — not targets, not even
  `budgeted` amounts. The product is the planning plus a *human* checklist of what to change;
  keep it that way.
- **Offline-first.** The engine is a pure function of `(cache, plan) → view` and must never
  touch the network. Only `sync` hits the YNAB API. The app must render from the last cache
  (or an empty one) without erroring.
- **Money is whole pesos as `long`.** YNAB milliunits → pesos (`quot … 1000`) happens once,
  at the client boundary, and nowhere else.
- **One pillar per category** (`:donaciones :ahorro :fun :necesario`). Tags live in `plan.edn`.

## How to work

- **TDD is the discipline.** Use the REPL to *discover* the shape of a function, but the
  moment you know it: failing test first, then implement (red → green → refactor). The REPL is
  not a substitute for tests.
- **Test weight is intentional:** the **engine is exhaustively tested**; the YNAB client,
  store, and config are tested simply (fixtures, round-trips, pure parsers); the **web/UI layer
  is verified by hand** — don't over-test it.
- **Keep units small and pure.** New planning logic belongs in `engine/` as pure functions.
  I/O lives at the edges (`ynab/client`, `store`, `web`).

```bash
clojure -M:test     # full suite (Kaocha)
clojure -M:run      # serve on :3000
```

## Frontend conventions

- **Vanilla CSS, CUBE architecture** (cube.fyi), in order: Composition → Utilities → Base →
  Blocks (generic, not domain-specific) → Exceptions → app-specific tweaks last.
- **Design tokens only.** Spacing and colors are CSS custom properties in `tokens.css`
  (Utopia-style fluid scales + a YNAB-like palette). **Never hardcode a color or spacing
  value** after the token layer; reference `var(--…)`. Structural one-offs (`1px`, `0`,
  `100%`, grid tracks, the `40rem` breakpoint) are fine.
- **Class grouping** uses CUBE brackets: `class="[ stack ] [ card ]"`. **Exceptions are
  `data-*` attributes**, not classes (e.g. `[data-status="balanced|over|under"]`).
- **HTML is mobile-first, semantic, and progressive-enhancement-first.** The page must work
  with **no JS** — one server-rendered `<form>` that POSTs and reloads. JS only enhances
  (live recompute via a localhost round-trip to the engine — never reimplement engine logic
  in JS).
- **Web Components** follow the gomakethings boilerplate
  (https://gomakethings.com/snippets/boilerplates/web-component/): inline
  `customElements.define`, private `#fields`, a `connectedCallback` readiness check that
  delegates to `init()`, the `handleEvent` delegation pattern, and public methods for external
  callers. Components communicate **up** via bubbling `CustomEvent`s.

## Data & secrets

- `plan.edn` (your plan: income, pillar tags, target overrides) and `cache.edn` (the YNAB
  snapshot) are **git-ignored** — they're personal data. `.env` is git-ignored too; only
  `.env.example` is tracked. Never commit secrets or personal budget data.

## Conventions

- Namespaces are `ynab-planner.*`; files use `ynab_planner/` (underscore).
- Commit messages: conventional prefixes (`feat(engine):`, `fix(web):`, …).
- Design specs/plans under `docs/superpowers/` are intentionally untracked (ephemeral working
  docs) — don't commit them.
