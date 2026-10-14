import os,sys,json,subprocess,time,pathlib,signal
root=pathlib.Path('/workspace/local-ai-workspace');out=pathlib.Path('/workspace/visual-refinement-run-20261013');mode=sys.argv[1]
env=os.environ.copy();env.update(JAVA_HOME='/workspace/.toolchain/jdk-17',ANDROID_HOME='/workspace/.toolchain/android-sdk',ANDROID_SDK_ROOT='/workspace/.toolchain/android-sdk');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
cmd=['./gradlew','--no-daemon','--no-parallel','--max-workers=1','--offline','--console=plain','-Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin','-Parm64Only=true']
if mode=='build':
    cmd+=[':app:assembleDebug',':app:compileDebugUnitTestKotlin',':app:lintDebug',':app:compileDebugAndroidTestKotlin'];limit=1200
else:
    cmd+=['--info','--init-script',str(out/'events.init.gradle'),':app:testDebugUnitTest']
    for name in (['WorkspaceRefinementUiTest.compactWorkspaceKeepsBothCreationActionsAndProjectDialog'] if mode=='root-probe' else ['WorkspaceDialogLegacyDiagnosticTest'] if mode=='legacy-dialog' else ['ChatRefinementUiTest','WorkspaceRefinementUiTest','LocalAiThemeTest','ChatVisualSurfacesUiTest','ChatHeaderComposerCompactCaptureTest','ChatHeaderComposerLargeFontCaptureTest','MainActivityThemeCaptureTest','MainActivityDarkThemeCaptureTest']):
        cmd+=['--tests','com.localai.workspace.ui.'+name]
    limit=300
record={'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'command':cmd,'environment':{k:env[k] for k in ['JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT']},'emulatorInstalled':pathlib.Path(env['ANDROID_HOME']+'/emulator/emulator').is_file(),'localKvm':pathlib.Path('/dev/kvm').exists(),'startedAtUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'wallLimitSeconds':limit}
(out/(mode+'-command.json')).write_text(json.dumps(record,indent=2)+'\n');start=time.monotonic()
baseline_workers=set()
for item in pathlib.Path('/proc').iterdir():
    if item.name.isdigit():
        try:
            if b'Gradle Test Executor' in (item/'cmdline').read_bytes():baseline_workers.add(int(item.name))
        except OSError:pass
with (out/(mode+'.log')).open('w') as log:
    p=subprocess.Popen(cmd,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    try:
        if mode=='focused':
            captured=False
            while p.poll() is None and time.monotonic()-start<limit:
                if time.monotonic()-start>240 and not captured:
                    captured=True
                    for item in pathlib.Path('/proc').iterdir():
                        if not item.name.isdigit() or int(item.name) in baseline_workers:continue
                        try:active=b'Gradle Test Executor' in (item/'cmdline').read_bytes()
                        except OSError:continue
                        if active:
                            with (out/('thread-dump-'+item.name+'.txt')).open('w') as dump:
                                try:
                                    d=subprocess.run(['/workspace/.toolchain/jdk-21-temurin/bin/jcmd',item.name,'Thread.print','-l'],stdout=dump,stderr=subprocess.STDOUT,timeout=10)
                                    dump.write('\nexit_code='+str(d.returncode)+'\n')
                                except subprocess.TimeoutExpired:dump.write('\nexit_code=124\n')
                time.sleep(1)
            if p.poll() is None:raise subprocess.TimeoutExpired(cmd,limit)
        code=p.wait(timeout=limit)
    except subprocess.TimeoutExpired:
        code=124;os.killpg(p.pid,signal.SIGTERM)
        try:p.wait(timeout=10)
        except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait()
record.update(exitCode=code,durationSeconds=round(time.monotonic()-start,3),endedAtUtc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()))
(out/(mode+'-result.json')).write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2),flush=True);sys.exit(code)
