"""Security regressions for credential helpers; real Docker is never called."""

import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest


REPO = Path(__file__).resolve().parents[1]
POWERSHELLS = list(dict.fromkeys(
    path for name in ("powershell", "pwsh") if (path := shutil.which(name))
))
GIT_BASH = Path("C:/Program Files/Git/bin/bash.exe")
BASH = str(GIT_BASH) if GIT_BASH.exists() else (
    shutil.which("bash") if os.name != "nt" else None
)

PS_HARNESS = r'''
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile(
    $env:ROTATION_TEST_SOURCE, [ref]$tokens, [ref]$parseErrors)
if (@($parseErrors).Count -ne 0) { throw 'PowerShell parse failed' }
$helper = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Invoke-DockerCli'
}, $true)
if (-not $helper) { throw 'Docker helper missing' }
. ([scriptblock]::Create($helper.Extent.Text))

$failed = $false
try {
    $null = Invoke-DockerCli -DockerCommand $env:ROTATION_TEST_DOCKER -Arguments @(
        'fail', 'ARG_SECRET_MUST_STAY_PRIVATE')
} catch {
    $failed = $true
    $message = $_.ToString()
    if ($message -match 'SECRET_MUST_STAY_PRIVATE') { throw 'Credential leaked in error' }
    if ($message -notmatch 'exit code 23') { throw 'Failure lost its exit code' }
}
if (-not $failed) { throw 'Docker failure was accepted' }
if ($ErrorActionPreference -ne 'Stop') { throw 'Caller error preference changed' }

$result = Invoke-DockerCli -DockerCommand $env:ROTATION_TEST_DOCKER -Arguments @('success')
if ($result.ExitCode -ne 0 -or $result.Text -notmatch 'fixture-ok') {
    throw 'Successful probe result changed'
}
$result = Invoke-DockerCli -DockerCommand $env:ROTATION_TEST_DOCKER -Arguments @('fail') -IgnoreExitCode
if ($result.ExitCode -ne 23 -or $result.Text -notmatch 'OUTPUT_SECRET_MUST_STAY_PRIVATE') {
    throw 'Ignored failure result changed'
}
$recreate = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq 'Invoke-ComposeRecreate'
}, $true)
if ($recreate) {
    . ([scriptblock]::Create($recreate.Extent.Text))
    Invoke-ComposeRecreate -DockerCommand $env:ROTATION_TEST_DOCKER -ComposeFiles @('fixture.yml') -Services @('panel-web', 'bot-runner') -ProjectName fixture
}
Write-Output 'GREEN'
'''

FAKE_DOCKER = r'''
import sys
args = sys.argv[1:]
if args == ["success"]:
    print("fixture-ok")
elif args[:1] == ["fail"]:
    print("OUTPUT_SECRET_MUST_STAY_PRIVATE")
    print("STDERR_SECRET_MUST_STAY_PRIVATE", file=sys.stderr)
    sys.exit(23)
elif args[:1] == ["exec"]:
    print("OUTPUT_SECRET_MUST_STAY_PRIVATE")
    print("STDERR_SECRET_MUST_STAY_PRIVATE", file=sys.stderr)
    sys.exit(23)
elif args[:1] == ["compose"] and "up" in args:
    if "--no-deps" not in args:
        raise SystemExit(24)
    if args[-2:] != ["panel-web", "bot-runner"]:
        raise SystemExit(25)
elif args == ["compose", "version"] or args == ["info"] or args[:1] == ["ps"]:
    pass
else:
    raise SystemExit("Unexpected fake Docker operation")
'''


