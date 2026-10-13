#!/usr/bin/env python3
import datetime, hashlib, json, os, pathlib, signal, subprocess, sys, time
WT=pathlib.Path('/workspace/ux02e-a182-jdk17gradle-contract')
EV=pathlib.Path('/workspace/local-ai-workspace/docs/validation/ux-02e-inline-links/full-host-gate-20261013')
J17=pathlib.Path('/workspace/.toolchain/jdk-17')
J21=pathlib.Path('/workspace/.toolchain/jdk-21-temurin')
SDK=pathlib.Path('/workspace/.toolchain/android-sdk')
INIT=pathlib.Path('/tmp/ux02e-full-gate-20261013.init.gradle')
EVENTS=EV/'test-events.tsv'
LOG=EV/'gradle-full-gate.log'
SUP=EV/'supervisor.log'
MAX=2100.0
IDLE=420.0
SECOND=60.0

def utc(): return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds').replace('+00:00','Z')
def say(s):
    line=f'{utc()} {s}'
    print(line,flush=True)
    with SUP.open('a',encoding='utf-8') as f: f.write(line+'\n')
def read_test_events():
    out=[]
    if EVENTS.exists():
        for ln in EVENTS.read_text(errors='replace').splitlines():
            p=ln.split('\t')
            if len(p)>=2 and p[1] in ('TEST_START','TEST_END'):
                try: t=datetime.datetime.fromisoformat(p[0].replace('Z','+00:00')).timestamp()
                except Exception: t=0
                out.append((t,ln))
    return out

