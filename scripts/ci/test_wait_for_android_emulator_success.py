import unittest

import wait_for_android_emulator_success as gate


SHA = "a" * 40
BRANCH = "feat/0.5.0-skills-agents"


def run(*, sha=SHA, branch=BRANCH, event="push", status="completed", conclusion="success", run_id=123):
    return {
        "id": run_id,
        "run_number": run_id,
        "head_sha": sha,
        "head_branch": branch,
        "event": event,
        "status": status,
        "conclusion": conclusion,
        "created_at": "2026-10-11T12:00:00Z",
        "html_url": f"https://github.com/org/repo/actions/runs/{run_id}",
    }


class MatchingRunTest(unittest.TestCase):
    def test_only_exact_push_sha_and_branch_match(self):
        payload = {"workflow_runs": [
            run(sha="b" * 40, run_id=1),
            run(branch="main", run_id=2),
            run(event="workflow_dispatch", run_id=3),
            run(run_id=4),
        ]}
        self.assertEqual(4, gate.matching_run(payload, SHA, BRANCH)["id"])

    def test_newest_matching_run_is_selected(self):
        older = run(run_id=4)
        newer = dict(run(run_id=5), created_at="2026-10-11T12:01:00Z")
        self.assertEqual(5, gate.matching_run({"workflow_runs": [newer, older]}, SHA, BRANCH)["id"])

    def test_non_success_completion_fails_closed(self):
        with self.assertRaises(gate.GateError) as error:
            gate.wait_for_success(lambda: {"workflow_runs": [run(conclusion="failure")]}, SHA, BRANCH, 1, 1)
        self.assertIn("ANDROID_INSTRUMENTATION_NOT_SUCCESSFUL", str(error.exception))

    def test_waits_for_exact_run_to_finish_successfully(self):
        responses = iter([
            {"workflow_runs": [run(status="in_progress", conclusion=None)]},
            {"workflow_runs": [run(status="completed", conclusion="success")]},
        ])
        now = [0.0]
        slept = []

        def sleep(seconds):
            slept.append(seconds)
            now[0] += seconds

        result = gate.wait_for_success(
            lambda: next(responses), SHA, BRANCH, 30, 5,
            monotonic=lambda: now[0], sleep=sleep,
        )
        self.assertEqual(123, result["id"])
        self.assertEqual([5], slept)

    def test_timeout_without_matching_run_does_not_pass(self):
        now = [0.0]
        with self.assertRaisesRegex(gate.GateError, "NOT_SUCCESSFUL_BEFORE_TIMEOUT"):
            gate.wait_for_success(
                lambda: {"workflow_runs": []}, SHA, BRANCH, 2, 1,
                monotonic=lambda: now[0],
                sleep=lambda seconds: now.__setitem__(0, now[0] + seconds),
            )

    def test_output_requires_github_run_metadata(self):
        with self.assertRaisesRegex(gate.GateError, "ANDROID_RUN_METADATA_INVALID"):
            gate.write_output("/dev/null", run(run_id="not-a-number"), SHA)


if __name__ == "__main__":
    unittest.main()
