"""Command-line interface for inspecting YNAB without writing to it."""

from __future__ import annotations

import argparse
from datetime import date
import json
import sys
from uuid import UUID

import ynab

from ynab_read.client import CredentialError, query


COMMANDS = ("plans", "settings", "accounts", "categories", "months", "month", "transactions", "scheduled")


def plan_id(value: str) -> str:
    if value == "last-used":
        return value
    try:
        UUID(value)
    except ValueError:
        raise argparse.ArgumentTypeError("Usa un UUID o 'last-used'.") from None
    return value


def iso_date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise argparse.ArgumentTypeError("Usa YYYY-MM-DD.") from None


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Explora YNAB usando el SDK oficial (solo consultas).")
    p.add_argument("command", choices=COMMANDS)
    p.add_argument("--plan", type=plan_id, default="last-used", help="UUID o last-used (predeterminado)")
    p.add_argument("--month", help="Mes YYYY-MM, requerido para 'month'")
    p.add_argument("--since", type=iso_date, help="Desde YYYY-MM-DD (transacciones)")
    p.add_argument("--until", type=iso_date, help="Hasta YYYY-MM-DD (transacciones)")
    p.add_argument("--limit", type=int, default=50, help="Mostrar máximo 1-200 transacciones; la API puede descargar más")
    return p


def main(argv: list[str] | None = None) -> int:
    p = parser()
    args = p.parse_args(argv)
    if args.command == "month":
        if not args.month:
            p.error("'month' requiere --month YYYY-MM")
        try:
            if len(args.month) != 7 or args.month[4] != "-":
                raise ValueError
            month = date.fromisoformat(args.month + "-01")
        except ValueError:
            p.error("Mes inválido: usa YYYY-MM")
    else:
        month = None
        if args.month is not None:
            p.error("--month solo aplica a 'month'")
    if args.since or args.until:
        if args.command != "transactions":
            p.error("--since y --until solo aplican a 'transactions'")
        if args.since and args.until and args.since > args.until:
            p.error("--since debe ser anterior o igual a --until")
    if not 1 <= args.limit <= 200:
        p.error("--limit debe estar entre 1 y 200")
    if args.command != "transactions" and args.limit != 50:
        p.error("--limit solo aplica a 'transactions'")
    try:
        data = query(args.command, args.plan, month=month, since=args.since, until=args.until, limit=args.limit)
    except CredentialError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1
    except ynab.ApiException as exc:
        messages = {401: "token inválido o revocado", 403: "acceso denegado", 404: "recurso no encontrado", 429: "límite de consultas alcanzado"}
        print(f"YNAB HTTP {exc.status}: {messages.get(exc.status, 'error de API')}", file=sys.stderr)
        return 1
    print(json.dumps(data, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
