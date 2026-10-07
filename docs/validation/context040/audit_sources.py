import hashlib,json
from pathlib import Path
root=Path('/workspace/local-ai-workspace');out=root/'docs/validation/context040'
base=json.loads((out/'baseline031-hashes.json').read_text())
changed=[p for p,h in base.items() if (root/p).is_file() and hashlib.sha256((root/p).read_bytes()).hexdigest()!=h]
deleted=[p for p in base if not (root/p).exists()]
new=[str(p.relative_to(root)) for p in (root/'app/src').rglob('*') if p.is_file() and str(p.relative_to(root)) not in base]
protected=[p for p in base if '/validation/' in p or p.startswith('litert-compat/') or '/inference/' in p or '/python/' in p or '/semantic/' in p and '/ui/' not in p or p.endswith(('/WorkspaceDatabase.kt','/Entities.kt','/Daos.kt','/AssistantSettings.kt'))]
violations=[p for p in protected if p in changed or p in deleted]
assert not violations and not deleted,(violations,deleted)
assert len(changed)==5,changed
report={'baseline':'0.3.1','target':'0.4.0','changedFiles':changed,'newFiles':new,'deletedFiles':deleted,'protectedFilesChecked':len(protected),'protectedFilesChanged':violations,'existingSchemasChanged':False,'legacyValidationCatalogChanged':False,'semanticV2CatalogChanged':False,'nativeRuntimeChanged':False,'contextBuilderDefault':'OFF','contextBuilderChatScope':'TEXT_ONLY_EXPERIMENTAL','physical040Evidence':'NOT_YET_DEVICE_VALIDATED','finalSourceHashes':{p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in changed+new}}
(out/'source-audit.json').write_text(json.dumps(report,indent=2));print('Protected:',len(protected),'Changed:',len(changed),'New:',len(new),'Deleted:',len(deleted))
