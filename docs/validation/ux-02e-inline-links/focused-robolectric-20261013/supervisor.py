#!/usr/bin/env python3
import datetime as dt, json, os, pathlib, signal, subprocess, sys, time

ROOT = pathlib.Path('/workspace/local-ai-workspace')
WT = pathlib.Path('/workspace/ux02e-isolated-diagnostic-20261013')
OUT = ROOT / 'docs/validation/ux-02e-inline-links/focused-robolectric-20261013'
TMP = pathlib.Path('/tmp/ux02e-focused-robolectric-20261013')
JDK17 = pathlib.Path('/workspace/.toolchain/jdk-17')
JDK21 = pathlib.Path('/workspace/.toolchain/jdk-21-temurin')
EVENTS = OUT / 'test-events.tsv'
LOG = OUT / 'gradle-focused.log'
TARGET_CLASS = 'com.localai.workspace.context.ContextFoundationDeduplicationTest'
TARGET_METHOD = 'productionFoundationPreservesDistinctDocumentProvenanceAndDeduplicatesRepeatedHit'
TARGET = TARGET_CLASS + '.' + TARGET_METHOD
CMD = [str(WT / 'gradlew'), '--no-daemon', '--no-parallel', '--max-workers=1', '--console=plain', '--info', '--rerun-tasks', '--init-script', str(TMP / 'test-events.init.gradle'), '-Porg.gradle.java.installations.paths=' + str(JDK17) + ',' + str(JDK21), ':app:testDebugUnitTest', '--tests', TARGET]

def utc(): return dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z')
def write_json(path, data): pathlib.Path(path).write_text(json.dumps(data, indent=2, ensure_ascii=False)+'\n')
def read_events():
    if not EVENTS.exists(): return []
    return EVENTS.read_text(errors='replace').splitlines()
def tail_log(n=100):
    if not LOG.exists(): return []
    with LOG.open('rb') as f:
        f.seek(0, os.SEEK_END); size=f.tell(); f.seek(max(0,size-200000)); data=f.read().decode('utf-8','replace')
    return data.splitlines()[-n:]
def proc_inventory():
    rows=[]
    for ent in pathlib.Path('/proc').iterdir():
        if not ent.name.isdigit(): continue
        try:
            pid=int(ent.name); raw=(ent/'cmdline').read_bytes(); cmd=raw.replace(b'\0',b' ').decode('utf-8','replace').strip()
            if not cmd or not any(t in cmd for t in ('GradleDaemon','GradleWorkerMain','Gradle Test Executor','gradle-wrapper','GradleWrapperMain')): continue
            stat=(ent/'stat').read_text(); rest=stat[stat.rfind(')')+2:].split()
            rows.append({'pid':pid,'state':rest[0] if rest else '?','ppid':int(rest[1]) if len(rest)>1 else None,'pgid':int(rest[2]) if len(rest)>2 else None,
                         'exe':os.readlink(ent/'exe') if (ent/'exe').exists() else '', 'cwd':os.readlink(ent/'cwd') if (ent/'cwd').exists() else '', 'cmdline':cmd})
        except (FileNotFoundError, PermissionError, ProcessLookupError, OSError, ValueError): pass
    return rows
def workers(rows, baseline_pids):
    # Never attach to a Gradle worker that predates this invocation. A test
    # worker is identified by Gradle's test-executor argument, not merely by
    # being a generic Gradle worker (which could be a Kotlin/compiler worker).
    return [r for r in rows if r['pid'] not in baseline_pids
            and 'GradleWorkerMain' in r['cmdline']
            and 'Gradle Test Executor' in r['cmdline']]
def save_snapshot(label, wrapper_pid):
    rows=proc_inventory()
    snap={'capturedAtUtc':utc(),'label':label,'wrapperPid':wrapper_pid,'processes':rows,'lastTestEvents':read_events()[-12:],'gradleLogTail':tail_log(80)}
    write_json(OUT/(label+'-process-snapshot.json'),snap)
    return rows
