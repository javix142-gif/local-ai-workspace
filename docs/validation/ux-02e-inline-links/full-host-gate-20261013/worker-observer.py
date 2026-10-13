#!/usr/bin/env python3
import datetime,gzip,json,os,pathlib,subprocess,time
EV=pathlib.Path('/workspace/local-ai-workspace/docs/validation/ux-02e-inline-links/full-host-gate-20261013')
EVENTS=EV/'test-events.tsv'; LOG=EV/'gradle-full-gate.log'
ROOTPID=133734; JCMD='/workspace/.toolchain/jdk-21-temurin/bin/jcmd'
IDLE=420; SECOND=60; started=time.monotonic(); snap=0; pending=None; first_count=None; last_count=0; last_seen=None

def utc(): return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds').replace('+00:00','Z')
def parse():
 rows=[]
 if EVENTS.exists():
  for l in EVENTS.read_text(errors='replace').splitlines():
   p=l.split('\t')
   if len(p)>=2 and p[1] in ('TEST_START','TEST_END'):
    try: ts=datetime.datetime.fromisoformat(p[0].replace('Z','+00:00')).timestamp()
    except: ts=0
    rows.append((ts,l))
 return rows

def descendants(rootpid):
 cp=subprocess.run(['ps','-ww','-eo','pid,ppid,pgid,stat,etime,args'],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=8)
 lines=cp.stdout.splitlines(); procs={}
 for line in lines[1:]:
  q=line.strip().split(None,5)
  if len(q)>=6:
   try: procs[int(q[0])]={'ppid':int(q[1]),'pgid':int(q[2]),'stat':q[3],'etime':q[4],'args':q[5]}
   except: pass
 ids={rootpid}; changed=True
 while changed:
  changed=False
  for pid,info in procs.items():
   if info['ppid'] in ids and pid not in ids: ids.add(pid); changed=True
 return '\n'.join([lines[0]]+[f"{pid} {procs[pid]['ppid']} {procs[pid]['pgid']} {procs[pid]['stat']} {procs[pid]['etime']} {procs[pid]['args']}" for pid in sorted(ids) if pid in procs]),[(pid,info) for pid,info in procs.items() if pid in ids and ('GradleWorkerMain' in info['args'] or 'Gradle Test Executor' in info['args'])]

def capture(label,reason,rows):
 d=EV/label;d.mkdir(parents=True,exist_ok=True)
 tree,workers=descendants(ROOTPID)
 (d/'process-tree.txt').write_text(tree+'\n')
 (d/'events-tail.tsv').write_text('\n'.join(x[1] for x in rows[-80:])+'\n')
 log=LOG.read_text(errors='replace').splitlines() if LOG.exists() else []
 (d/'gradle-log-tail.txt').write_text('\n'.join(log[-120:])+'\n')
 info=[]
 for pid,p in workers:
  rec={'pid':pid,'ppid':p['ppid'],'executable':os.path.realpath(f'/proc/{pid}/exe'),'args':p['args']}
  try: rec['cmdline']=pathlib.Path(f'/proc/{pid}/cmdline').read_bytes().replace(b'\0',b' ').decode(errors='replace').strip()
  except Exception as e: rec['cmdlineError']=repr(e)
  jf=d/f'jcmd-{pid}-Thread.print.txt'
  try:
   with jf.open('w',encoding='utf-8') as out:
    cp=subprocess.run([JCMD,str(pid),'Thread.print','-l'],stdout=out,stderr=subprocess.STDOUT,timeout=18,text=True)
   rec['jcmdExitCode']=cp.returncode
  except Exception as e: rec['jcmdError']=repr(e);jf.write_text(repr(e)+'\n')
  info.append(rec)
 meta={'capturedAtUtc':utc(),'sourceCodeSha':'a1827fa204cda0cdf80a7e5a51670435e8c8d4b7','wrapperPid':ROOTPID,'reason':reason,'testEventCount':len(rows),'lastEvent':rows[-1][1] if rows else None,'workers':info,'jcmdPath':JCMD}
 (d/'snapshot.json').write_text(json.dumps(meta,indent=2)+'\n')
 for p in list(d.glob('*.txt')):
  with p.open('rb') as src, p.with_suffix(p.suffix+'.gz').open('wb') as raw:
   with gzip.GzipFile(fileobj=raw,mode='wb',compresslevel=9,mtime=0) as out: out.write(src.read())
  p.unlink()
 print(f'{utc()} OBSERVER_SNAPSHOT {label} reason={reason} testEvents={len(rows)} workers={[x["pid"] for x in info]}',flush=True)

def main():
 global pending,first_count,snap,last_count,last_seen
 print(f'{utc()} OBSERVER_START wrapperPid={ROOTPID} idleSeconds={IDLE}',flush=True)
 while True:
  rows=parse(); count=len(rows); now=time.time(); mono=time.monotonic()
  if count>last_count:
   last_count=count;last_seen=rows[-1][0]
   if pending is not None:
    print(f'{utc()} OBSERVER_PROGRESS_AFTER_FIRST count={count}; cancel second capture',flush=True);pending=None;first_count=None
  try: os.kill(ROOTPID,0)
  except ProcessLookupError:
   print(f'{utc()} OBSERVER_END wrapper-exited',flush=True);return
  idle=now-(last_seen if last_seen else now-(mono-started))
  if pending is not None and mono-pending>=SECOND and count==first_count:
   snap+=1;capture(f'observer-stall-{snap:02d}-second','60 seconds after first capture with no new JUnit test event',rows)
   print(f'{utc()} OBSERVER_SECOND_CAPTURE_COMPLETE',flush=True);return
  if idle>=IDLE and pending is None:
   snap+=1;capture(f'observer-stall-{snap:02d}-first',f'no TEST_START/TEST_END for {IDLE}s',rows)
   pending=time.monotonic();first_count=count
  if mono-started>=2080:
   snap+=1;capture(f'observer-deadline-{snap:02d}','35-minute deadline safety capture',rows);return
  time.sleep(3)
if __name__=='__main__':main()
