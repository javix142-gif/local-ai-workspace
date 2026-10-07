import litert_lm,time,json,math,wave,struct
from PIL import Image,ImageDraw
root='/workspace/local-ai-workspace/docs/validation/semantic030'
im=Image.new('RGB',(448,448),'white');d=ImageDraw.Draw(im);d.ellipse((35,210,175,350),outline='black',width=10);d.ellipse((275,210,415,350),outline='black',width=10);d.line([(105,280),(185,145),(245,280),(105,280),(295,170),(345,280)],fill='red',width=12);d.line([(175,140),(210,140)],fill='black',width=10);im.save(root+'/red-bicycle.png')
with wave.open(root+'/tone.wav','wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(16000);w.writeframes(b''.join(struct.pack('<h',int(math.sin(i*2*math.pi*440/16000)*10000)) for i in range(16000)))
r={'runtime':'0.17.1','platform':'Linux host; NOT YET DEVICE VALIDATED'}
try:
 e=litert_lm.EmbeddingEngine('/workspace/semantic-models/embeddinggemma-2-740m.litertlm',backend=litert_lm.Backend.CPU(),vision_backend=litert_lm.Backend.CPU(),audio_backend=litert_lm.Backend.CPU())
 for name,content in [('image',litert_lm.Content.ImageFile(root+'/red-bicycle.png')),('audio',litert_lm.Content.AudioFile(root+'/tone.wav'))]:
  t=time.monotonic();v=e.compute_embedding(content,litert_lm.EmbeddingOptions(normalize=True,output_size=768)).embedding;r[name]={'dimension':len(v),'finite':all(math.isfinite(x) for x in v),'norm':math.sqrt(sum(x*x for x in v)),'ms':(time.monotonic()-t)*1000}
 e.close()
except Exception as ex:r['error']=type(ex).__name__+': '+str(ex)
json.dump(r,open(root+'/host-eg2-multimodal.json','w'),indent=2);print(json.dumps(r,indent=2))
