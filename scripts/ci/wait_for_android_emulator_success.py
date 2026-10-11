#!/usr/bin/env python3
"""Wait for the exact feature-branch source SHA to pass connected instrumentation."""

from __future__ import annotations

import json
import os
import sys
import time
from typing import Callable
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen


WORKFLOW_FILE = "android-emulator-instrumented.yml"
EXPECTED_BRANCH = "feat/0.5.0-skills-agents"


class GateError(RuntimeError):
    pass


def matching_run(payload: dict[str, object], source_sha: str, branch: str) -> dict[str, object] | None:
    runs = payload.get("workflow_runs", [])
    if not isinstance(runs, list):
        raise GateError("WORKFLOW_RUNS_RESPONSE_INVALID")
    matches = [
        run for run in runs
        if isinstance(run, dict)
        and str(run.get("head_sha", "")).lower() == source_sha.lower()
        and run.get("head_branch") == branch
        and run.get("event") == "push"
    ]
    if not matches:
        return None
    return max(matches, key=lambda run: (str(run.get("created_at", "")), int(run.get("run_number", 0))))


def fetch_workflow_runs(
    repository: str,
    source_sha: str,
    branch: str,
    token: str,
    api_url: str = "https://api.github.com",
) -> dict[str, object]:
    if "/" not in repository or not token:
        raise GateError("GITHUB_REPOSITORY_OR_TOKEN_UNAVAILABLE")
    query = urlencode({"branch": branch, "head_sha": source_sha, "per_page": "100"})
    workflow = quote(WORKFLOW_FILE, safe="")
    url = f"{api_url.rstrip('/')}/repos/{repository}/actions/workflows/{workflow}/runs?{query}"
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "local-ai-workspace-ci-gate",
    })
    try:
        with urlopen(request, timeout=20) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise GateError(f"GITHUB_ACTIONS_API_ERROR:{type(exc).__name__}") from exc
    if not isinstance(payload, dict):
        raise GateError("GITHUB_ACTIONS_API_RESPONSE_INVALID")
    return payload


def wait_for_success(
    fetch: Callable[[], dict[str, object]],
    source_sha: str,
    branch: str,
    timeout_seconds: int,
    poll_seconds: int,
    *,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> dict[str, object]:
    deadline = monotonic() + timeout_seconds
    while True:
        run = matching_run(fetch(), source_sha, branch)
        if run is not None:
            if run.get("status") == "completed":
                if run.get("conclusion") != "success":
                    raise GateError(json.dumps({
                        "reason": "ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL",
                        "runId": run.get("id"),
                        "headSha": run.get("head_sha"),
                        "conclusion": run.get("conclusion"),
                        "runUrl": run.get("html_url"),
                    }, separators=(",", ":")))
                return run
        remaining = deadline - monotonic()
        if remaining <= 0:
            raise GateError(f"ANDROID_INSTRUMENTATION_RUN_NOT_SUCCESSFUL_BEFORE_TIMEOUT:{source_sha}")
        sleep(min(poll_seconds, remaining))


def write_output(path: str, run: dict[str, object], source_sha: str) -> None:
    output = {
        "gate_passed": "true",
        "android_run_id": str(run.get("id", "")),
        "android_run_url": str(run.get("html_url", "")),
        "android_run_sha": source_sha,
        "android_run_conclusion": str(run.get("conclusion", "")),
    }
    if not output["android_run_id"].isdigit() or not output["android_run_url"].startswith("https://github.com/"):
        raise GateError("ANDROID_RUN_METADATA_INVALID")
    with open(path, "a", encoding="utf-8") as stream:
        for key, value in output.items():
            stream.write(f"{key}={value}\n")


def main() -> int:
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    source_sha = os.environ.get("GITHUB_SHA", "")
    branch = os.environ.get("GITHUB_REF_NAME", "")
    token = os.environ.get("GITHUB_TOKEN", "")
    api_url = os.environ.get("GITHUB_API_URL", "https://api.github.com")
    output_path = os.environ.get("GITHUB_OUTPUT", "")
    try:
        if not source_sha or len(source_sha) != 40 or not branch or not output_path:
            raise GateError("SOURCE_METADATA_UNAVAILABLE")
        if branch != EXPECTED_BRANCH:
            raise GateError("UNAUTHORIZED_BRANCH")
        timeout_seconds = int(os.environ.get("ANDROID_GATE_TIMEOUT_SECONDS", "2700"))
        poll_seconds = int(os.environ.get("ANDROID_GATE_POLL_SECONDS", "20"))
        if timeout_seconds <= 0 or poll_seconds <= 0:
            raise GateError("INVALID_GATE_TIMEOUT_CONFIGURATION")
        fetch = lambda: fetch_workflow_runs(repository, source_sha, branch, token, api_url)
        run = wait_for_success(fetch, source_sha, branch, timeout_seconds, poll_seconds)
        write_output(output_path, run, source_sha)
        print(json.dumps({
            "status": "ANDROID_INSTRUMENTATION_SUCCESS",
            "sourceSha": source_sha,
            "runId": run.get("id"),
            "runUrl": run.get("html_url"),
            "conclusion": run.get("conclusion"),
        }, separators=(",", ":")))
        return 0
    except (GateError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
