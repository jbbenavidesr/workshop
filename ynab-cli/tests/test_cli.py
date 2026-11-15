from __future__ import annotations

import json
from unittest.mock import patch

import pytest
from typer.testing import CliRunner

from ynab_cli import cli, client

runner = CliRunner()


def test_missing_token_explains_how_to_set_it(tmp_path, monkeypatch):
    monkeypatch.delenv("YNAB_TOKEN", raising=False)
    monkeypatch.setenv("YNAB_TOKEN_FILE", str(tmp_path / "missing"))
    result = runner.invoke(cli.app, ["plans"])
    assert result.exit_code == 1
    assert "Falta el token" in result.output


def test_token_from_file_is_stripped(tmp_path, monkeypatch):
    monkeypatch.delenv("YNAB_TOKEN", raising=False)
    path = tmp_path / "token"
    path.write_text("dummy\n")
    monkeypatch.setenv("YNAB_TOKEN_FILE", str(path))
    assert client.read_token() == "dummy"


def test_token_from_environment_wins(tmp_path, monkeypatch):
    monkeypatch.setenv("YNAB_TOKEN_FILE", str(tmp_path / "missing"))
    monkeypatch.setenv("YNAB_TOKEN", "dummy")
    assert client.read_token() == "dummy"
    monkeypatch.setenv("YNAB_TOKEN", "two\nlines")
    with pytest.raises(client.YnabError):
        client.read_token()


@pytest.mark.parametrize("argv", [
    ["month", "--month", "2026-13"],
    ["accounts", "--plan", "../evil"],
    ["transactions", "--since", "2026-11-02", "--until", "2026-11-01"],
])
def test_invalid_arguments_never_reach_ynab(argv):
    with patch.object(client, "_get") as get:
        result = runner.invoke(cli.app, argv)
    assert result.exit_code == 2
    get.assert_not_called()


def test_snapshot_prints_normalized_categories():
    categories = {"category_groups": [{"name": "Hogar", "categories": [
        {"id": "c1", "name": "Arriendo", "budgeted": 1_500_000_000, "activity": 0, "balance": 0}]}]}
    with patch.object(client, "_get", return_value=categories):
        result = runner.invoke(cli.app, ["snapshot"])
    snap = json.loads(result.stdout)
    assert snap["retrieved_at"].endswith("Z")
    assert snap["categories"][0]["assigned"] == 1_500_000


def test_spread_reads_stdin():
    result = runner.invoke(cli.app, ["spread"], input='{"amount": 1200000, "periods": 6}')
    assert json.loads(result.stdout)["per_period"] == 200_000


def test_evaluate_reads_a_file(tmp_path):
    plan = tmp_path / "plan.json"
    plan.write_text('{"income": 100, "items": [{"category_id": "a", "monthly_amount": 80}]}')
    result = runner.invoke(cli.app, ["evaluate", str(plan)])
    assert json.loads(result.stdout)["status"] == "under"


def test_diff_compares_two_files(tmp_path):
    before, after = tmp_path / "before.json", tmp_path / "after.json"
    before.write_text('[{"category_id": "a", "monthly_amount": 100}]')
    after.write_text('[{"category_id": "a", "monthly_amount": 80}]')
    result = runner.invoke(cli.app, ["diff", str(before), str(after)])
    assert json.loads(result.stdout)["changed"] == [
        {"category_id": "a", "changes": {"monthly_amount": {"before": 100, "after": 80}}}]


def test_transactions_are_limited():
    data = {"transactions": [{"id": str(i)} for i in range(3)]}
    with patch.object(client, "_get", return_value=data):
        result = runner.invoke(cli.app, ["transactions", "--limit", "2"])
    out = json.loads(result.stdout)
    assert (len(out["transactions"]), out["total_matching_count"], out["returned_count"]) == (2, 3, 2)


@pytest.mark.parametrize("argv,stdin", [
    (["evaluate"], "{not json"),
    (["evaluate"], '[{"category_id": "a", "monthly_amount": 1}]'),
    (["evaluate"], '{"income": 1}'),
    (["spread"], '{"amount": 1, "from": "2027/01", "to": "2027-02"}'),
])
def test_bad_input_is_a_short_error_not_a_traceback(argv, stdin):
    result = runner.invoke(cli.app, argv, input=stdin)
    assert result.exit_code == 1
    assert result.stderr.startswith("Error:")
    assert "Traceback" not in result.output
