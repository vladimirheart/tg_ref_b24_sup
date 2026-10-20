"""Behavioral tests for both maintenance scripts; Docker is always a fake."""

import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


REPO = Path(__file__).resolve().parents[1]
POWERSHELL = shutil.which("powershell") or shutil.which("pwsh")
GIT_BASH = Path("C:/Program Files/Git/bin/bash.exe")
BASH = str(GIT_BASH) if GIT_BASH.exists() else (shutil.which("bash") if os.name != "nt" else None)
FAKE_DOCKER = r'''
import json
import os
from pathlib import Path
import sys

args = sys.argv[1:]
with Path(os.environ["DOCKER_MAINTENANCE_TEST_LOG"]).open("a", encoding="utf-8") as log:
    log.write(json.dumps(args) + "\n")
if args[:1] == ["info"]:
    print(os.environ.get("DOCKER_MAINTENANCE_TEST_DAEMON", "test:daemon"))
elif args[:1] == ["compose"]:
    if "up" in args and os.environ.get("DOCKER_MAINTENANCE_TEST_FAIL_UP") == "1":
        sys.exit(23)
    if "--environment" in args:
        print("COMPOSE_PROJECT_NAME=fixture-project")
        print("SECRET_MUST_NOT_BE_PRINTED=fixture-secret-value")
elif args[:1] == ["ps"]:
    pass
elif args[:2] == ["image", "prune"]:
    if os.environ.get("DOCKER_MAINTENANCE_TEST_FAIL") == "1":
        print("Simulated prune failure", file=sys.stderr)
        sys.exit(19)
    print("Total reclaimed space: 0B")
elif args[:2] in (["system", "df"], ["image", "ls"]):
    print("fixture storage report")
else:
    raise SystemExit("Unexpected Docker operation: " + repr(args))
'''


