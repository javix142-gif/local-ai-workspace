from pathlib import Path
import json,shutil,zipfile,hashlib
root=Path('/workspace/local-ai-workspace');out=root/'docs/validation/context040';ev=out/'evidence';ev.mkdir(exist_ok=True)
for module in ['app','litert-compat']:
 target=ev/module;target.mkdir(exist_ok=True)
 for p in (root/module/'build/test-results/testDebugUnitTest').glob('TEST-*.xml'):shutil.copy2(p,target/p.name)
for src,name in [('app/build/reports/lint-results-debug.xml','lint-results-debug.xml'),('app/build/reports/context-host-benchmark.json','context-host-benchmark.json')]:shutil.copy2(root/src,ev/name)
audit=json.loads((out/'source-audit.json').read_text())
paths=[root/p for p in audit['changedFiles']+audit['newFiles']]+[root/'docs/CONTEXT040_REPORT.md']
paths+=list(out.rglob('*'))
ziptarget=root/'dist/local-ai-workspace-0.4.0-source-and-qa.zip'
with zipfile.ZipFile(ziptarget,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(set(paths)):
  if p.is_file() and p.name!='delivery.json':z.write(p,p.relative_to(root))
artifacts=[]
for p in [root/'dist/apk/local-ai-workspace-0.4.0-context-memory-arm64.apk',root/'docs/CONTEXT040_REPORT.md',ziptarget]:
 artifacts.append({'path':str(p),'bytes':p.stat().st_size,'sha256':hashlib.file_digest(p.open('rb'),'sha256').hexdigest()})
(out/'artifact-manifest.json').write_text(json.dumps(artifacts,indent=2));print(json.dumps(artifacts,indent=2))
