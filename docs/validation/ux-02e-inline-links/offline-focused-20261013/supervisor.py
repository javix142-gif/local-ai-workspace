#!/usr/bin/env python3
"""Run one offline Gradle target with a 300-second hard wall limit and bounded diagnostics."""
import datetime as dt
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

repo = Path('/workspace/local-ai-workspace')
worktree = Path('/workspace/ux02e-offline-a182')
evidence = repo / 'docs/validation/ux-02e-inline-links/offline-focused-20261013'
target = 'com.localai.workspace.context.ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit'
java17 = Path('/workspace/.toolchain/jdk-17')
java21 = Path('/workspace/.toolchain/jdk-21-temurin')
events = evidence / 'test-events.tsv'
log_path = evidence / 'gradle-offline-focused.log'
command = [
    str(worktree / 'gradlew'), '--offline', '--no-daemon', '--no-parallel',
    '--max-workers=1', '--console=plain', '--info',
    '--init-script', str(evidence / 'test-events.init.gradle'),
    '-Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin',
    ':app:testDebugUnitTest', '--tests', target
]
env = os.environ.copy()
env.update({
    'JAVA_HOME': str(java17),
    'PATH': str(java17 / 'bin') + os.pathsep + env.get('PATH', ''),
    'ANDROID_HOME': '/workspace/.toolchain/android-sdk',
    'ANDROID_SDK_ROOT': '/workspace/.toolchain/android-sdk',
    'GRADLE_USER_HOME': '/home/agent/.gradle',
    'UX02E_TEST_EVENTS': str(events),
})

def now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z')

def write_json(path, obj):
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')

def proc_rows():
    result = subprocess.run(['ps', '-eo', 'pid,ppid,pgid,etimes,stat,args', '--ww'],
                            capture_output=True, text=True, timeout=10)
    rows = result.stdout.splitlines()
    return [row for row in rows if ('Gradle Test Executor' in row or 'GradleWorkerMain' in row or
                                     'GradleDaemon' in row or 'GradleWrapperMain' in row)]

def worker_pids():
    found = []
    for cmdline_path in Path('/proc').glob('[0-9]*/cmdline'):
        try:
            raw = cmdline_path.read_bytes().replace(b'\0', b' ').decode(errors='replace')
            if 'Gradle Test Executor' in raw or 'org.gradle.process.internal.worker.GradleWorkerMain' in raw:
                pid = int(cmdline_path.parent.name)
                if pid != os.getpid():
                    found.append((pid, raw.strip()))
        except (OSError, ValueError):
            pass
    return sorted(set(found))

def capture_snapshot(label):
    psres = subprocess.run(['ps', '-eo', 'pid,ppid,pgid,etimes,stat,args', '--ww'],
                           capture_output=True, text=True, timeout=10)
    filtered = [line for line in psres.stdout.splitlines()
                if ('Gradle Test Executor' in line or 'GradleWorkerMain' in line or
                    'GradleDaemon' in line or 'GradleWrapperMain' in line)]
    (evidence / f'processes-{label}.txt').write_text(
        f'timestampUtc={now()}\npsExitCode={psres.returncode}\n' + '\n'.join(filtered) + '\n')
    workers = worker_pids()
    write_json(evidence / f'workers-{label}.json', {
        'timestampUtc': now(), 'workers': [{'pid': pid, 'cmdline': cmd} for pid,cmd in workers]
    })
    return workers

def capture_thread_dumps(label, workers):
    jcmd = str(java21 / 'bin/jcmd')
    for pid, cmdline in workers:
        base = evidence / f'jcmd-{label}-pid-{pid}'
        started = now()
        try:
            result = subprocess.run([jcmd, str(pid), 'Thread.print', '-l'],
                                    capture_output=True, text=True, timeout=20)
            (base.with_suffix('.stdout.txt')).write_text(result.stdout)
            (base.with_suffix('.stderr.txt')).write_text(result.stderr)
            code = result.returncode
        except subprocess.TimeoutExpired as exc:
            (base.with_suffix('.stdout.txt')).write_bytes(exc.stdout or b'')
            (base.with_suffix('.stderr.txt')).write_bytes(exc.stderr or b'')
            code = 124
        write_json(base.with_suffix('.meta.json'), {
            'startedAtUtc': started, 'finishedAtUtc': now(), 'pid': pid,
            'workerCmdline': cmdline, 'command': [jcmd, str(pid), 'Thread.print', '-l'],
            'exitCode': code, 'timeoutSeconds': 20
        })

