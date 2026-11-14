from __future__ import annotations

from datetime import date
from typing import Any

TARGET_TYPES = {
    "MF": "monthly_funding",
    "TBD": "balance_by_date",
    "TB": "target_balance",
    "NEED": "needed_for_spending",
    "DEBT": "debt_payment",
}


def divide_rounding_half_up(n: int, d: int) -> int:
    return (2 * n + d) // (2 * d)


def units(milliunits: int | None) -> int | None:
    return None if milliunits is None else divide_rounding_half_up(milliunits, 1000)


def _cadence(cadence: int | None, frequency: int | None) -> tuple[str | None, int | None]:
    n = frequency or 1
    if cadence in (None, 0):
        return None, None
    if cadence in (1, 2, 13):
        return {1: "month", 2: "week", 13: "year"}[cadence], n
    if 3 <= cadence <= 12:
        return "month", cadence - 1
    if cadence == 14:
        return "year", 2
    return "unknown", cadence


def decode_target(category: dict[str, Any]) -> dict[str, Any] | None:
    kind = category.get("goal_type")
    if kind is None:
        return None
    if kind not in TARGET_TYPES:
        return {"type": "unknown", "raw": {k: v for k, v in category.items() if k.startswith("goal_")}}
    cadence, every = _cadence(category.get("goal_cadence"), category.get("goal_cadence_frequency"))
    target_date = category.get("goal_target_date")
    return {
        "type": TARGET_TYPES[kind],
        "amount": units(category.get("goal_target")),
        "date": str(target_date)[:7] if target_date else None,
        "cadence": cadence,
        "every": every,
        "underfunded": units(category.get("goal_under_funded")),
        "left": units(category.get("goal_overall_left")),
        "months_to_budget": category.get("goal_months_to_budget"),
        "snoozed": category.get("goal_snoozed_at") is not None,
        "whole_amount": category.get("goal_needs_whole_amount"),
    }


def normalize(categories_data: dict[str, Any], *, plan_id: str, retrieved_at: str) -> dict[str, Any]:
    categories = [
        {
            "id": c["id"],
            "name": c["name"],
            "group_name": group["name"],
            "hidden": c.get("hidden", False),
            "internal": c.get("internal", False),
            "assigned": units(c.get("budgeted", 0)),
            "activity": units(c.get("activity", 0)),
            "available": units(c.get("balance", 0)),
            "target": decode_target(c),
        }
        for group in categories_data["category_groups"]
        if not group.get("deleted")
        for c in group["categories"]
        if not c.get("deleted")
    ]
    return {"retrieved_at": retrieved_at, "plan_id": plan_id, "categories": categories}


def _money(value: Any, name: str) -> int:
    if type(value) is not int or value < 0:
        raise ValueError(f"'{name}' debe ser un entero no negativo.")
    return value


def _month(value: Any) -> date:
    if not isinstance(value, str) or len(value) != 7:
        raise ValueError(f"Mes inválido {value!r}: usa YYYY-MM.")
    return date.fromisoformat(value + "-01")


def spread(payload: dict[str, Any]) -> dict[str, int]:
    amount = _money(payload.get("amount"), "amount")
    available = payload.get("available", 0)
    if type(available) is not int:
        raise ValueError("'available' debe ser un entero.")
    if ("periods" in payload) == ("from" in payload or "to" in payload):
        raise ValueError("Usa 'periods' o 'from' + 'to' (uno de los dos).")
    if "periods" in payload:
        periods = payload["periods"]
    else:
        start, end = _month(payload.get("from")), _month(payload.get("to"))
        periods = (end.year - start.year) * 12 + end.month - start.month + 1
    if type(periods) is not int or periods < 1:
        raise ValueError("Los periodos deben ser al menos 1 ('to' no puede ser anterior a 'from').")
    remaining = max(0, amount - available)
    return {"remaining": remaining, "periods": periods, "per_period": divide_rounding_half_up(remaining, periods)}


def _index(items: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for item in items:
        cid = item.get("category_id")
        if not isinstance(cid, str) or not cid:
            raise ValueError("Cada ítem necesita un 'category_id'.")
        if cid in indexed:
            raise ValueError(f"category_id repetido: {cid}")
        indexed[cid] = item
    return indexed


def _items(value: Any) -> list[dict[str, Any]]:
    return value["items"] if isinstance(value, dict) else value


def evaluate(plan: dict[str, Any]) -> dict[str, Any]:
    income = _money(plan.get("income"), "income")
    items = _index(_items(plan)).values()
    amounts = [_money(i.get("monthly_amount"), "monthly_amount") for i in items]
    total = sum(amounts)
    by_pillar: dict[str, int] = {}
    unclassified = 0
    for item, amount in zip(items, amounts):
        if item.get("pillar"):
            by_pillar[item["pillar"]] = by_pillar.get(item["pillar"], 0) + amount
        else:
            unclassified += amount

    def pct(amount: int) -> float:
        return round(100 * amount / income, 1) if income else 0.0

    return {
        "income": income,
        "total": total,
        "difference": income - total,
        "status": "balanced" if total == income else "over" if total > income else "under",
        "by_pillar": {p: {"amount": a, "pct": pct(a)} for p, a in sorted(by_pillar.items())},
        "unclassified": unclassified,
    }


def diff(before: Any, after: Any) -> dict[str, list[dict[str, Any]]]:
    old, new = _index(_items(before)), _index(_items(after))
    changed = []
    for cid in sorted(old.keys() & new.keys()):
        fields = sorted((old[cid].keys() | new[cid].keys()) - {"category_id"})
        changes = {f: {"before": old[cid].get(f), "after": new[cid].get(f)}
                   for f in fields if old[cid].get(f) != new[cid].get(f)}
        if changes:
            changed.append({"category_id": cid, "changes": changes})
    return {
        "added": [new[cid] for cid in sorted(new.keys() - old.keys())],
        "removed": [old[cid] for cid in sorted(old.keys() - new.keys())],
        "changed": changed,
    }
