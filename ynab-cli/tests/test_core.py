from __future__ import annotations

import pytest

from ynab_cli.core import decode_target, diff, evaluate, normalize, spread, units


def cat(**fields):
    return {"id": "c", "name": "C", "budgeted": 0, "activity": 0, "balance": 0} | fields


def snapshot(*categories, group="Hogar"):
    data = {"category_groups": [{"name": group, "categories": list(categories)}]}
    return normalize(data, plan_id="last-used", retrieved_at="2026-11-14T12:00:00Z")


def test_units_rounds_milliunits_to_the_nearest_peso():
    assert units(-25_000) == -25
    assert units(583_664_820) == 583_665
    assert units(1_499) == 1
    assert units(None) is None


def test_snapshot_flattens_categories_in_pesos():
    [rent] = snapshot(cat(name="Arriendo", budgeted=1_500_000_000, activity=-1_500_000_000))["categories"]
    assert (rent["group_name"], rent["assigned"], rent["activity"]) == ("Hogar", 1_500_000, -1_500_000)


def test_snapshot_drops_deleted_categories():
    categories = snapshot(cat(id="kept"), cat(id="gone", deleted=True))["categories"]
    assert [c["id"] for c in categories] == ["kept"]


def test_dated_target_keeps_ynab_progress_facts():
    target = decode_target(cat(goal_type="TBD", goal_target=7_800_000_000, goal_target_date="2026-09-01",
                               goal_under_funded=1_000_000_000, goal_overall_left=4_000_000_000,
                               goal_months_to_budget=4))
    assert target.items() >= {"type": "balance_by_date", "amount": 7_800_000, "date": "2026-09",
                              "underfunded": 1_000_000, "left": 4_000_000, "months_to_budget": 4}.items()


def test_no_goal_means_no_target():
    assert decode_target(cat()) is None


@pytest.mark.parametrize("cadence,frequency,expected", [
    (0, None, (None, None)),
    (1, 2, ("month", 2)),
    (2, None, ("week", 1)),
    (4, 7, ("month", 3)),
    (13, 1, ("year", 1)),
    (14, None, ("year", 2)),
])
def test_cadence_decodes_to_unit_and_count(cadence, frequency, expected):
    t = decode_target(cat(goal_type="NEED", goal_target=300_000_000,
                          goal_cadence=cadence, goal_cadence_frequency=frequency))
    assert (t["cadence"], t["every"]) == expected


def test_unknown_target_keeps_raw_fields():
    t = decode_target(cat(goal_type="NEW_KIND", goal_target=1_000, goal_whatever="x"))
    assert t == {"type": "unknown", "raw": {"goal_type": "NEW_KIND", "goal_target": 1_000, "goal_whatever": "x"}}


def test_spread_divides_what_is_missing_over_inclusive_months():
    assert spread({"amount": 1_200_000, "available": 200_000, "from": "2027-01", "to": "2027-05"}) == {
        "remaining": 1_000_000, "periods": 5, "per_period": 200_000}


def test_spread_rounds_half_up():
    assert spread({"amount": 1_000_000, "periods": 3})["per_period"] == 333_333
    assert spread({"amount": 5, "periods": 2})["per_period"] == 3


def test_spread_of_a_funded_target_is_zero():
    assert spread({"amount": 100, "available": 500, "periods": 2})["per_period"] == 0


@pytest.mark.parametrize("payload", [
    {"amount": 100, "periods": 0},
    {"amount": -1, "periods": 1},
    {"amount": 100, "from": "2027-06", "to": "2027-01"},
    {"amount": 100},
    {"amount": 100, "periods": 2, "from": "2027-01", "to": "2027-02"},
])
def test_spread_rejects_bad_input(payload):
    with pytest.raises(ValueError):
        spread(payload)


def test_evaluate_compares_with_income_and_groups_by_pillar():
    result = evaluate({"income": 1_000_000, "items": [
        {"category_id": "rent", "monthly_amount": 600_000, "pillar": "needs"},
        {"category_id": "saving", "monthly_amount": 300_000, "pillar": "savings"},
        {"category_id": "misc", "monthly_amount": 200_000},
    ]})
    assert (result["total"], result["difference"], result["status"]) == (1_100_000, -100_000, "over")
    assert result["by_pillar"]["needs"] == {"amount": 600_000, "pct": 60.0}
    assert result["unclassified"] == 200_000


@pytest.mark.parametrize("items", [
    [{"category_id": "a", "monthly_amount": 1}, {"category_id": "a", "monthly_amount": 2}],
    [{"category_id": "a", "monthly_amount": True}],
    [{"category_id": "a", "monthly_amount": -1}],
    [{"monthly_amount": 1}],
])
def test_evaluate_rejects_bad_items(items):
    with pytest.raises(ValueError):
        evaluate({"income": 0, "items": items})


def test_diff_reports_added_removed_and_changed_fields():
    before = [{"category_id": "rent", "monthly_amount": 600_000, "pillar": "needs"},
              {"category_id": "old", "monthly_amount": 50_000}]
    after = [{"category_id": "new", "monthly_amount": 25_000},
             {"category_id": "rent", "monthly_amount": 700_000}]
    assert diff(before, after) == {
        "added": [{"category_id": "new", "monthly_amount": 25_000}],
        "removed": [{"category_id": "old", "monthly_amount": 50_000}],
        "changed": [{"category_id": "rent", "changes": {
            "monthly_amount": {"before": 600_000, "after": 700_000},
            "pillar": {"before": "needs", "after": None}}}],
    }


def test_diff_ignores_order():
    items = [{"category_id": "a", "monthly_amount": 1}, {"category_id": "b", "monthly_amount": 2}]
    assert diff(items, list(reversed(items))) == {"added": [], "removed": [], "changed": []}
