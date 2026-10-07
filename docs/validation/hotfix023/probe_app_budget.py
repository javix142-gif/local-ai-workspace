import json,time,re,litert_lm as lm
from pathlib import Path
lm.set_min_log_severity(lm.LogSeverity.SILENT)
policy=re.search(r'internal const val SYSTEM_POLICY = "([^"]+)"',Path('app/src/main/java/com/localai/workspace/inference/LiteRtConversationPayload.kt').read_text()).group(1)
report={'platform':'Linux SDK 0.17.1 / CPU; NOT Android','modelSha256':'181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c','concurrentBuild':True,'cases':[]}
reasoning='Analiza cuidadosamente: tres cajas contienen 4 objetos cada una. Se retiran 5. ¿Cuántos quedan? Responde brevemente.'
with lm.Engine('/workspace/model-validation/gemma-4-E2B-it.litertlm',backend=lm.Backend.CPU(thread_count=4),max_num_tokens=4096,enable_benchmark=True) as engine:
 for total in [512,1024]:
  name,enabled,prompt,budget='ON_UNBOUNDED_'+str(total),True,reasoning,-1
  row={'case':name,'thinkingEffective':enabled,'maxOutput':total,'thinkingTokenBudget':budget,'rawCallbackCount':0,'thoughtCallbackCount':0,'finalCallbackCount':0,'thoughtCharacters':0,'finalCharacters':0}
  final=[];start=time.monotonic()
  with engine.create_conversation(system_message=policy,thinking_config=lm.ThinkingConfig(enabled,budget),extra_context={'enable_thinking':enabled},max_output_tokens=total,sampler_config=lm.SamplerConfig(top_k=40,top_p=.95,temperature=.3,seed=0)) as conv:
   for msg in conv.send_message_async(prompt,thinking_config=lm.ThinkingConfig(enabled,budget),max_output_tokens=total):
    row['rawCallbackCount']+=1
    text=''.join(x.get('text','') for x in msg.get('content',[]) if isinstance(x,dict));thought=''.join(str(v) for k,v in msg.get('channels',{}).items() if k in ('thought','analysis'))
    if thought:row['thoughtCallbackCount']+=1;row.setdefault('firstThoughtMs',round((time.monotonic()-start)*1000))
    if text:row['finalCallbackCount']+=1;row.setdefault('firstFinalMs',round((time.monotonic()-start)*1000))
    row['thoughtCharacters']+=len(thought);row['finalCharacters']+=len(text);final.append(text)
   info=conv.get_benchmark_info();row['outputTokens']=info.last_decode_token_count;row['outputLimitReached']=info.last_decode_token_count>=total
  row['durationMs']=round((time.monotonic()-start)*1000);text=''.join(final)
  row['answerValidated']=bool(re.search('tokio|tokyo',text,re.I)) if name=='OFF_SIMPLE' else bool(text.strip()) if name=='AUTO_SIMPLE' else bool(re.search(r'\b7\b',text))
  row['status']='PASS' if row['answerValidated'] and (row['thoughtCharacters']>0)==enabled else 'FAIL';row['finishState']='DONE'
  report['cases'].append(row);Path('docs/validation/hotfix023/app-budget-probe.json').write_text(json.dumps(report,indent=2));print(json.dumps(row),flush=True)
