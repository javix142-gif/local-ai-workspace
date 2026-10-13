#!/usr/bin/env python3
import datetime, hashlib, json, os, pathlib, signal, subprocess, sys, time

WT = pathlib.Path('/workspace/ux02e-offline-a182')
EV = pathlib.Path('/workspace/local-ai-workspace/docs/validation/ux-02e-inline-links/prefix-diagnostic-20261013')
J17 = pathlib.Path('/workspace/.toolchain/jdk-17')
J21 = pathlib.Path('/workspace/.toolchain/jdk-21-temurin')
SDK = pathlib.Path('/workspace/.toolchain/android-sdk')
EVENTS = EV / 'test-events.tsv'
LOG = EV / 'gradle.log'
SUPERVISOR_LOG = EV / 'supervisor.log'
MAX_SECONDS = 300.0
QUIET_SECONDS = 120.0
CONFIRM_SECONDS = 60.0
CAPTURE_RESERVE_SECONDS = 50.0
SOURCE_SHA = 'a1827fa204cda0cdf80a7e5a51670435e8c8d4b7'
TARGET = 'com.localai.workspace.context.ContextFoundationDeduplicationTest.productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit'

FILTERS = [
    'com.localai.workspace.context.AuditHarness',
    'com.localai.workspace.context.ContextDeduplicationAblationTest',
    'com.localai.workspace.context.ContextEvidenceExcerptTest',
    TARGET,
]
INIT = EV / 'test-events.init.gradle'
ARGV = [
    './gradlew', '--offline', '--no-daemon', '--no-parallel', '--max-workers=1',
    '--console=plain', '--info', '--init-script', str(INIT),
    f'-Porg.gradle.java.installations.paths={J17},{J21}', ':app:testDebugUnitTest',
]
for pattern in FILTERS:
    ARGV += ['--tests', pattern]


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')


def say(message):
    line = f'{utc()} {message}'
    print(line, flush=True)
    with SUPERVISOR_LOG.open('a', encoding='utf-8') as out:
        out.write(line + '\n')


def events():
    if not EVENTS.exists():
        return []
    result = []
    for line in EVENTS.read_text(errors='replace').splitlines():
        fields = line.split('\t')
        if len(fields) >= 2 and fields[1] in ('TEST_START', 'TEST_END'):
            result.append(line)
    return result


def process_rows(pgid):
    cp = subprocess.run(['ps', '-ww', '-eo', 'pid,ppid,pgid,stat,etime,args'], text=True,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=8)
    lines = cp.stdout.splitlines()
    selected = [lines[0]] if lines else []
    rows = []
    for line in lines[1:]:
        fields = line.strip().split(None, 5)
        if len(fields) < 6:
            continue
        try:
            pid, ppid, group = int(fields[0]), int(fields[1]), int(fields[2])
        except ValueError:
            continue
        if group == pgid:
            row = {'pid': pid, 'ppid': ppid, 'pgid': group, 'state': fields[3],
                   'elapsed': fields[4], 'args': fields[5]}
            rows.append(row)
            selected.append(line)
    return cp.returncode, '\n'.join(selected) + '\n', rows