events.write_text('')
(evidence / 'command.txt').write_text(' '.join(command) + '\n')
(evidence / 'environment.txt').write_text(
    f'JAVA_HOME={java17}\nPATH begins with {java17}/bin\n'
    'ANDROID_HOME=/workspace/.toolchain/android-sdk\n'
    'ANDROID_SDK_ROOT=/workspace/.toolchain/android-sdk\n'
    'GRADLE_USER_HOME=/home/agent/.gradle (existing, not cleaned)\n'
    f'UX02E_TEST_EVENTS={events} (temporary init-script listener output)\n'
    'Gradle runtime JDK=17.0.20.1; app JVM worker toolchain exposed at JDK=21.0.12.1\n'
    'Gradle offline mode enabled; no downloads or installations requested.\n')
start = time.monotonic()
start_utc = now()
with log_path.open('wb') as logfile:
    proc = subprocess.Popen(command, cwd=worktree, env=env, stdout=logfile,
                            stderr=subprocess.STDOUT, start_new_session=True)
    (evidence / 'run-start.json').write_text(json.dumps({
        'startedAtUtc': start_utc, 'sourceCodeSha': 'a1827fa204cda0cdf80a7e5a51670435e8c8d4b7',
        'worktree': str(worktree), 'gradleWrapperPid': proc.pid,
        'timeoutSeconds': 300, 'command': command,
        'gradleOffline': True, 'onlyRequestedTestTask': ':app:testDebugUnitTest',
        'testFilter': target
    }, indent=2) + '\n')
    print(f'RUN_STARTED pid={proc.pid} utc={start_utc}', flush=True)
    last_event_size = events.stat().st_size
    last_event_change = time.monotonic()
    last_dump = None
    worker_seen = False
    next_notice = 60
    next_snapshot = 60
    diagnostics = []
    timed_out = False
    while True:
        rc = proc.poll()
        elapsed = time.monotonic() - start
        if rc is not None:
            break
        size = events.stat().st_size
        if size != last_event_size:
            last_event_size = size
            last_event_change = time.monotonic()
        workers = worker_pids()
        if workers:
            worker_seen = True
        if elapsed >= next_snapshot:
            rows = capture_snapshot(f't{int(elapsed)}s')
            print(f'PROGRESS elapsed={int(elapsed)}s workerCount={len(rows)} events={size}', flush=True)
            next_snapshot += 60
        if workers and time.monotonic() - last_event_change >= 120:
            if last_dump is None or time.monotonic() - last_dump >= 120:
                label = f'stall-{int(elapsed)}s'
                capture_snapshot(label)
                capture_thread_dumps(label, workers)
                diagnostics.append({'atElapsedSeconds': round(elapsed,3), 'label':label,
                                    'workerPids':[pid for pid,_ in workers]})
                last_dump = time.monotonic()
                print(f'STALL_DIAGNOSTIC elapsed={int(elapsed)}s workers={len(workers)}', flush=True)
        if elapsed >= 300:
            timed_out = True
            workers = capture_snapshot('final-before-stop')
            if workers:
                capture_thread_dumps('final-before-stop', workers)
            print('HARD_TIMEOUT_300S diagnostics captured before stop', flush=True)
            try:
                os.killpg(proc.pid, signal.SIGINT)
            except ProcessLookupError:
                pass
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGTERM)
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(proc.pid, signal.SIGKILL)
                    proc.wait(timeout=5)
            rc = proc.returncode
            break
        if elapsed >= next_notice:
            print(f'RUNNING elapsed={int(elapsed)}s events={size} worker={bool(workers)}', flush=True)
            next_notice += 60
        time.sleep(1)
