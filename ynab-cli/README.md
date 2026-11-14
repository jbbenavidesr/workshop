# YNAB CLI

Read-only YNAB toolkit for financial-planning conversations with an assistant (Hermes).
YNAB is the source of truth; this tool brings faithful facts and deterministic arithmetic,
the assistant brings judgment. Uses the
[official YNAB Python SDK](https://github.com/ynab/ynab-sdk-python) and
[Typer](https://typer.tiangolo.com/).

## Principles

- Simple, generic tools first. Codify a new tool only when a pattern repeats in real use.
- YNAB's numbers answer "how are we now"; local functions answer "what if".
- Facts, not recommendations. Pillars describe composition; they don't set priorities.
- Pure functions in `core.py`; network and files stay in `client.py` / `cli.py`.
- Read-only. No command writes to YNAB.

## Setup

Requires Python 3.10+, [uv](https://docs.astral.sh/uv/getting-started/installation/),
and a YNAB personal access token (Account Settings → Developer Settings → New
Token). The token grants **write access** at the YNAB level; only this CLI is
read-only. Never commit the token or budget data to this public repository.

```bash
cd ynab-cli
uv sync --locked
uv run --locked pytest
```

Put the token in `~/.config/ynab-cli/token` (one line, `chmod 600`), or set `YNAB_TOKEN`
or `YNAB_TOKEN_FILE`. Never pass it as an argument or paste it into chat.

All commands print JSON. `--plan UUID` picks a plan; the default is `last-used`.

## Generic reads

Thin wrappers over the API, raw SDK output in **milliunits** (÷ 1000 = pesos):
`plans`, `settings`, `accounts`, `categories`, `months`, `month --month YYYY-MM`,
`transactions [--since --until --limit]`, `scheduled`.

## Analysis tools

Amounts are whole currency units (COP), rounded to the peso. JSON inputs are a file path or `-` / nothing for stdin.

**`snapshot`** — categories and targets, normalized. Amounts are for the current month.

```bash
uv run --locked ynab-cli snapshot > ~/ynab-snapshot.json   # personal data: keep outside the repo
```

```json
{"retrieved_at": "2026-11-14T12:00:00Z", "plan_id": "last-used", "categories": [{
  "id": "…", "name": "Seguro", "group_name": "Hogar", "hidden": false, "internal": false,
  "assigned": 0, "activity": 0, "available": 3800000,
  "target": {"type": "balance_by_date", "amount": 7800000, "date": "2026-09",
             "cadence": null, "every": null, "underfunded": 1000000, "left": 4000000,
             "months_to_budget": 4, "snoozed": false, "whole_amount": null}}]}
```

`target` is `null` without a goal. Types: `monthly_funding`, `balance_by_date`,
`target_balance`, `needed_for_spending`, `debt_payment`; anything else is `unknown` with the raw
`goal_*` fields. `cadence` is `month|week|year` and `every` how many of them, e.g. "every 2 months"
is `month` / `2`.

**`spread`** — `max(0, amount − available)` over N periods, rounded half-up.

```bash
echo '{"amount": 1200000, "available": 200000, "from": "2027-01", "to": "2027-05"}' | uv run --locked ynab-cli spread
# {"remaining": 1000000, "periods": 5, "per_period": 200000}
echo '{"amount": 5200000, "periods": 12}' | uv run --locked ynab-cli spread   # weekly 100k → amount × 52 over 12
```

Use either `periods` or `from` + `to` (inclusive months). `available` defaults to 0.

**`evaluate`** — plan totals against an income.

```bash
uv run --locked ynab-cli evaluate plan.json
```

```json
{"income": 8000000, "items": [
  {"category_id": "…", "monthly_amount": 200000, "pillar": "savings"}]}
```

Returns `total`, `difference` (income − total), `status` (`balanced|over|under`),
`by_pillar` (`amount`, `pct` of income) and `unclassified`. Items may carry extra fields such as `name`.

**`diff`** — `added`, `removed` and per-field `changed` between two plans or item lists, by
`category_id`.

```bash
uv run --locked ynab-cli diff current.json proposal.json
```
