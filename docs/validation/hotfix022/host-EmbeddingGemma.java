package com.arm.aichat;
public class EmbeddingGemma {
 private native long nativeLoad(String path,String ignoredDirectory);
 private native float[] nativeEmbed(long handle,byte[] text);
 private native void nativeClose(long handle);
 private native int nativeDimension(long handle);
 private native String nativeArchitecture(long handle);
 public static void main(String[] args) throws Exception {
  System.loadLibrary("embedding-jni-host");
  EmbeddingGemma binding=new EmbeddingGemma();
  boolean safelyFailed=false;
  try { binding.nativeLoad("/nonexistent-private-test-file.gguf","/no-extracted-libraries"); }
  catch(EmbeddingNativeException error) { safelyFailed=error.getMessage().startsWith("GGUF_OPEN|GGUF_OPEN_FAILED|") && !error.getMessage().contains("/nonexistent"); }
  long handle=binding.nativeLoad(args[0],"/no-extracted-libraries");
  int dimension=0;String architecture="";double norm=0;boolean finite=true;
  try {
   dimension=binding.nativeDimension(handle);architecture=binding.nativeArchitecture(handle);
   float[] vector=binding.nativeEmbed(handle,"task: search result | query: prueba de embedding".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   if(vector.length!=dimension)throw new AssertionError("dimension mismatch");
   for(float value:vector){finite&=Float.isFinite(value);norm+=(double)value*value;}
   norm=Math.sqrt(norm);
  } finally { binding.nativeClose(handle); }
  boolean ok=safelyFailed&&finite&&dimension==768&&architecture.equals("gemma-embedding")&&Math.abs(norm-1)<.001;
  System.out.println("{\"platform\":\"Linux x86_64 JNI, NOT Android ARM64\",\"safeTypedException\":"+safelyFailed+",\"actualDimension\":"+dimension+",\"actualArchitecture\":\""+architecture+"\",\"finite\":"+finite+",\"l2Norm\":"+norm+",\"passed\":"+ok+"}");
  if(!ok)System.exit(1);
 }
}
