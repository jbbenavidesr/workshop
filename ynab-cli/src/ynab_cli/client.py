"""Thin, read-only boundary around YNAB's official Python SDK."""

from __future__ import annotations

import os
from pathlib import Path
import stat

import ynab


class CredentialError(ValueError):
    """Safe-to-display credential configuration error (never includes token)."""


def read_token(token_file: Path | None = None) -> str:
    """Use YNAB_TOKEN, otherwise read a local 0600 token file."""
    token = os.environ.get("YNAB_TOKEN")
    if token:
        if token != token.strip() or "\n" in token or "\r" in token:
            raise CredentialError("YNAB_TOKEN debe contener una sola línea sin espacios alrededor.")
        return token
    path = token_file or Path(os.environ.get("YNAB_TOKEN_FILE", "~/.config/ynab-cli/token")).expanduser()
    try:
        metadata = path.lstat()
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_mode & 0o077:
            raise CredentialError(f"El archivo {path} debe ser regular y tener permisos 0600.")
        token = path.read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        raise CredentialError(
            f"Falta el token: guarda una línea en {path} (chmod 600) o configura YNAB_TOKEN. "
            "No lo pegues en el chat ni lo guardes en Git."
        ) from None
    if not token or "\n" in token or "\r" in token:
        raise CredentialError("El archivo del token debe contener una sola línea no vacía.")
    return token


def query(command: str, plan: str = "last-used", *, month=None, since=None, until=None, limit: int = 50,
          token_file: Path | None = None) -> dict:
    """Return JSON-compatible SDK response data; never expose a write method."""
    configuration = ynab.Configuration(access_token=read_token(token_file))
    with ynab.ApiClient(configuration) as client:
        if command == "plans":
            result = ynab.PlansApi(client).get_plans(_request_timeout=25)
        elif command == "settings":
            result = ynab.PlansApi(client).get_plan_settings_by_id(plan, _request_timeout=25)
        elif command == "accounts":
            result = ynab.AccountsApi(client).get_accounts(plan, _request_timeout=25)
        elif command == "categories":
            result = ynab.CategoriesApi(client).get_categories(plan, _request_timeout=25)
        elif command == "months":
            result = ynab.MonthsApi(client).get_plan_months(plan, _request_timeout=25)
        elif command == "month":
            result = ynab.MonthsApi(client).get_plan_month(plan, month, _request_timeout=25)
        elif command == "transactions":
            result = ynab.TransactionsApi(client).get_transactions(
                plan, since_date=since, until_date=until, _request_timeout=25
            )
        elif command == "scheduled":
            result = ynab.ScheduledTransactionsApi(client).get_scheduled_transactions(plan, _request_timeout=25)
        else:
            raise ValueError("Consulta no admitida")
        data = result.data.model_dump(mode="json", exclude_none=True)
        if command == "transactions":
            transactions = data["transactions"]
            data["total_matching_count"] = len(transactions)
            data["transactions"] = transactions[:limit]
            data["returned_count"] = len(data["transactions"])
        return data
