import json,time,importlib.metadata
import litert_lm as lm
lm.set_min_log_severity(lm.LogSeverity.SILENT)
class Calculator(lm.Tool):
 def __init__(self):self.calls=[]
 def get_tool_description(self):
  return {'type':'function','function':{'name':'calculator.evaluate','description':'Evaluate a bounded arithmetic expression accurately.','parameters':{'type':'object','additionalProperties':False,'properties':{'expression':{'type':'string'}},'required':['expression']}}}
 def execute(self,param):
  expression=param.get('expression','')
  # Controlled, fixed synthetic arithmetic; never eval arbitrary model content.
  if expression.replace(' ','') not in ('123*47','123×47'):raise ValueError('invalid synthetic probe argument')
  result=123*47;self.calls.append(result);return {'status':'SUCCESS','value':str(result)}
report={'platform':'Linux x86_64 reference SDK; NOT physical Android','modelSha256':'181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c','sdk':'0.17.1','tests':[]}
t=time.monotonic()
try:
 with lm.Engine('/workspace/model-validation/gemma-4-E2B-it.litertlm',backend=lm.Backend.CPU(thread_count=4),max_num_tokens=4096) as engine:
  report['initializationMs']=round((time.monotonic()-t)*1000)
  calculator=Calculator()
  with engine.create_conversation(tools=[calculator],automatic_tool_calling=True,thinking_config=lm.ThinkingConfig(enable_thinking=False),max_output_tokens=256,sampler_config=lm.SamplerConfig(top_k=40,top_p=.95,temperature=.7,seed=17)) as conversation:
   answer=conversation.send_message('Usa la calculadora para calcular 123*47. Responde brevemente.')
   text=json.dumps(answer.get('content'),ensure_ascii=False)
   report['tests'].append({'test':'native automatic calculator','calls':len(calculator.calls),'validatedResult':calculator.calls[-1] if calculator.calls else None,'finalContainsResult':'5781' in text or '5,781' in text or '5.781' in text,'responseKeys':list(answer),'status':'PASS' if calculator.calls and any(x in text for x in ('5781','5,781','5.781')) else 'FAIL'})
  for enabled in [False,True]:
   with engine.create_conversation(thinking_config=lm.ThinkingConfig(enable_thinking=enabled,thinking_token_budget=128),max_output_tokens=256,sampler_config=lm.SamplerConfig(top_k=40,top_p=.95,temperature=.7,seed=17)) as conversation:
    answer=conversation.send_message('Analiza tres cajas con 4 objetos cada una. Se retiran 5. ¿Cuántos quedan? Responde brevemente.')
    final_ok='7' in json.dumps(answer.get('content'))
    channel_observed=bool(answer.get('channels')) or bool(answer.get('reasoning_content'))
    report['tests'].append({'test':'native ThinkingConfig','enabled':enabled,'requestCompleted':True,'responseKeys':list(answer),'containsExpectedFinalAnswer':final_ok,'reasoningChannelObserved':channel_observed,'status':'PASS' if final_ok and channel_observed==enabled else 'FAIL'})
except Exception as error:report['errorClass']=type(error).__name__
report['durationMs']=round((time.monotonic()-t)*1000)
with open('/workspace/local-ai-workspace/docs/validation/hotfix022/gemma-native-probe.json','w') as f:json.dump(report,f,indent=2)
print(json.dumps(report,indent=2),flush=True)
