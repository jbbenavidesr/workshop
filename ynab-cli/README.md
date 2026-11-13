# YNAB CLI

Small, reusable **read-only** CLI for exploring a YNAB plan. Uses the
[official YNAB Python SDK](https://github.com/ynab/ynab-sdk-python), not a
Hermes-specific integration. It sits beside `ynab-planner/`, which remains a
separate browser-based planning app.

## Setup

Requires Python 3.10+ and a YNAB personal access token (Account Settings →
Developer Settings → New Token). The token grants **write access** at the YNAB
level; only this CLI is read-only. Never commit the token or budget data to this
public repository.

```bash
cd ynab-cli
python3 -m venv .venv
.venv/bin/python -m pip install -e '.[test]'
```

For a local credential, create `~/.config/ynab-read/token` containing only the
token, permissions `0600`. Alternatively set `YNAB_TOKEN` or `YNAB_TOKEN_FILE`
to a path outside the repository. Do not paste the token into chat, shell
arguments, or version-controlled `.env` files.

```bash
.venv/bin/ynab-read plans
.venv/bin/ynab-read settings
.venv/bin/ynab-read accounts
.venv/bin/ynab-read categories
.venv/bin/ynab-read month --month 2026-11
.venv/bin/ynab-read transactions --since 2026-11-01 --limit 50
.venv/bin/ynab-read --help
.venv/bin/python -m pytest
```

`--plan UUID` chooses a different plan; default is `last-used`. Other commands:
`months`, `scheduled`. Output is JSON so other programs and agents can consume
it. Transaction limits cap **displayed** results, not the data fetched from
YNAB. The SDK returns amounts in **milliunits**; divide by 1000 to get the
plan's currency unit. `settings` provides the plan's currency configuration.
No data is cached or stored by this CLI. For live access, a token must be
installed on the machine executing it.
