import json,time,litert_lm as lm
lm.set_min_log_severity(lm.LogSeverity.SILENT)
report={'platform':'Linux x86_64; PHYSICAL_DEVICE_TEST_REQUIRED','sdk':'0.17.1','tests':[]}
with lm.Engine('/workspace/model-validation/gemma-4-E2B-it.litertlm',backend=lm.Backend.CPU(thread_count=4),max_num_tokens=4096) as engine:
 for total,budget in [(256,-1),(512,-1),(1024,-1),(256,128)]:
  row={'maxOutput':total,'thinkingBudget':budget,'rawCallbackCount':0,'thoughtCallbackCount':0,'finalCallbackCount':0,'mixedCallbackCount':0,'thoughtCharacters':0,'finalCharacters':0,'oldFilterVisibleCharacters':0,'keys':[]}
  final=[];start=time.monotonic()
  with engine.create_conversation(thinking_config=lm.ThinkingConfig(True,budget),max_output_tokens=total,sampler_config=lm.SamplerConfig(top_k=40,top_p=.95,temperature=.3,seed=0)) as conv:
   for msg in conv.send_message_async('Analiza cuidadosamente: tres cajas contienen 4 objetos cada una. Se retiran 5. ¿Cuántos quedan? Responde brevemente.'):
    row['rawCallbackCount']+=1
    text=''.join(x.get('text','') for x in msg.get('content',[]) if isinstance(x,dict))
    channels=msg.get('channels',{})
    thought=''.join(str(v) for k,v in channels.items() if k in ('thought','analysis'))
    visible=channels.get('final',text if not channels else '')
    row['keys']=sorted(set(row['keys'])|set(msg))
    if thought:row['thoughtCallbackCount']+=1;row.setdefault('firstThoughtMs',round((time.monotonic()-start)*1000))
    if text:row['finalCallbackCount']+=1;row.setdefault('firstFinalMs',round((time.monotonic()-start)*1000))
    if thought and text:row['mixedCallbackCount']+=1
    row['thoughtCharacters']+=len(thought);row['finalCharacters']+=len(text);row['oldFilterVisibleCharacters']+=len(visible);final.append(text)
   try:
    info=conv.get_benchmark_info();row['benchmark']=str(info) if False else {'outputTokens':info.last_decode_token_count}
   except Exception as e:row['benchmarkErrorClass']=type(e).__name__
  row['totalMs']=round((time.monotonic()-start)*1000);row['finalContains7']='7' in ''.join(final);row['finishState']='DONE';report['tests'].append(row)
  with open('/workspace/local-ai-workspace/docs/validation/hotfix023/host-probe.json','w') as f:json.dump(report,f,indent=2)
  print(json.dumps(row),flush=True)