def capture(label, reason, proc, run_start_mono):
    directory = EV / label
    directory.mkdir(parents=True, exist_ok=True)
    current_events = events()
    metadata = {
        'capturedAtUtc': utc(), 'reason': reason,
        'elapsedSeconds': round(time.monotonic() - run_start_mono, 3),
        'sourceCodeSha': SOURCE_SHA, 'wrapperPid': proc.pid,
        'testEventCount': len(current_events),
        'lastTestEvent': current_events[-1] if current_events else None,
        'workerJdk': str(J21), 'jcmdPath': str(J21 / 'bin/jcmd'),
    }
    try:
        ps_exit, process_text, rows = process_rows(proc.pid)
        (directory / 'processes.txt').write_text(process_text)
        metadata['psExitCode'] = ps_exit
    except Exception as error:
        rows = []
        metadata['psError'] = repr(error)
        (directory / 'processes.txt').write_text(repr(error) + '\n')

    (directory / 'test-events-tail.tsv').write_text('\n'.join(current_events[-80:]) + ('\n' if current_events else ''))
    try:
        lines = LOG.read_text(errors='replace').splitlines()
        (directory / 'gradle-log-tail.txt').write_text('\n'.join(lines[-180:]) + '\n')
    except Exception as error:
        (directory / 'gradle-log-tail.txt').write_text(repr(error) + '\n')

    candidates = [row for row in rows if 'Gradle Test Executor' in row['args']]
    if not candidates:
        candidates = [row for row in rows if 'GradleWorkerMain' in row['args']]
    worker_records = []
    for worker in candidates:
        pid = worker['pid']
        record = dict(worker)
        try:
            record['executable'] = os.path.realpath(f'/proc/{pid}/exe')
        except Exception as error:
            record['executableError'] = repr(error)
        try:
            cmdline = pathlib.Path(f'/proc/{pid}/cmdline').read_bytes()
            record['cmdline'] = cmdline.replace(b'\0', b' ').decode(errors='replace').strip()
        except Exception as error:
            record['cmdlineError'] = repr(error)

        jcmd_out = directory / f'jcmd-{pid}-Thread.print.stdout.txt'
        jcmd_err = directory / f'jcmd-{pid}-Thread.print.stderr.txt'
        try:
            with jcmd_out.open('wb') as stdout, jcmd_err.open('wb') as stderr:
                completed = subprocess.run([str(J21 / 'bin/jcmd'), str(pid), 'Thread.print', '-l'],
                                           stdout=stdout, stderr=stderr, timeout=15)
            record['jcmdExitCode'] = completed.returncode
        except subprocess.TimeoutExpired as error:
            record['jcmdExitCode'] = 'TIMEOUT_15S'
            record['jcmdTimeoutStdoutBytes'] = len(error.stdout or b'')
            record['jcmdTimeoutStderrBytes'] = len(error.stderr or b'')
        except Exception as error:
            record['jcmdError'] = repr(error)

        dump_ok = jcmd_out.exists() and jcmd_out.stat().st_size > 0 and record.get('jcmdExitCode') == 0
        if not dump_ok:
            jstack_out = directory / f'jstack-{pid}-l.stdout.txt'
            jstack_err = directory / f'jstack-{pid}-l.stderr.txt'
            try:
                with jstack_out.open('wb') as stdout, jstack_err.open('wb') as stderr:
                    completed = subprocess.run([str(J21 / 'bin/jstack'), '-l', str(pid)],
                                               stdout=stdout, stderr=stderr, timeout=15)
                record['jstackExitCode'] = completed.returncode
            except subprocess.TimeoutExpired as error:
                record['jstackExitCode'] = 'TIMEOUT_15S'
                record['jstackTimeoutStdoutBytes'] = len(error.stdout or b'')
                record['jstackTimeoutStderrBytes'] = len(error.stderr or b'')
            except Exception as error:
                record['jstackError'] = repr(error)
            dump_ok = jstack_out.exists() and jstack_out.stat().st_size > 0 and record.get('jstackExitCode') == 0

        if not dump_ok:
            try:
                os.kill(pid, signal.SIGQUIT)
                record['sigquitSent'] = True
                record['sigquitExitCode'] = 0
            except Exception as error:
                record['sigquitSent'] = False
                record['sigquitError'] = repr(error)
        worker_records.append(record)

    metadata['testWorkers'] = worker_records
    metadata['threadDumpCaptured'] = any(
        (directory / f'jcmd-{x["pid"]}-Thread.print.stdout.txt').exists()
        and (directory / f'jcmd-{x["pid"]}-Thread.print.stdout.txt').stat().st_size > 0
        for x in worker_records
    ) or any(
        (directory / f'jstack-{x["pid"]}-l.stdout.txt').exists()
        and (directory / f'jstack-{x["pid"]}-l.stdout.txt').stat().st_size > 0
        for x in worker_records
    )
    (directory / 'snapshot.json').write_text(json.dumps(metadata, indent=2) + '\n')
    say(f'SNAPSHOT label={label} reason={reason} events={len(current_events)} workerPids={[x["pid"] for x in worker_records]} threadDump={metadata["threadDumpCaptured"]}')
    return metadata


