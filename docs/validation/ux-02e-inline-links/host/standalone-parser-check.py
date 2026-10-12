#!/usr/bin/env python3
"""Compile and run only BasicMarkdownTest with cached Kotlin/JUnit artifacts.

This is a parser-only fallback check, not a substitute for the Android Gradle
or Compose/Robolectric gates. It avoids fabricating Android SDK/NDK packages.
"""

from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[4]
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"


def jar(pattern: str) -> str:
    matches = sorted(CACHE.rglob(pattern))
    if not matches:
        raise RuntimeError(f"Cached artifact not found: {pattern}")
    return str(matches[0])


def run(command: list[str]) -> int:
    print("COMMAND:", " ".join(command), flush=True)
    result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    print(result.stdout, end="", flush=True)
    print("EXIT_CODE:", result.returncode, flush=True)
    return result.returncode


def main() -> int:
    print("CODE_SHA:", subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip())
    compiler = jar("kotlin-compiler-embeddable-2.0.21.jar")
    stdlib = jar("kotlin-stdlib-2.0.21.jar")
    script_runtime = jar("kotlin-script-runtime-2.0.21.jar")
    reflect = jar("kotlin-reflect-1.6.10.jar")
    trove = jar("trove4j-1.0.20200330.jar")
    coroutines = jar("kotlinx-coroutines-core-jvm-1.6.4.jar")
    annotations = jar("annotations-13.0.jar")
    junit = jar("junit-4.13.2.jar")
    hamcrest = jar("hamcrest-core-1.3.jar")
    compiler_classpath = ":".join((compiler, stdlib, script_runtime, reflect, trove, coroutines, annotations))
    test_classpath = ":".join((stdlib, junit, hamcrest))
    with tempfile.TemporaryDirectory(prefix="ux02e-parser-") as temporary:
        output = str(Path(temporary) / "classes")
        compile_command = [
            "java", "-cp", compiler_classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", test_classpath,
            "-d", output,
            "app/src/main/java/com/localai/workspace/ui/BasicMarkdown.kt",
            "app/src/test/java/com/localai/workspace/ui/BasicMarkdownTest.kt",
        ]
        compile_status = run(compile_command)
        if compile_status:
            return compile_status
        run_classpath = ":".join((output, stdlib, junit, hamcrest))
        return run(["java", "-cp", run_classpath, "org.junit.runner.JUnitCore", "com.localai.workspace.ui.BasicMarkdownTest"])


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"CHECK_SETUP_ERROR: {type(error).__name__}: {error}", file=sys.stderr)
        raise SystemExit(2)
