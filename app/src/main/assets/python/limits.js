/* Standard host memory cap, applied before this pinned WASM runtime is instantiated.
   The audited wasm imports env.memory. This caps CPython's WASM heap, not browser RSS. */
function enforceWasmMemoryCap() {
 const Native = WebAssembly.Memory;
 const Limited = new Proxy(Native, {construct(target,args) {
  const opts={...args[0],maximum:Math.min(args[0].maximum ?? 4096,4096)};
  if(opts.initial>4096) throw new Error('WASM initial memory exceeds 256 MiB');
  return Reflect.construct(target,[opts]);
 }});
 Object.defineProperty(WebAssembly,'Memory',{value:Limited,writable:false,configurable:false});
}
if(typeof module !== 'undefined') module.exports={enforceWasmMemoryCap};