def proc_rows(pgid):
    cp=subprocess.run(['ps','-ww','-eo','pid,ppid,pgid,stat,etime,args'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=8)
    lines=cp.stdout.splitlines()
    kept=[lines[0]] if lines else []
    workers=[]
    for line in lines[1:]:
        parts=line.strip().split(None,5)
        if len(parts)<6: continue
        try: pid,ppid,gid=int(parts[0]),int(parts[1]),int(parts[2])
        except Exception: continue
        if gid==pgid:
            kept.append(line)
            args=parts[5]
            if 'GradleWorkerMain' in args or 'Gradle Test Executor' in args:
                workers.append((pid,args))
    return '\n'.join(kept)+'\n',workers,cp.returncode

def capture(label,reason,proc,started_mono):
    dest=EV/label; dest.mkdir(parents=True,exist_ok=True)
    now=time.monotonic()
    ev=read_test_events()
    metadata={'capturedAtUtc':utc(),'reason':reason,'elapsedSeconds':round(now-started_mono,3),'gradleWrapperPid':proc.pid,'testEventCount':len(ev),'lastTestEvent':ev[-1][1] if ev else None,'sourceCodeSha':'a1827fa204cda0cdf80a7e5a51670435e8c8d4b7'}
    try:
        tree,workers,code=proc_rows(proc.pid)
        (dest/'process-tree.txt').write_text(tree)
        metadata['processTreeExitCode']=code
    except Exception as e:
        workers=[]; metadata['processTreeError']=repr(e)
        (dest/'process-tree.txt').write_text(repr(e)+'\n')
    (dest/'test-events-tail.tsv').write_text('\n'.join(line for _,line in ev[-80:]) + ('\n' if ev else ''))
    try:
        text=LOG.read_text(errors='replace').splitlines()
        (dest/'gradle-log-tail.txt').write_text('\n'.join(text[-120:])+'\n')
    except Exception as e: (dest/'gradle-log-tail.txt').write_text(repr(e)+'\n')
    runtime=[]
    for pid,args in workers:
        rec={'pid':pid,'args':args}
        try: rec['exe']=os.path.realpath(f'/proc/{pid}/exe')
        except Exception as e: rec['exeError']=repr(e)
        try: rec['cmdline']=pathlib.Path(f'/proc/{pid}/cmdline').read_bytes().replace(b'\0',b' ').decode(errors='replace').strip()
        except Exception as e: rec['cmdlineError']=repr(e)
        runtime.append(rec)
        jcmd=J21/'bin'/'jcmd'
        dump=dest/f'jcmd-{pid}-Thread.print.txt'
        if jcmd.exists():
            try:
                with dump.open('w',encoding='utf-8') as out:
                    cp=subprocess.run([str(jcmd),str(pid),'Thread.print','-l'],stdout=out,stderr=subprocess.STDOUT,text=True,timeout=18)
                rec['jcmdPath']=str(jcmd); rec['jcmdExitCode']=cp.returncode
            except Exception as e:
                rec['jcmdPath']=str(jcmd); rec['jcmdError']=repr(e)
                dump.write_text(repr(e)+'\n')
        else: rec['jcmdError']='JDK21 jcmd executable unavailable'
    metadata['workers']=runtime
    (dest/'snapshot.json').write_text(json.dumps(metadata,indent=2)+'\n')
    (dest/'jcmd-path.txt').write_text(str(J21/'bin'/'jcmd')+'\n')
    say(f'SNAPSHOT {label} reason={reason} testEvents={len(ev)} workers='+','.join(str(x['pid']) for x in runtime))
    # preserve potentially whitespace-bearing diagnostics losslessly, avoiding text artifacts with irrelevant trailing spaces
    for p in list(dest.glob('*.txt')):
        if p.name in ('jcmd-path.txt',): continue
        gz=pathlib.Path(str(p)+'.gz')
        try:
            import gzip
            with p.open('rb') as src, gzip.open(gz,'wb',compresslevel=9,mtime=0) as dst: dst.write(src.read())
            p.unlink()
        except TypeError:
            import gzip
            with p.open('rb') as src, gz.open('wb') as raw:
                with gzip.GzipFile(fileobj=raw,mode='wb',compresslevel=9,mtime=0) as dst: dst.write(src.read())
            p.unlink()
    return len(ev)

def stop_group(proc,why):
    say(f'STOP_REQUEST reason={why} pid={proc.pid}')
    for sig,wait in ((signal.SIGINT,18),(signal.SIGTERM,8),(signal.SIGKILL,3)):
        if proc.poll() is not None: break
        try: os.killpg(proc.pid,sig)
        except ProcessLookupError: break
        except Exception as e: say(f'SIGNAL_ERROR signal={sig.name} error={e!r}')
        try: proc.wait(timeout=wait)
        except subprocess.TimeoutExpired: say(f'SIGNAL_WAIT_EXPIRED signal={sig.name}')
    try: proc.wait(timeout=3)
    except subprocess.TimeoutExpired: pass

def main():
    env=os.environ.copy()
    env['JAVA_HOME']=str(J17); env['PATH']=str(J17/'bin')+os.pathsep+env.get('PATH','')
    env['ANDROID_HOME']=str(SDK); env['ANDROID_SDK_ROOT']=str(SDK); env['UX02E_TEST_EVENTS']=str(EVENTS)
    argv=['./gradlew','--no-daemon','--no-parallel','--max-workers=1','--console=plain','--info','--rerun-tasks','--init-script',str(INIT),f'-Porg.gradle.java.installations.paths={J17},{J21}',':app:testDebugUnitTest',':litert-compat:testDebugUnitTest',':app:lintDebug',':app:compileDebugAndroidTestKotlin']
    start_wall=utc(); start=time.monotonic()
    (EV/'run-start.txt').write_text(f'startedAtUtc={start_wall}\nsourceCodeSha=a1827fa204cda0cdf80a7e5a51670435e8c8d4b7\n')
    say(f'RUN_START source=a1827fa204cda0cdf80a7e5a51670435e8c8d4b7 pid=starting maxSeconds={MAX:.0f}')
    with LOG.open('wb') as out:
        proc=subprocess.Popen(argv,cwd=WT,env=env,stdout=out,stderr=subprocess.STDOUT,start_new_session=True)
        say(f'GRADLE_PROCESS pid={proc.pid} pgid={proc.pid} argv={" ".join(argv)}')
        last_seen_count=0; last_event_mono=start; last_heartbeat=start
        pending_second=None; first_snap_count=None; snapshot_idx=0; reason=None
        hard_capture_done=False
        while True:
            rc=proc.poll()
            if rc is not None:
                reason='NATURAL_EXIT'; break
            now=time.monotonic(); elapsed=now-start
            events=read_test_events(); count=len(events)
            if count>last_seen_count:
                last_seen_count=count; last_event_mono=now
                if pending_second is not None:
                    say(f'PROGRESS_AFTER_FIRST_STALL_CAPTURE newTestEvents={count-first_snap_count}; stall-stop cancelled')
                    pending_second=None; first_snap_count=None
            if elapsed>=2080 and not hard_capture_done:
                capture('deadline-final','absolute 35-minute deadline approaching',proc,start)
                hard_capture_done=True
                reason='ABSOLUTE_TIMEOUT_35_MINUTES'
                stop_group(proc,reason)
                break
            if pending_second is not None and now-pending_second>=SECOND and count==first_snap_count:
                snapshot_idx+=1
                capture('stall-second','60 seconds after first stalled snapshot; no new JUnit test event',proc,start)
                reason='NO_TEST_EVENT_7_MINUTES_PLUS_60_SECONDS; two diagnostic snapshots captured'
                stop_group(proc,reason)
                break
            if now-last_event_mono>=IDLE and pending_second is None:
                snapshot_idx+=1
                label='stall-first' if snapshot_idx==1 else f'stall-first-{snapshot_idx}'
                first_snap_count=capture(label,f'no new TEST_START/TEST_END event for {IDLE:.0f} seconds',proc,start)
                pending_second=time.monotonic()
                last_event_mono=now
            if now-last_heartbeat>=60:
                last=events[-1][1] if events else 'NONE'
                say(f'HEARTBEAT elapsedSeconds={elapsed:.0f} testEvents={count} secondsSinceLastTestEvent={now-last_event_mono:.0f} lastEvent={last}')
                last_heartbeat=now
            time.sleep(min(3.0,max(.2,MAX-elapsed)))
    end=utc(); elapsed=time.monotonic()-start
    rc=proc.returncode
    summary={'sourceCodeSha':'a1827fa204cda0cdf80a7e5a51670435e8c8d4b7','workingDirectory':str(WT),'argv':argv,'startedAtUtc':start_wall,'finishedAtUtc':end,'durationSeconds':round(elapsed,3),'wrapperPid':proc.pid,'exitCode':rc,'controllerStopReason':reason,'testEventCount':len(read_test_events()),'maxWallSeconds':MAX,'stallThresholdSeconds':IDLE,'secondCaptureDelaySeconds':SECOND}
    (EV/'run-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    say(f'RUN_END exitCode={rc} durationSeconds={elapsed:.3f} reason={reason} testEvents={summary["testEventCount"]}')
    return 0 if rc==0 else 1
if __name__=='__main__': sys.exit(main())