def stop_group(proc, reason):
    say(f'STOP_REQUEST reason={reason} wrapperPid={proc.pid} pgid={proc.pid}')
    for sig, wait_seconds in ((signal.SIGINT, 8), (signal.SIGTERM, 5), (signal.SIGKILL, 3)):
        if proc.poll() is not None:
            break
        try:
            os.killpg(proc.pid, sig)
            say(f'SIGNAL_SENT signal={sig.name} pgid={proc.pid}')
        except ProcessLookupError:
            break
        try:
            proc.wait(timeout=wait_seconds)
        except subprocess.TimeoutExpired:
            say(f'SIGNAL_WAIT_EXPIRED signal={sig.name}')
    try:
        proc.wait(timeout=3)
    except subprocess.TimeoutExpired:
        pass


def main():
    if WT.joinpath('.git').exists() is False and not (WT / '.git').is_file():
        raise RuntimeError('Target path is not a Git worktree')
    head = subprocess.check_output(['git', '-C', str(WT), 'rev-parse', 'HEAD'], text=True).strip()
    if head != SOURCE_SHA:
        raise RuntimeError(f'Unexpected source SHA {head}')
    tracked_status = subprocess.check_output(['git', '-C', str(WT), 'status', '--porcelain=v1'], text=True)
    if tracked_status.strip():
        raise RuntimeError('Tracked worktree is not clean')
    if EVENTS.exists():
        EVENTS.unlink()

    env = os.environ.copy()
    env['JAVA_HOME'] = str(J17)
    env['PATH'] = str(J17 / 'bin') + os.pathsep + env.get('PATH', '')
    env['ANDROID_HOME'] = str(SDK)
    env['ANDROID_SDK_ROOT'] = str(SDK)
    env['UX02E_TEST_EVENTS'] = str(EVENTS)

    before_xml = {}
    for path in (WT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
        before_xml[str(path)] = {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                                 'mtimeNs': path.stat().st_mtime_ns, 'bytes': path.stat().st_size}
    (EV / 'xml-before.json').write_text(json.dumps(before_xml, indent=2) + '\n')
    (EV / 'command.txt').write_text(' '.join(ARGV) + '\n')
    (EV / 'environment.txt').write_text('\n'.join([
        f'JAVA_HOME={env["JAVA_HOME"]}', f'PATH_prefix={J17 / "bin"}',
        f'ANDROID_HOME={env["ANDROID_HOME"]}', f'ANDROID_SDK_ROOT={env["ANDROID_SDK_ROOT"]}',
        f'GRADLE_USER_HOME={env.get("GRADLE_USER_HOME", "<default /home/agent/.gradle>")}',
        f'UX02E_TEST_EVENTS={env["UX02E_TEST_EVENTS"]}',
        'Gradle runtime JDK=Temurin 17.0.20.1', 'App test-worker JDK=Temurin 21.0.12.1 (configured by app/build.gradle.kts)',
        'Android emulator/device=NOT_USED', 'APK=NOT_GENERATED', 'network=disabled at Gradle dependency resolution via --offline',
        'No install/download/cache cleanup/source copy requested.',
    ]) + '\n')

    started_utc = utc()
    started_mono = time.monotonic()
    argv_json = json.dumps(ARGV)
    (EV / 'run-start.json').write_text(json.dumps({
        'sourceCodeSha': SOURCE_SHA, 'startedAtUtc': started_utc, 'workingDirectory': str(WT),
        'argv': ARGV, 'filters': FILTERS, 'maxWallSeconds': MAX_SECONDS,
        'stallQuietSeconds': QUIET_SECONDS, 'confirmationSeconds': CONFIRM_SECONDS,
    }, indent=2) + '\n')
    LOG.parent.mkdir(parents=True, exist_ok=True)
    say(f'RUN_START source={SOURCE_SHA} maxSeconds={MAX_SECONDS:.0f} argv={argv_json}')
    reason = 'NATURAL_EXIT'
    captures = []
    pending_capture_at = None
    pending_event_count = None
    last_event_count = 0
    last_event_mono = None
    last_heartbeat = started_mono
    with LOG.open('wb') as output:
        proc = subprocess.Popen(ARGV, cwd=WT, env=env, stdout=output, stderr=subprocess.STDOUT,
                                start_new_session=True)
        say(f'GRADLE_PROCESS pid={proc.pid} pgid={proc.pid}')
        while True:
            rc = proc.poll()
            if rc is not None:
                reason = 'NATURAL_EXIT'
                break
            now = time.monotonic()
            elapsed = now - started_mono
            current_events = events()
            count = len(current_events)
            if count > last_event_count:
                last_event_count = count
                last_event_mono = now
                if pending_capture_at is not None:
                    say(f'PROGRESS_AFTER_STALL_SNAPSHOT newEvents={count-pending_event_count}; stall-confirmation-cancelled')
                    pending_capture_at = None
                    pending_event_count = None
            if last_event_mono is not None and now - last_event_mono >= QUIET_SECONDS and pending_capture_at is None:
                captures.append(capture('stall-first', f'no TEST_START/TEST_END for {QUIET_SECONDS:.0f}s after first test event', proc, started_mono))
                pending_capture_at = time.monotonic()
                pending_event_count = count
            if pending_capture_at is not None and now - pending_capture_at >= CONFIRM_SECONDS and count == pending_event_count:
                captures.append(capture('stall-confirmation', f'no test-event progress for {CONFIRM_SECONDS:.0f}s after first snapshot', proc, started_mono))
                reason = 'NO_TEST_EVENT_PROGRESS_120S_PLUS_60S; DIAGNOSTICS_CAPTURED_BEFORE_STOP'
                stop_group(proc, reason)
                break
            if elapsed >= MAX_SECONDS - CAPTURE_RESERVE_SECONDS:
                captures.append(capture('deadline-final', f'{MAX_SECONDS:.0f}s absolute wall deadline safety capture', proc, started_mono))
                reason = 'ABSOLUTE_300S_DEADLINE; FINAL_DIAGNOSTICS_CAPTURED_BEFORE_STOP'
                stop_group(proc, reason)
                break
            if now - last_heartbeat >= 30:
                say(f'HEARTBEAT elapsedSeconds={elapsed:.0f} events={count} activeTest={(current_events[-1] if current_events else "NONE")}')
                last_heartbeat = now
            time.sleep(1)

    finished_utc = utc()
    elapsed = round(time.monotonic() - started_mono, 3)
    final_events = events()
    starts = [line for line in final_events if '\tTEST_START\t' in line]
    ends = [line for line in final_events if '\tTEST_END\t' in line]
    summary = {
        'sourceCodeSha': SOURCE_SHA, 'workingDirectory': str(WT), 'argv': ARGV,
        'startedAtUtc': started_utc, 'finishedAtUtc': finished_utc,
        'durationSeconds': elapsed, 'wrapperPid': proc.pid,
        'exitCode': proc.returncode, 'stopReason': reason,
        'maxWallSeconds': MAX_SECONDS, 'testStartEvents': len(starts), 'testEndEvents': len(ends),
        'unclosedTestStarts': [line for line in starts if not any(
            ('\tTEST_END\t' + '\t'.join(line.split('\t')[2:4])) in end for end in ends)],
        'testEventsPath': 'test-events.tsv', 'captures': captures,
        'targetMethod': TARGET,
        'expectedPrefixFromPriorEventLog': 36,
        'observedGradleConclusion': 'BUILD SUCCESSFUL' if 'BUILD SUCCESSFUL' in LOG.read_text(errors='replace') else ('BUILD FAILED' if 'BUILD FAILED' in LOG.read_text(errors='replace') else 'NOT_EMITTED'),
    }
    (EV / 'run-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    say(f'RUN_END exitCode={proc.returncode} durationSeconds={elapsed} reason={reason} TEST_START={len(starts)} TEST_END={len(ends)} conclusion={summary["observedGradleConclusion"]}')
    return 0 if proc.returncode == 0 else 1

if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print(f'SUPERVISOR_FATAL {type(error).__name__}: {error}', file=sys.stderr, flush=True)
        raise
