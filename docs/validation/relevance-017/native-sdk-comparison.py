import dataclasses,importlib.metadata,json,resource,signal,sys,threading,time
from pathlib import Path
import litert_lm as lm
signal.alarm(260);lm.set_min_log_severity(lm.LogSeverity.ERROR)
root=Path('/workspace/.runtime-validation');version=importlib.metadata.version('litert-lm-api');precision=sys.argv[1] if len(sys.argv)>1 else 'default';label=version.replace('.','')+'-'+precision;out=root/('cpu-sdk-'+label+'.json');report={'sdk':version,'precisionRequested':precision,'model':'Qwen3.5-2B_int8.litertlm','platform':'Linux x86_64 C API, NOT Android','context':2048,'threads':4,'temperature':.3,'topP':.95,'topK':40,'seed':0,'maxOutput':128,'appPolicy':False,'history':False,'extraControls':False,'prerender':False,'turns':[],'deviceValidation':'NOT_EXECUTED'}
def save():out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
kwargs={}
if precision=='fp32':
 from litert_lm._ffi import ActivationDataType
 kwargs['activation_data_type']=ActivationDataType.FLOAT32
(root/('cache-sdk-'+label)).mkdir(exist_ok=True)
start=time.monotonic();print('MODEL_OPEN_START',label,flush=True)
engine=lm.Engine(str(root/'Qwen3.5-2B_int8.litertlm'),backend=lm.Backend.CPU(thread_count=4),max_num_tokens=2048,cache_dir=str(root/('cache-sdk-'+label)),enable_benchmark=True,enable_speculative_decoding=False,**kwargs);report['loadMs']=round((time.monotonic()-start)*1000);save();print('MODEL_OPEN_OK',report['loadMs'],flush=True)
try:
 for prompt in ['Calcula 17 + 25. Responde solo el número.','que comunas de santiago de chile conoces?','Enumera cinco comunas de la región metropolitana de Santiago, Chile. Solo nombres.']:
  c=engine.create_conversation(automatic_tool_calling=False,extra_context={'enable_thinking':False},thinking_config=lm.ThinkingConfig(enable_thinking=False),sampler_config=lm.SamplerConfig(temperature=.3,top_p=.95,top_k=40,seed=0),max_output_tokens=128)
  answer='';first=None;start=time.monotonic();timer=threading.Timer(45,c.cancel_process);timer.daemon=True;timer.start();item={'prompt':prompt}
  try:
   for m in c.send_message_async(lm.Message.user(prompt),max_output_tokens=128,thinking_config=lm.ThinkingConfig(enable_thinking=False)):
    delta=''.join(p.get('text','') for p in m.get('content',[]) if p.get('type')=='text')
    if delta:
     if first is None:first=time.monotonic()
     answer+=delta
   item.update(nativeDone=True,benchmark=dataclasses.asdict(c.get_benchmark_info()))
  except Exception as e:item['error']=type(e).__name__+':'+str(e)[:200]
  finally:
   timer.cancel();item.update(text=answer,ttftMs=None if first is None else round((first-start)*1000),totalMs=round((time.monotonic()-start)*1000));report['turns'].append(item);save();print(json.dumps(item,ensure_ascii=False),flush=True);c.close()
finally:
 engine.close();report['unloadReturned']=True;report['hostPeakRssBytes']=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss*1024;save()