def run_attach(tool, pid, label, timeout_sec=20):
    path=JDK21/'bin'/tool
    cmd=[str(path),str(pid),'Thread.print','-l'] if tool=='jcmd' else [str(path),'-l',str(pid)]
    start=time.monotonic(); status={ 'command':cmd,'workerPid':pid,'tool':tool,'startedAtUtc':utc() }
    try:
        p=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout_sec)
        out,err=p.stdout,p.stderr; status.update({'exitCode':p.returncode,'timedOut':False})
    except subprocess.TimeoutExpired as e:
        out=e.stdout or b''; err=e.stderr or b''; status.update({'exitCode':124,'timedOut':True})
    status.update({'finishedAtUtc':utc(),'durationSeconds':round(time.monotonic()-start,3),'stdoutBytes':len(out),'stderrBytes':len(err)})
    (OUT/(label+'-'+tool+'-stdout.txt')).write_bytes(out)
    (OUT/(label+'-'+tool+'-stderr.txt')).write_bytes(err)
    write_json(OUT/(label+'-'+tool+'-status.json'),status)
    good=(status.get('exitCode')==0 and len(out)>0 and (b'Full thread dump' in out or b'JNI global references' in out or b'java.lang.Thread.State' in out))
    return good,status

def capture_diagnostics(label, wrapper_pid, baseline_pids, max_tool_timeout=20):
    rows=save_snapshot(label+'-before',wrapper_pid)
    ws=workers(rows, baseline_pids); result={'label':label,'capturedAtUtc':utc(),'workers':[]}
    for row in ws:
        pid=row['pid']; good,jstatus=run_attach('jcmd',pid,label+'-worker-'+str(pid),max_tool_timeout)
        worker_result={'pid':pid,'exe':row['exe'],'jcmd':jstatus}
        if not good:
            jgood,jstatus=run_attach('jstack',pid,label+'-worker-'+str(pid),max_tool_timeout)
            worker_result['jstack']=jstatus
        else: jgood=True
        # Preserve a directed SIGQUIT attempt and the Gradle output that may receive its dump.
        try:
            os.kill(pid,signal.SIGQUIT); sig={'pid':pid,'signal':'SIGQUIT','exitCode':0,'sentAtUtc':utc()}
        except OSError as e:
            sig={'pid':pid,'signal':'SIGQUIT','exitCode':1,'error':str(e),'sentAtUtc':utc()}
        worker_result['sigquit']=sig
        time.sleep(2)
        (OUT/(label+'-gradle-log-after-sigquit.tail.txt')).write_text('\n'.join(tail_log(120))+'\n')
        result['workers'].append(worker_result)
    if not ws: result['workerDiscovery']='No Gradle test worker was present in /proc at capture time.'
    save_snapshot(label+'-after',wrapper_pid)
    write_json(OUT/(label+'-diagnostics.json'),result)