class MaintenanceTests:
    language = None

    def setUp(self):
        if self.language == "ps" and not POWERSHELL:
            self.skipTest("PowerShell is not installed")
        if self.language == "sh" and not BASH:
            self.skipTest("Bash is not installed")
        self.fixture = tempfile.TemporaryDirectory(prefix="iguana-maintenance-test-")
        self.addCleanup(self.fixture.cleanup)
        self.root = Path(self.fixture.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.state = self.root / "state with spaces"
        self.log = self.root / "docker.jsonl"
        fake = self.root / "fake_docker.py"
        fake.write_text(FAKE_DOCKER, encoding="utf-8")
        (self.bin / "docker.cmd").write_text(
            '@"' + sys.executable + '" "%DOCKER_MAINTENANCE_FAKE%" %*\n',
            encoding="utf-8",
        )
        docker_shell = self.bin / "docker"
        docker_shell.write_text(
            "#!/usr/bin/env bash\nexec "
            + shlex.quote(sys.executable.replace("\\", "/"))
            + ' "$DOCKER_MAINTENANCE_FAKE" "$@"\n',
            encoding="utf-8",
        )
        docker_shell.chmod(0o755)
        self.env = dict(os.environ)
        self.env.update(
            PATH=str(self.bin) + os.pathsep + self.env.get("PATH", ""),
            DOCKER_MAINTENANCE_FAKE=str(fake),
            DOCKER_MAINTENANCE_TEST_LOG=str(self.log),
        )

    def run_helper(self, *options, fail=False, **env):
        mapping = {
            "clean": ("-Clean", "--clean"),
            "if_due": ("-IfDue", "--if-due"),
            "project": ("-ProjectName", "--project-name"),
            "retention": ("-RetentionHours", "--retention-hours"),
        }
        index = 0 if self.language == "ps" else 1
        if self.language == "ps":
            cmd = [POWERSHELL, "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                   str(REPO / "scripts/docker-maintenance.ps1"), "-StateDirectory", str(self.state)]
        else:
            cmd = [BASH, str(REPO / "scripts/docker-maintenance.sh").replace("\\", "/"),
                   "--state-directory", str(self.state).replace("\\", "/")]
        for option in options:
            if isinstance(option, tuple):
                cmd.extend((mapping[option[0]][index], str(option[1])))
            else:
                cmd.append(mapping[option][index])
        result = subprocess.run(
            cmd, env=dict(self.env, **env), cwd=REPO, capture_output=True,
            text=True, encoding="utf-8", errors="replace", timeout=30,
        )
        if fail:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def calls(self):
        if not self.log.exists():
            return []
        return [json.loads(line) for line in self.log.read_text(encoding="utf-8").splitlines()]

    def prunes(self):
        return [call for call in self.calls() if call[:2] == ["image", "prune"]]

    def stamp(self):
        stamps = list(self.state.glob("*.last-clean"))
        self.assertEqual(len(stamps), 1)
        return stamps[0]

    def test_report_is_read_only_and_does_not_print_compose_secrets(self):
        result = self.run_helper()
        self.assertFalse(self.state.exists())
        self.assertEqual(self.prunes(), [])
        self.assertNotIn("fixture-secret-value", result.stdout + result.stderr)

    def test_cleanup_is_project_scoped_and_keeps_tagged_images(self):
        self.run_helper("clean")
        self.assertEqual(self.prunes(), [[
            "image", "prune", "--force", "--filter",
            "label=io.iguana.project=fixture-project", "--filter", "until=168h",
        ]])
        self.assertTrue(self.stamp().read_text(encoding="ascii").strip().isdigit())
        self.assertEqual(list(self.state.glob("*.lock")), [])

    def test_weekly_cleanup_is_skipped_when_not_due(self):
        self.run_helper("clean", "if_due")
        self.run_helper("clean", "if_due")
        self.assertEqual(len(self.prunes()), 1)

    def test_expired_cleanup_is_run_again(self):
        self.run_helper("clean", "if_due")
        self.stamp().write_text(str(int(time.time()) - 8 * 86400), encoding="ascii")
        self.run_helper("clean", "if_due")
        self.assertEqual(len(self.prunes()), 2)

    def test_manual_cleanup_bypasses_interval(self):
        self.run_helper("clean")
        self.run_helper("clean")
        self.assertEqual(len(self.prunes()), 2)

    def test_state_is_scoped_to_daemon_and_project(self):
        self.run_helper("clean", "if_due")
        self.run_helper("clean", "if_due", ("project", "second-project"))
        self.run_helper("clean", "if_due", DOCKER_MAINTENANCE_TEST_DAEMON="second:daemon")
        self.assertEqual(len(self.prunes()), 3)
        self.assertEqual(len(list(self.state.glob("*.last-clean"))), 3)

    def test_failure_does_not_record_success_or_leak_lock(self):
        self.run_helper("clean", fail=True, DOCKER_MAINTENANCE_TEST_FAIL="1")
        self.assertEqual(list(self.state.glob("*.last-clean")), [])
        self.assertEqual(list(self.state.glob("*.lock")), [])
        self.run_helper("clean", "if_due")
        self.assertEqual(len(self.prunes()), 2)

    def test_existing_lock_prevents_duplicate_cleanup(self):
        self.run_helper("clean")
        lock = Path(str(self.stamp()) + ".lock")
        lock.mkdir()
        self.run_helper("clean")
        self.assertEqual(len(self.prunes()), 1)
        self.assertTrue(lock.is_dir(), "Another process's lock must not be removed")

    def test_invalid_or_future_timestamp_fails_closed(self):
        self.run_helper("clean")
        for value in ("not-a-timestamp", "123\n456", str(int(time.time()) + 86400)):
            with self.subTest(value=value):
                self.stamp().write_text(value, encoding="ascii")
                self.run_helper("clean", "if_due", fail=True)
        self.assertEqual(len(self.prunes()), 1)
        self.assertEqual(list(self.state.glob("*.lock")), [])

    def test_invalid_project_is_rejected_before_deletion(self):
        self.run_helper("clean", ("project", "../other"), fail=True)
        self.assertEqual(self.prunes(), [])
        self.assertFalse(self.state.exists())

    def test_if_due_without_clean_is_rejected(self):
        self.run_helper("if_due", fail=True)
        self.assertEqual(self.calls(), [])

    def test_custom_retention_and_cross_platform_timestamp(self):
        self.run_helper("clean", ("retention", 336))
        self.assertIn("until=336h", self.prunes()[0])
        self.stamp().write_bytes((str(int(time.time())) + "\r\n").encode("ascii"))
        self.run_helper("clean", "if_due")
        self.assertEqual(len(self.prunes()), 1)

    def run_launcher(self, *options, fail=False, **env):
        checkout = self.root / "launcher"
        scripts = checkout / "scripts"
        scripts.mkdir(parents=True, exist_ok=True)
        for name in ("docker-production-up.ps1", "docker-production-up.sh",
                     "docker-maintenance.ps1", "docker-maintenance.sh"):
            (scripts / name).write_text(
                (REPO / "scripts" / name).read_text(encoding="utf-8"),
                encoding="utf-8", newline="\n",
            )
        seed = checkout / "config/shared"
        seed.mkdir(parents=True, exist_ok=True)
        for name in ("settings.json", "locations.json", "org_structure.json"):
            (seed / name).write_text("{}", encoding="utf-8")
        (checkout / "docker-compose.production-contour.yml").write_text(
            "services: {}\n", encoding="utf-8",
        )
        runtime = str(self.root / "runtime")
        if self.language == "ps":
            cmd = [POWERSHELL, "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
                   "-File", str(scripts / "docker-production-up.ps1"), "-AllowInsecureDefaults"]
            mapping = {"build": "-Build", "foreground": "-NoDetach", "validate": "-ValidateOnly"}
        else:
            cmd = [BASH, str(scripts / "docker-production-up.sh").replace("\\", "/"),
                   "--allow-insecure-defaults"]
            mapping = {"build": "--build", "foreground": "--no-detach", "validate": "--validate-only"}
            if os.name == "nt":
                runtime = "/" + runtime[0].lower() + runtime[2:].replace("\\", "/")
        cmd.extend(mapping[option] for option in options)
        fixture_env = {key: value for key, value in self.env.items()
                       if not key.startswith(("IGUANA_", "APP_", "MONITORING_"))}
        fixture_env["IGUANA_SHARED_CONFIG_DIR"] = runtime
        result = subprocess.run(
            cmd, env=dict(fixture_env, **env), cwd=checkout, capture_output=True,
            text=True, encoding="utf-8", errors="replace", timeout=30,
        )
        if fail:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def test_launcher_runs_cleanup_after_build_and_respects_interval(self):
        self.run_launcher("build")
        self.run_launcher("build")
        self.assertEqual(len(self.prunes()), 1)

    def test_failed_start_does_not_run_cleanup(self):
        self.run_launcher("build", fail=True, DOCKER_MAINTENANCE_TEST_FAIL_UP="1")
        self.assertEqual(self.prunes(), [])

    def test_cleanup_failure_does_not_fail_successful_start(self):
        result = self.run_launcher("build", DOCKER_MAINTENANCE_TEST_FAIL="1")
        self.assertIn("project image cleanup failed", result.stdout + result.stderr)
        self.assertEqual(len(self.prunes()), 1)

    def test_cleanup_is_skipped_without_build_in_foreground_or_when_disabled(self):
        self.run_launcher()
        self.run_launcher("build", "foreground")
        self.run_launcher("build", IGUANA_DOCKER_AUTO_CLEANUP="false")
        self.run_launcher("build", "validate")
        self.assertEqual(self.prunes(), [])


class PowerShellMaintenanceTests(MaintenanceTests, unittest.TestCase):
    language = "ps"


class BashMaintenanceTests(MaintenanceTests, unittest.TestCase):
    language = "sh"


if __name__ == "__main__":
    unittest.main(verbosity=2)
