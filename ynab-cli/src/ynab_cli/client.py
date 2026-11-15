"""Thin, read-only boundary around YNAB's official Python SDK."""

from __future__ import annotations

from datetime import date
import os
from pathlib import Path

import ynab

TIMEOUT = 25
HTTP_MESSAGES = {
    401: "token inválido o revocado",
    403: "acceso denegado",
    404: "recurso no encontrado",
    429: "límite de consultas alcanzado",
}


class YnabError(Exception):
    """Safe-to-display error (never includes the token)."""


def read_token() -> str:
    """Use YNAB_TOKEN, otherwise the file at YNAB_TOKEN_FILE."""
    token = os.environ.get("YNAB_TOKEN")
    if token is None:
        path = Path(os.environ.get("YNAB_TOKEN_FILE", "~/.config/ynab-cli/token")).expanduser()
        try:
            token = path.read_text(encoding="utf-8")
        except FileNotFoundError:
            raise YnabError(f"Falta el token: guarda una línea en {path} o configura YNAB_TOKEN.") from None
    token = token.strip()
    if not token or "\n" in token:
        raise YnabError("El token debe ser una sola línea no vacía.")
    return token


def _get(call) -> dict:
    """Run one SDK call and return its data as JSON-compatible dict."""
    with ynab.ApiClient(ynab.Configuration(access_token=read_token())) as api:
        try:
            result = call(api)
        except ynab.ApiException as exc:
            raise YnabError(f"YNAB HTTP {exc.status}: {HTTP_MESSAGES.get(exc.status, 'error de API')}") from None
    return result.data.model_dump(mode="json", exclude_none=True)


def plans() -> dict:
    return _get(lambda c: ynab.PlansApi(c).get_plans(_request_timeout=TIMEOUT))


def settings(plan: str) -> dict:
    return _get(lambda c: ynab.PlansApi(c).get_plan_settings_by_id(plan, _request_timeout=TIMEOUT))


def accounts(plan: str) -> dict:
    return _get(lambda c: ynab.AccountsApi(c).get_accounts(plan, _request_timeout=TIMEOUT))


def categories(plan: str) -> dict:
    return _get(lambda c: ynab.CategoriesApi(c).get_categories(plan, _request_timeout=TIMEOUT))


def months(plan: str) -> dict:
    return _get(lambda c: ynab.MonthsApi(c).get_plan_months(plan, _request_timeout=TIMEOUT))


def month(plan: str, month: date) -> dict:
    return _get(lambda c: ynab.MonthsApi(c).get_plan_month(plan, month, _request_timeout=TIMEOUT))


def transactions(plan: str, since: date | None = None, until: date | None = None) -> dict:
    return _get(lambda c: ynab.TransactionsApi(c).get_transactions(
        plan, since_date=since, until_date=until, _request_timeout=TIMEOUT))


def scheduled(plan: str) -> dict:
    return _get(lambda c: ynab.ScheduledTransactionsApi(c).get_scheduled_transactions(plan, _request_timeout=TIMEOUT))
