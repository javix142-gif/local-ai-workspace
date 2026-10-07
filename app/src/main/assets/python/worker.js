const bootstrapStarted=performance.now();importScripts('limits.js');enforceWasmMemoryCap();importScripts('pyodide.js');
let interpreter;let output='';let overflow=false;
(async()=>{
 try {
  interpreter=await loadPyodide({indexURL:new URL('./',self.location.href).href});
  interpreter.setStdout({batched: line=>{if(output.length+line.length+1>16000){if(!overflow)self.postMessage({kind:'result',result:{status:'OUTPUT_LIMIT',error:'Output exceeds 16000 characters',stdout:output}});overflow=true;return;}output+=line+'\n';}});
  interpreter.setStderr({batched: line=>{if(output.length+line.length+1<=16000)output+=line+'\n';}});
  const policy=await (await fetch('policy.py')).text();interpreter.runPython(policy);
  // No further fetches are necessary: no pip/package downloads or network access.
  Object.defineProperty(self,'fetch',{value:()=>Promise.reject(new Error('Network disabled')),writable:false,configurable:false});
  self.postMessage({kind:'ready',bootstrapMs:Math.round(performance.now()-bootstrapStarted)});
 } catch(error){self.postMessage({kind:'result',result:{status:'INITIALIZATION_ERROR',error:String(error).slice(0,1000)}});}
})();
self.onmessage=event=>{
 const start=performance.now();output='';overflow=false;
 try {
  const run=interpreter.globals.get('_run_json');let result;
  try {result=JSON.parse(run(event.data.code));} finally {run.destroy();}
  if(overflow)result={status:'OUTPUT_LIMIT',error:'Output exceeds 16000 characters'};
  result.stdout=output;result.executionMs=Math.round(performance.now()-start);
  self.postMessage({kind:'result',result});
 }catch(error){self.postMessage({kind:'result',result:{status:overflow?'OUTPUT_LIMIT':'ERROR',error:String(error).slice(0,1000),stdout:output}});}
};
