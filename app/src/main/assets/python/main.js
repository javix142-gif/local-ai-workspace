const worker=new Worker('worker.js');let ready=false,code=null,finished=false,deadline,bootstrapMs=null;
function finish(result){if(finished)return;finished=true;result.bootstrapMs=bootstrapMs;clearTimeout(deadline);worker.terminate();AppBridge.result(JSON.stringify(result));}
function begin(){if(!ready||code===null)return;clearTimeout(deadline);deadline=setTimeout(()=>finish({status:'TIMEOUT',error:'Execution exceeded 10 seconds'}),10000);worker.postMessage({code});}
worker.onmessage=e=>{if(e.data.kind==='ready'){ready=true;bootstrapMs=e.data.bootstrapMs;begin();}else if(e.data.kind==='result')finish(e.data.result);};
worker.onerror=()=>finish({status:'ERROR',error:'Python worker failed'});
deadline=setTimeout(()=>finish({status:'INITIALIZATION_TIMEOUT',error:'Python initialization exceeded 30 seconds'}),30000);
window.start=source=>{code=source;begin();};
