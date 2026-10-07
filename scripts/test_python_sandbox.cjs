// Executes the pinned CPython/WASM engine and production policy, not a fake Python executor.
// Node verifies the policy/memory/time boundary; Android WebView/CSP still requires device tests.
const {Worker,isMainThread,parentPort,workerData}=require('node:worker_threads');
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const base=path.resolve(__dirname,'../app/src/main/assets/python');
if(!isMainThread){
 require(path.join(base,'limits.js')).enforceWasmMemoryCap();
 const {loadPyodide}=require(path.join(base,'pyodide.js'));
 (async()=>{
  const p=await loadPyodide({indexURL:base+'/'});let out='',overflow=false;
  p.setStdout({batched:s=>{if(out.length+s.length>16000){overflow=true;return;}out+=s+'\n';}});
  p.runPython(fs.readFileSync(path.join(base,'policy.py'),'utf8'));
  parentPort.postMessage({ready:true});
  parentPort.on('message',code=>{
   const run=p.globals.get('_run_json');let r=JSON.parse(run(code));run.destroy();if(overflow)r.status='OUTPUT_LIMIT';r.stdout=out;out='';overflow=false;parentPort.postMessage(r);
  });
 })().catch(e=>{parentPort.postMessage({error:String(e)});});
}else{
 (async()=>{
  const worker=new Worker(__filename);let pending;
  const ready=new Promise((resolve,reject)=>{worker.on('message',m=>{if(m.ready)resolve();else if(m.error&&!m.status)reject(Error(m.error));else if(pending){const {resolve}=pending;pending=null;resolve(m);}});worker.on('error',reject);});
  await ready;
  const run=(code,timeout=10000)=>new Promise((resolve,reject)=>{const timer=setTimeout(()=>{worker.terminate();reject(Error('TIMEOUT'));},timeout);pending={resolve:m=>{clearTimeout(timer);resolve(m);}};worker.postMessage(code);});
  const results=[];
  let r=await run('import math,statistics,json,csv,datetime,collections\nprint(math.sqrt(256)+statistics.mean([1,2,3]))');assert.equal(r.status,'SUCCESS');assert.equal(r.stdout.trim(),'18.0');results.push('stdlib execution');console.error('PASS stdlib');
  r=await run('with open("/work/test.txt","w") as f: f.write("safe")\nwith open("/work/test.txt") as f: print(f.read())');assert.equal(r.status,'SUCCESS');assert.equal(r.stdout.trim(),'safe');results.push('virtual filesystem');console.error('PASS virtual FS');
  r=await run('open("/etc/passwd").read()');assert.equal(r.status,'POLICY_REJECTED');results.push('host filesystem denied');console.error('PASS host FS denied');
  r=await run('import urllib.request\nprint(urllib.request.urlopen("https://example.com").read())');assert.equal(r.status,'POLICY_REJECTED');results.push('network module denied');console.error('PASS network denied');
  r=await run('import subprocess\nsubprocess.run(["sh"])');assert.equal(r.status,'POLICY_REJECTED');results.push('subprocess denied');console.error('PASS subprocess denied');
  r=await run('print("x"*20000)');assert.equal(r.status,'OUTPUT_LIMIT');results.push('output limit');console.error('PASS output limit');
  r=await run('x=[0]*80000000\nprint(len(x))');assert.equal(r.status,'MEMORY_LIMIT');results.push('256 MiB WASM heap cap');console.error('PASS WASM memory cap');
  await assert.rejects(run('while True: pass',300),/TIMEOUT/);results.push('external worker timeout');
  console.log(JSON.stringify({runtime:'Pyodide 0.27.7 / CPython 3.12.7',environment:'Node/Linux, not Android',passed:results.length,tests:results}));
 })().catch(e=>{console.error(e);process.exit(1);});
}
