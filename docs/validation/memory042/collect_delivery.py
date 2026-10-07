from pathlib import Path
import json,zipfile,hashlib,shutil
root=Path('/workspace/local-ai-workspace');out=root/'docs/validation/memory042'
audit=json.loads((out/'source-audit.json').read_text())
for module in ['app','litert-compat']:
 target=out/'evidence'/module;target.mkdir(parents=True,exist_ok=True)
 for p in (root/module/'build/test-results/testDebugUnitTest').glob('TEST-*.xml'):shutil.copy2(p,target/p.name)
shutil.copy2(root/'app/build/reports/lint-results-debug.xml',out/'evidence/lint-results-debug.xml')
files=[root/p for p in audit['changed']+audit['new']]+[root/'docs/MEMORY042_REPORT.md']+list(out.rglob('*'))
archive=root/'dist/local-ai-workspace-0.4.2-source-and-qa.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for p in files:
  if p.is_file() and p.name not in ['delivery.json','artifact-manifest.json']:z.write(p,p.relative_to(root))
artifacts=[root/'dist/apk/local-ai-workspace-0.4.2-memory-relevance-hotfix-arm64.apk',root/'docs/MEMORY042_REPORT.md',archive]
manifest=[{'path':str(p),'bytes':p.stat().st_size,'sha256':hashlib.file_digest(p.open('rb'),'sha256').hexdigest()} for p in artifacts]
(out/'artifact-manifest.json').write_text(json.dumps(manifest,indent=2));print(json.dumps(manifest,indent=2))