def stop_only_this_run(p, baseline_pids):
    rows=proc_inventory(); owned=[]
    for r in rows:
        if r['pid']==p.pid or (r['pid'] not in baseline_pids and ('GradleDaemon' in r['cmdline'] or 'GradleWorkerMain' in r['cmdline'])):
            owned.append(r['pid'])
    outcomes=[]
    for pid in owned:
        try: os.kill(pid,signal.SIGINT); outcomes.append({'pid':pid,'signal':'SIGINT','ok':True})
        except OSError as e: outcomes.append({'pid':pid,'signal':'SIGINT','ok':False,'error':str(e)})
    deadline=time.monotonic()+5
    while time.monotonic()<deadline and p.poll() is None: time.sleep(.2)
    rows=proc_inventory(); still={r['pid'] for r in rows}
    for pid in owned:
        if pid in still:
            try: os.kill(pid,signal.SIGTERM); outcomes.append({'pid':pid,'signal':'SIGTERM','ok':True})
            except OSError as e: outcomes.append({'pid':pid,'signal':'SIGTERM','ok':False,'error':str(e)})
    deadline=time.monotonic()+4
    while time.monotonic()<deadline and p.poll() is None: time.sleep(.2)
    rows=proc_inventory(); still={r['pid'] for r in rows}
    for pid in owned:
        if pid in still:
            try: os.kill(pid,signal.SIGKILL); outcomes.append({'pid':pid,'signal':'SIGKILL','ok':True})
            except OSError as e: outcomes.append({'pid':pid,'signal':'SIGKILL','ok':False,'error':str(e)})
    if p.poll() is None:
        try: p.wait(timeout=3)
        except subprocess.TimeoutExpired: pass
    write_json(OUT/'termination-actions.json',{'timestampUtc':utc(),'ownedPids':owned,'actions':outcomes,'wrapperReturnCode':p.poll()})

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'test-events.init.gradle').write_bytes((TMP/'test-events.init.gradle').read_bytes())
    EVENTS.write_text('')
    env=os.environ.copy(); env.update({'JAVA_HOME':str(JDK17),'PATH':str(JDK17/'bin')+os.pathsep+env.get('PATH',''),
        'ANDROID_HOME':'/workspace/.toolchain/android-sdk','ANDROID_SDK_ROOT':'/workspace/.toolchain/android-sdk','UX02E_TEST_EVENTS':str(EVENTS)})
    baseline={r['pid'] for r in proc_inventory()}
    start=time.monotonic(); started=utc()
    (OUT/'command.txt').write_text(' '.join([f'{k}={v}' for k,v in [('JAVA_HOME',env['JAVA_HOME']),('ANDROID_HOME',env['ANDROID_HOME']),('ANDROID_SDK_ROOT',env['ANDROID_SDK_ROOT']),('UX02E_TEST_EVENTS',env['UX02E_TEST_EVENTS'])]])+' '+' '.join(CMD)+'\n')
    with LOG.open('wb') as log:
        p=subprocess.Popen(CMD,cwd=WT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        wrapper_pid=p.pid; last_count=0; last_progress=start; first_capture=False; final_capture=False; timed_out=False
        write_json(OUT/'run-start.json',{'startedAtUtc':started,'sourceCodeSha':subprocess.check_output(['git','-C',str(WT),'rev-parse','HEAD'],text=True).strip(),'wrapperPid':wrapper_pid,'command':CMD,'gradleUserHome':env.get('GRADLE_USER_HOME','/home/agent/.gradle')})
        while p.poll() is None:
            now=time.monotonic(); elapsed=now-start; ev=read_events(); count=len(ev)
            if count>last_count:
                last_count=count; last_progress=now
            quiet=now-last_progress
            if not first_capture and quiet>=120:
                capture_diagnostics('quiet-120s',wrapper_pid,baseline); first_capture=True; last_progress=time.monotonic()
            if not final_capture and elapsed>=245:
                # Leave enough time for both bounded attach attempts and scoped process shutdown before 300s.
                capture_diagnostics('deadline-capture',wrapper_pid,baseline); final_capture=True
            if elapsed>=300:
                timed_out=True
                stop_only_this_run(p,baseline)
                break
            time.sleep(.5)
        try: returncode=p.wait(timeout=3)
        except subprocess.TimeoutExpired:
            timed_out=True; stop_only_this_run(p,baseline); returncode=p.poll()
    finished=utc(); duration=round(time.monotonic()-start,3); ev=read_events()
    starts=[e for e in ev if '\tTEST_START\t' in e]; ends=[e for e in ev if '\tTEST_END\t' in e]
    active=[]
    for e in starts:
        f=e.split('\t')
        if len(f)>=4 and not any((z.split('\t')[2:4]==f[2:4]) for z in ends): active.append(f[2]+'.'+f[3])
    xmls=sorted((WT/'app/build/test-results/testDebugUnitTest').glob('*.xml')) if (WT/'app/build/test-results/testDebugUnitTest').exists() else []
    import xml.etree.ElementTree as ET
    xsum={'files':len(xmls),'tests':0,'failures':0,'errors':0,'skipped':0,'paths':[]}
    for q in xmls:
        root=ET.parse(q).getroot(); xsum['tests']+=int(root.attrib.get('tests',0)); xsum['failures']+=int(root.attrib.get('failures',0)); xsum['errors']+=int(root.attrib.get('errors',0)); xsum['skipped']+=int(root.attrib.get('skipped',0)); xsum['paths'].append(str(q))
    result={'sourceCodeSha':'a1827fa204cda0cdf80a7e5a51670435e8c8d4b7','startedAtUtc':started,'finishedAtUtc':finished,'durationSeconds':duration,'wrapperPid':wrapper_pid,'returnCode':returncode,'absoluteDeadlineHit':timed_out,'testStartEvents':len(starts),'testEndEvents':len(ends),'activeTestWithoutEnd':active,'xml':xsum,'taskResults':{'appTest':'PASS' if returncode==0 and xsum['tests']>0 and xsum['failures']==0 and xsum['errors']==0 else ('FAIL' if returncode==0 and (xsum['failures'] or xsum['errors']) else ('TIMEOUT' if timed_out else 'INCOMPLETE'))},'log':'gradle-focused.log','events':'test-events.tsv'}
    write_json(OUT/'run-result.json',result)
    print(json.dumps(result,indent=2))
    return 0
if __name__=='__main__': sys.exit(main())
