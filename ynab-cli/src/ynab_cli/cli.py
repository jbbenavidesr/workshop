"""Read-only YNAB command-line interface: parse, validate, call, emit."""

from __future__ import annotations

from datetime import date, datetime, timezone
import json
from typing import Annotated
from uuid import UUID

import typer

from ynab_cli import client, core

app = typer.Typer(help="Explora YNAB usando el SDK oficial (por ahora, solo consultas).", no_args_is_help=True)


def validate_plan(value: str) -> str:
    if value != "last-used":
        try:
            UUID(value)
        except ValueError:
            raise typer.BadParameter("Usa un UUID o 'last-used'.") from None
    return value


Plan = Annotated[str, typer.Option("--plan", callback=validate_plan, help="UUID o last-used (predeterminado)")]
JsonIn = Annotated[typer.FileText, typer.Argument(help="Archivo JSON o - para stdin")]


def run(compute) -> None:
    try:
        typer.echo(json.dumps(compute(), ensure_ascii=False, indent=2))
    except (client.YnabError, ValueError) as exc:
        typer.echo(f"Error: {exc}", err=True)
        raise typer.Exit(1) from None


@app.command()
def plans() -> None:
    """Lista los planes disponibles."""
    run(client.plans)


@app.command()
def settings(plan: Plan = "last-used") -> None:
    """Consulta ajustes, incluida la moneda del plan."""
    run(lambda: client.settings(plan))


@app.command()
def accounts(plan: Plan = "last-used") -> None:
    """Lista las cuentas del plan."""
    run(lambda: client.accounts(plan))


@app.command()
def categories(plan: Plan = "last-used") -> None:
    """Lista los grupos y categorías."""
    run(lambda: client.categories(plan))


@app.command()
def months(plan: Plan = "last-used") -> None:
    """Lista los meses del plan."""
    run(lambda: client.months(plan))


@app.command()
def month(
    month: Annotated[datetime, typer.Option("--month", formats=["%Y-%m"], help="Mes YYYY-MM")],
    plan: Plan = "last-used",
) -> None:
    """Consulta un mes concreto y sus categorías."""
    run(lambda: client.month(plan, month.date()))


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
    run(lambda: core.take(client.transactions(plan, since, until), "transactions", limit))


@app.command()
def scheduled(plan: Plan = "last-used") -> None:
    """Consulta transacciones programadas."""
    run(lambda: client.scheduled(plan))


@app.command()
def snapshot(plan: Plan = "last-used") -> None:
    """Foto normalizada de categorías y targets (pesos enteros, mes actual)."""
    retrieved_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    run(lambda: core.normalize(client.categories(plan), plan_id=plan, retrieved_at=retrieved_at))


@app.command()
def spread(source: JsonIn = "-") -> None:
    """Reparte un monto: {amount, available?, periods} o {amount, available?, from, to}."""
    run(lambda: core.spread(json.load(source)))


@app.command()
def evaluate(source: JsonIn = "-") -> None:
    """Totales de un plan {income, items:[{category_id, monthly_amount, pillar?}]} contra el ingreso."""
    run(lambda: core.evaluate(json.load(source)))


@app.command()
def diff(before: JsonIn, after: JsonIn) -> None:
    """Cambios entre dos planes o listas de ítems, por category_id."""
    run(lambda: core.diff(json.load(before), json.load(after)))
