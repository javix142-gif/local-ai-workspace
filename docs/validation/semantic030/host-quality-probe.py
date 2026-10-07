import litert_lm,json,time,math
root='/workspace/local-ai-workspace/docs/validation/semantic030'
e=litert_lm.EmbeddingEngine('/workspace/semantic-models/embeddinggemma-2-740m.litertlm',backend=litert_lm.Backend.CPU(),vision_backend=litert_lm.Backend.CPU(),audio_backend=litert_lm.Backend.CPU())
def vec(content):return e.compute_embedding(content,litert_lm.EmbeddingOptions(normalize=True,output_size=768)).embedding
def dot(a,b):return sum(x*y for x,y in zip(a,b))
r={}
for kind,prompt,p,n in [('image','a red bicycle','red-bicycle.png','blue-car.png'),('audio','a steady continuous musical tone','tone.wav','noise.wav')]:
 q=vec('task: search result | query: '+prompt);ctor=litert_lm.Content.ImageFile if kind=='image' else litert_lm.Content.AudioFile
 a,b=vec(ctor(root+'/'+p)),vec(ctor(root+'/'+n));scores=[dot(q,a),dot(q,b)];r[kind]={'positiveScore':scores[0],'distractorScore':scores[1],'correctTop1':scores[0]>scores[1]}
e.close();json.dump(r,open(root+'/host-quality.json','w'),indent=2);print(json.dumps(r))
