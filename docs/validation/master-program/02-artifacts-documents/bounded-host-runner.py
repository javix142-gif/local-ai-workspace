import subprocess,os,time,signal,json,pathlib,sys
root=pathlib.Path('/workspace/local-ai-workspace');dest=pathlib.Path('/workspace/master-program-run')/sys.argv[1];dest.mkdir(exist_ok=True)
cmd=['./gradlew','--no-daemon','--no-parallel','--max-workers=1','--offline','--console=plain','--info','-Porg.gradle.java.installations.paths=/workspace/.toolchain/jdk-17,/workspace/.toolchain/jdk-21-temurin','-Parm64Only=true','--init-script','/workspace/master-program-run/test-events.init.gradle']+sys.argv[2:]
env=os.environ.copy();env.update(JAVA_HOME='/workspace/.toolchain/jdk-17',ANDROID_HOME='/workspace/.toolchain/android-sdk',ANDROID_SDK_ROOT='/workspace/.toolchain/android-sdk');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
started=time.time();sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip();(dest/'command.json').write_text(json.dumps({'sha':sha,'command':cmd,'startEpoch':started,'JAVA_HOME':env['JAVA_HOME'],'SDK':env['ANDROID_HOME'],'workerJdk':'/workspace/.toolchain/jdk-21-temurin'},indent=2))
with (dest/'gradle.log').open('w') as log:
 p=subprocess.Popen(cmd,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
 try:code=p.wait(timeout=(900 if ":app:assembleDebug" in cmd else 300))
 except subprocess.TimeoutExpired:
  snapshot=subprocess.run(['ps','-eo','pid,ppid,args'],capture_output=True,text=True);(dest/'processes.txt').write_text(snapshot.stdout)
  for line in snapshot.stdout.splitlines():
   if 'Gradle Test Executor' in line and 'jdk-21' in line:
    pid=line.split()[0]
    try:d=subprocess.run(['/workspace/.toolchain/jdk-21-temurin/bin/jcmd',pid,'Thread.print','-l'],timeout=15,capture_output=True,text=True);(dest/('thread-'+pid+'.txt')).write_text(d.stdout+d.stderr)
    except subprocess.TimeoutExpired:(dest/('thread-'+pid+'.txt')).write_text('jcmd timeout')
  os.killpg(p.pid,signal.SIGTERM)
  try:p.wait(timeout=5)
  except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait()
  code=124
end=time.time();(dest/'result.json').write_text(json.dumps({'sha':sha,'exitCode':code,'durationSeconds':end-started,'endEpoch':end,'timeout':code==124},indent=2))
import shutil,xml.etree.ElementTree as E
counts={'tests':0,'failures':0,'errors':0,'skipped':0};files=[]
for module in ['app','litert-compat']:
 for f in (root/module/'build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
  if f.stat().st_mtime>=started:
   target=dest/'junit'/module/f.name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,target);d=E.parse(f).getroot();files.append(str(target.relative_to(dest)))
   for k in counts:counts[k]+=int(d.get(k,0))
(dest/'junit-summary.json').write_text(json.dumps({'files':files,'counts':counts},indent=2));print(json.dumps({'sha':sha,'exitCode':code,'duration':end-started,'counts':counts}),flush=True)
sys.exit(code)
