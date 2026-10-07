from pathlib import Path
import json,shutil,zipfile,hashlib
root=Path('/workspace/local-ai-workspace');out=root/'docs/validation/chat041';ev=out/'evidence';ev.mkdir(exist_ok=True)
for module in ['app','litert-compat']:
 target=ev/module;target.mkdir(exist_ok=True)
 for p in (root/module/'build/test-results/testDebugUnitTest').glob('TEST-*.xml'):shutil.copy2(p,target/p.name)
shutil.copy2(root/'app/build/reports/lint-results-debug.xml',ev/'lint-results-debug.xml')
audit=json.loads((out/'source-audit.json').read_text())
paths=[root/p for p in audit['existingFilesChanged']+audit['newFiles']]+[root/'docs/CHAT041_REPORT.md']+list(out.rglob('*'))
target=root/'dist/local-ai-workspace-0.4.1-source-and-qa.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(set(paths)):
  if p.is_file() and p.name not in ['delivery.json','artifact-manifest.json']:z.write(p,p.relative_to(root))
artifacts=[]
for p in [root/'dist/apk/local-ai-workspace-0.4.1-normal-chat-hotfix-arm64.apk',root/'docs/CHAT041_REPORT.md',target]:
 artifacts.append({'path':str(p),'bytes':p.stat().st_size,'sha256':hashlib.file_digest(p.open('rb'),'sha256').hexdigest()})
(out/'artifact-manifest.json').write_text(json.dumps(artifacts,indent=2));print(json.dumps(artifacts,indent=2))