class CredentialRotationTests(unittest.TestCase):
    def setUp(self):
        self.fixture = tempfile.TemporaryDirectory(prefix="iguana-rotation-test-")
        self.addCleanup(self.fixture.cleanup)
        self.root = Path(self.fixture.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        fake = self.root / "fake_docker.py"
        fake.write_text(FAKE_DOCKER, encoding="utf-8")
        (self.bin / "docker.cmd").write_text(
            '@"' + sys.executable + '" "%ROTATION_TEST_FAKE%" %*\n', encoding="utf-8")
        docker_shell = self.bin / "docker"
        docker_shell.write_text(
            "#!/usr/bin/env bash\nexec " + shlex.quote(sys.executable.replace("\\", "/"))
            + ' "$ROTATION_TEST_FAKE" "$@"\n', encoding="utf-8")
        docker_shell.chmod(0o755)
        self.env = dict(os.environ)
        self.env.update(
            PATH=str(self.bin) + os.pathsep + self.env.get("PATH", ""),
            ROTATION_TEST_FAKE=str(fake).replace("\\", "/"),
            ROTATION_TEST_DOCKER=str(self.bin / "docker.cmd") if os.name == "nt" else str(docker_shell),
        )

    def test_powershell_errors_do_not_disclose_credentials(self):
        if not POWERSHELLS:
            self.skipTest("PowerShell is not installed")
        harness = self.root / "harness.ps1"
        harness.write_text(PS_HARNESS, encoding="utf-8")
        for shell in POWERSHELLS:
            for helper in ("apply", "status"):
                with self.subTest(shell=shell, helper=helper):
                    env = dict(self.env, ROTATION_TEST_SOURCE=str(
                        REPO / f"scripts/docker-production-credential-migration-{helper}.ps1"))
                    result = subprocess.run(
                        [shell, "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
                         "-File", str(harness)], env=env, capture_output=True, text=True, timeout=30)
                    output = result.stdout + result.stderr
                    self.assertEqual(result.returncode, 0, output)
                    self.assertIn("GREEN", output)
                    self.assertNotIn("SECRET_MUST_STAY_PRIVATE", output)

    def test_bash_rehearsal_stops_at_first_failed_component(self):
        if not BASH:
            self.skipTest("Bash is not installed")
        scripts = self.root / "scripts"
        scripts.mkdir()
        script = scripts / "docker-production-credential-migration-apply.sh"
        script.write_text((REPO / "scripts" / script.name).read_text(encoding="utf-8"), encoding="utf-8", newline="\n")
        result = subprocess.run(
            [BASH, str(script).replace("\\", "/"), "--components", "redis,grafana", "--rehearsal"],
            env=self.env, capture_output=True, text=True, timeout=30)
        output = result.stdout + result.stderr
        self.assertNotEqual(result.returncode, 0, output)
        self.assertIn("Starting step 1/2: redis", output)
        self.assertIn("Redis container is not running", output)
        self.assertNotIn("Completed step 1/2", output)
        self.assertNotIn("Starting step 2/2", output)
        self.assertNotIn("completed successfully", output)
        self.assertFalse((self.root / ".env").exists())
        self.assertEqual(list(self.root.glob(".env.credential-migration-*")), [])

    def test_secret_snapshots_are_ignored_by_git(self):
        for path in (
            "artifacts/credential-rotation-20261123-120000/env.before",
            "credential-rotation-20261123-120000/env.before",
            ".env.credential-migration-redis-20261123-120000.bak",
        ):
            with self.subTest(path=path):
                result = subprocess.run(
                    ["git", "check-ignore", "--no-index", "-q", path], cwd=REPO,
                    capture_output=True, text=True, timeout=10)
                self.assertEqual(result.returncode, 0, "Secret snapshot is not ignored: " + path)

    def test_bash_password_command_errors_do_not_disclose_credentials(self):
        if not BASH:
            self.skipTest("Bash is not installed")
        source = (REPO / "scripts/docker-production-credential-migration-apply.sh").read_text(encoding="utf-8")
        for helper in ("postgres", "rabbitmq", "redis", "grafana"):
            with self.subTest(helper=helper):
                name = f"update_{helper}_password"
                function = re.search(r"(?ms)^" + name + r"\(\) \{.*?^\}", source)
                self.assertIsNotNone(function)
                harness = self.root / f"{helper}.sh"
                harness.write_text(function.group() + "\n" + name + " fixture user database current target\n", encoding="utf-8", newline="\n")
                result = subprocess.run(
                    [BASH, str(harness).replace("\\", "/")], env=self.env,
                    capture_output=True, text=True, timeout=10)
                output = result.stdout + result.stderr
                self.assertEqual(result.returncode, 23, output)
                self.assertNotIn("SECRET_MUST_STAY_PRIVATE", output)

    def test_bash_recreate_does_not_start_unselected_dependencies(self):
        if not BASH:
            self.skipTest("Bash is not installed")
        source = (REPO / "scripts/docker-production-credential-migration-apply.sh").read_text(encoding="utf-8")
        function = re.search(r"(?ms)^invoke_compose_recreate\(\) \{.*?^\}", source)
        self.assertIsNotNone(function)
        harness = self.root / "recreate.sh"
        harness.write_text(
            'PROJECT=fixture\nCOMPOSE_FILES=(fixture.yml)\nRESTART_SERVICES=(panel-web bot-runner)\n'
            + function.group() + '\ninvoke_compose_recreate\n', encoding="utf-8", newline="\n")
        result = subprocess.run(
            [BASH, str(harness).replace("\\", "/")], env=self.env,
            capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