end_utc = now()
duration = round(time.monotonic() - start, 3)
# Preserve the exact result XML from this clean worktree, if emitted.
xml_files = sorted(worktree.glob('**/build/test-results/testDebugUnitTest/TEST-*.xml'))
xml_dir = evidence / 'xml'
if xml_files:
    xml_dir.mkdir(exist_ok=True)
xml_summaries=[]
for x in xml_files:
    target_path=xml_dir/x.name
    target_path.write_bytes(x.read_bytes())
    tree=ET.parse(x).getroot()
    xml_summaries.append({'file':x.relative_to(worktree).as_posix(),'copiedTo':target_path.relative_to(evidence).as_posix(),
                          'tests':int(tree.attrib.get('tests',0)),'failures':int(tree.attrib.get('failures',0)),
                          'errors':int(tree.attrib.get('errors',0)),'skipped':int(tree.attrib.get('skipped',0))})
(evidence / 'junit-xml-inventory.txt').write_text(
    f'worktree={worktree}\nsourceCodeSha=a1827fa204cda0cdf80a7e5a51670435e8c8d4b7\n'
    f'xmlFileCount={len(xml_summaries)}\n' +
    ''.join(f"{x['file']} tests={x['tests']} failures={x['failures']} errors={x['errors']} skipped={x['skipped']}\n" for x in xml_summaries) +
    (f"TOTAL tests={sum(x['tests'] for x in xml_summaries)} failures={sum(x['failures'] for x in xml_summaries)} errors={sum(x['errors'] for x in xml_summaries)} skipped={sum(x['skipped'] for x in xml_summaries)}\n" if xml_summaries else 'No JUnit XML was emitted.\n'))
# Count Gradle task records without inferring completion from progress messages.
log_text=log_path.read_text(errors='replace')
task_lines=[line.strip() for line in log_text.splitlines() if line.strip().startswith('> Task ')]
(evidence / 'task-outcomes.txt').write_text(
    f'wrapperExitCode={rc}\nrunTimedOut={str(timed_out).lower()}\n'
    f'testWorkerObserved={str(worker_seen).lower()}\n'
    f'testEventLines={sum(1 for line in events.read_text().splitlines() if line)}\n'
    f'junitXmlFiles={len(xml_summaries)}\n' +
    ('gradleTaskOutputLines:\n'+'\n'.join(task_lines)+'\n' if task_lines else 'gradleTaskOutputLines=0\n'))
if timed_out:
    classification='TIMEOUT_300S'
elif rc == 0 and xml_summaries and all(x['failures']==0 and x['errors']==0 for x in xml_summaries):
    classification='TARGET_TEST_PASS'
elif 'No cached version of' in log_text or 'not available for offline mode' in log_text or 'Could not resolve' in log_text:
    classification='OFFLINE_DEPENDENCY_RESOLUTION_BLOCKED'
elif any(x['failures'] or x['errors'] for x in xml_summaries):
    classification='TARGET_TEST_FAILED_WITH_JUNIT_XML'
else:
    classification='GRADLE_FAILURE_BEFORE_TARGET_RESULT'
write_json(evidence / 'run-result.json', {
    'startedAtUtc':start_utc,'finishedAtUtc':end_utc,'durationSeconds':duration,
    'sourceCodeSha':'a1827fa204cda0cdf80a7e5a51670435e8c8d4b7','wrapperExitCode':rc,
    'timedOutAt300Seconds':timed_out,'testWorkerObserved':worker_seen,
    'testEventFile':'test-events.tsv','testEvents':sum(1 for line in events.read_text().splitlines() if line),
    'xml':xml_summaries,'diagnosticCaptures':diagnostics,'target':target,
    'classification':classification
})
print(f'RUN_FINISHED exit={rc} elapsed={duration}s timeout={timed_out} workersSeen={worker_seen} xml={len(xml_summaries)}', flush=True)
