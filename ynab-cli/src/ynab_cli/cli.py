"""Command-line interface for exploring YNAB; currently read-only."""

from __future__ import annotations

from datetime import date
import json
from typing import Annotated
from uuid import UUID

import typer
import ynab

from ynab_cli.client import CredentialError, query

app = typer.Typer(help="Explora YNAB usando el SDK oficial (por ahora, solo consultas).", no_args_is_help=True)


def validate_plan(value: str) -> str:
    if value == "last-used":
        return value
    try:
        UUID(value)
    except ValueError:
        raise typer.BadParameter("Usa un UUID o 'last-used'.") from None
    return value


def parse_month(value: str) -> date:
    try:
        if len(value) != 7 or value[4] != "-":
            raise ValueError
        return date.fromisoformat(value + "-01")
    except ValueError:
        raise typer.BadParameter("Mes inválido: usa YYYY-MM.") from None


Plan = Annotated[str, typer.Option("--plan", callback=validate_plan, help="UUID o last-used (predeterminado)")]


def show(
    command: str, plan: str = "last-used", *, month: date | None = None,
    since: date | None = None, until: date | None = None, limit: int = 50,
) -> None:
    try:
        data = query(command, plan, month=month, since=since, until=until, limit=limit)
    except CredentialError as exc:
        typer.echo(f"Error: {exc}", err=True)
        raise typer.Exit(1) from None
    except ynab.ApiException as exc:
        messages = {401: "token inválido o revocado", 403: "acceso denegado", 404: "recurso no encontrado", 429: "límite de consultas alcanzado"}
        typer.echo(f"YNAB HTTP {exc.status}: {messages.get(exc.status, 'error de API')}", err=True)
        raise typer.Exit(1) from None
    typer.echo(json.dumps(data, ensure_ascii=False, indent=2))


@app.command()
def plans() -> None:
    """Lista los planes disponibles."""
    show("plans")


@app.command()
def settings(plan: Plan = "last-used") -> None:
    """Consulta ajustes, incluida la moneda del plan."""
    show("settings", plan)


@app.command()
def accounts(plan: Plan = "last-used") -> None:
    """Lista las cuentas del plan."""
    show("accounts", plan)


@app.command()
def categories(plan: Plan = "last-used") -> None:
    """Lista los grupos y categorías."""
    show("categories", plan)


@app.command()
def months(plan: Plan = "last-used") -> None:
    """Lista los meses del plan."""
    show("months", plan)


@app.command()
def month(
    month: Annotated[str, typer.Option("--month", help="Mes YYYY-MM")],
    plan: Plan = "last-used",
) -> None:
    """Consulta un mes concreto y sus categorías."""
    show("month", plan, month=parse_month(month))


@app.command()
def transactions(
    plan: Plan = "last-used",
    since: Annotated[date | None, typer.Option("--since", help="Desde YYYY-MM-DD")] = None,
    until: Annotated[date | None, typer.Option("--until", help="Hasta YYYY-MM-DD")] = None,
    limit: Annotated[int, typer.Option("--limit", min=1, max=200, help="Máximo mostrado (la API puede descargar más)")] = 50,
) -> None:
    """Consulta transacciones del plan."""
    if since and until and since > until:
        raise typer.BadParameter("--since debe ser anterior o igual a --until", param_hint="--since")
    show("transactions", plan, since=since, until=until, limit=limit)


@app.command()
def scheduled(plan: Plan = "last-used") -> None:
    """Consulta transacciones programadas."""
    show("scheduled", plan)


def main() -> None:
    app()


if __name__ == "__main__":
    main()
