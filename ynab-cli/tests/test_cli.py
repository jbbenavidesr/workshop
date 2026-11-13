from __future__ import annotations

from datetime import date
from unittest.mock import MagicMock, patch

import pytest
from typer.testing import CliRunner

from ynab_cli import cli, client

runner = CliRunner()


def test_no_token_reports_instructions(tmp_path, monkeypatch):
    monkeypatch.delenv("YNAB_TOKEN", raising=False)
    monkeypatch.setenv("YNAB_TOKEN_FILE", str(tmp_path / "missing"))
    result = runner.invoke(cli.app, ["plans"])
    assert result.exit_code == 1
    assert "Falta el token" in result.output


def test_token_file_rejects_insecure_permissions_and_symlinks(tmp_path, monkeypatch):
    monkeypatch.delenv("YNAB_TOKEN", raising=False)
    path = tmp_path / "token"
    path.write_text("dummy\n")
    path.chmod(0o644)
    with pytest.raises(client.CredentialError):
        client.read_token(path)
    path.chmod(0o600)
    assert client.read_token(path) == "dummy"
    symlink = tmp_path / "link"
    symlink.symlink_to(path)
    with pytest.raises(client.CredentialError):
        client.read_token(symlink)


def test_token_environment_override(tmp_path, monkeypatch):
    monkeypatch.setenv("YNAB_TOKEN", "dummy")
    assert client.read_token(tmp_path / "missing") == "dummy"
    monkeypatch.setenv("YNAB_TOKEN", "two\nlines")
    with pytest.raises(client.CredentialError):
        client.read_token()


@pytest.mark.parametrize("argv", [
    ["month"], ["month", "--month", "2026-13"], ["accounts", "--month", "2026-11"],
    ["transactions", "--limit", "0"], ["transactions", "--limit", "201"],
    ["transactions", "--since", "2026-11-02", "--until", "2026-11-01"],
    ["accounts", "--plan", "../evil"], ["plans", "--since", "2026-11-01"],
])
def test_invalid_arguments_never_call_api(argv):
    with patch.object(cli, "query") as query:
        result = runner.invoke(cli.app, argv)
    assert result.exit_code == 2, result.output
    query.assert_not_called()


@pytest.mark.parametrize("command,api_class,method", [
    ("plans", "PlansApi", "get_plans"),
    ("settings", "PlansApi", "get_plan_settings_by_id"),
    ("accounts", "AccountsApi", "get_accounts"),
    ("categories", "CategoriesApi", "get_categories"),
    ("months", "MonthsApi", "get_plan_months"),
    ("month", "MonthsApi", "get_plan_month"),
    ("transactions", "TransactionsApi", "get_transactions"),
    ("scheduled", "ScheduledTransactionsApi", "get_scheduled_transactions"),
])
def test_only_sdk_get_methods_are_called(command, api_class, method, monkeypatch):
    monkeypatch.setenv("YNAB_TOKEN", "dummy")
    response = MagicMock()
    response.data.model_dump.return_value = {"transactions": [1, 2, 3]} if command == "transactions" else {"ok": True}
    sdk_api = MagicMock()
    getattr(sdk_api, method).return_value = response
    with patch.object(client.ynab, "ApiClient") as api_client, patch.object(client.ynab, api_class, return_value=sdk_api):
        api_client.return_value.__enter__.return_value = MagicMock()
        output = client.query(command, month=date(2026, 11, 1), since=date(2026, 11, 1), limit=2)
    getattr(sdk_api, method).assert_called_once()
    if command == "transactions":
        assert output["transactions"] == [1, 2]
        assert output["total_matching_count"] == 3
    else:
        assert output == {"ok": True}


def test_cli_json_output():
    with patch.object(cli, "query", return_value={"plans": []}):
        result = runner.invoke(cli.app, ["plans"])
    assert result.exit_code == 0, result.output
    assert '"plans": []' in result.output


def test_each_command_routes_to_client():
    commands = [
        (["plans"], "plans", {}),
        (["settings"], "settings", {}),
        (["accounts"], "accounts", {}),
        (["categories"], "categories", {}),
        (["months"], "months", {}),
        (["month", "--month", "2026-11"], "month", {"month": date(2026, 11, 1)}),
        (["transactions", "--since", "2026-11-01"], "transactions", {"since": date(2026, 11, 1)}),
        (["scheduled"], "scheduled", {}),
    ]
    for argv, expected, options in commands:
        with patch.object(cli, "query", return_value={"ok": True}) as query:
            result = runner.invoke(cli.app, argv)
        assert result.exit_code == 0, (argv, result.output, result.exception)
        assert query.call_args.args[0] == expected
        assert query.call_args.kwargs.items() >= options.items()
