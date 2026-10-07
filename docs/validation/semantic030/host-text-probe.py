import json,time,hashlib,math,litert_lm,os
p='/workspace/semantic-models/embeddinggemma-2-740m.litertlm'
result={'runtime':'0.17.1','platform':'Linux host; NOT YET DEVICE VALIDATED','size':os.path.getsize(p),'sha256':hashlib.file_digest(open(p,'rb'),'sha256').hexdigest()}
try:
 t=time.monotonic();e=litert_lm.EmbeddingEngine(p,backend=litert_lm.Backend.CPU());result['loadMs']=(time.monotonic()-t)*1000
 for dim in [768,256]:
  t=time.monotonic();v=e.compute_embedding('task: search result | query: red bicycle',litert_lm.EmbeddingOptions(normalize=True,output_size=dim)).embedding
  result[str(dim)]={'dimension':len(v),'finite':all(math.isfinite(x) for x in v),'norm':math.sqrt(sum(x*x for x in v)),'ms':(time.monotonic()-t)*1000}
 e.close()
except Exception as ex:result['error']=type(ex).__name__+': '+str(ex)
print(json.dumps(result,indent=2));json.dump(result,open('/workspace/local-ai-workspace/docs/validation/semantic030/host-eg2-probe.json','w'),indent=2)
