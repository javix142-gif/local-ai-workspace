import hashlib,json,tarfile
from pathlib import Path
root=Path('/workspace/local-ai-workspace')
output=root/'docs/validation/semantic031'
base=json.loads((output/'baseline030-hashes.json').read_text())
changed=[p for p,h in base.items() if (root/p).is_file() and hashlib.sha256((root/p).read_bytes()).hexdigest()!=h]
new=[str(p.relative_to(root)) for p in (root/'app/src').rglob('*') if p.is_file() and str(p.relative_to(root)) not in base]
deleted=[p for p in base if not (root/p).exists()]
protected=[p for p in base if '/validation/' in p or p.endswith('/SemanticValidation.kt') or p.startswith('litert-compat/') or '/inference/' in p or '/python/' in p or p.endswith(('/WorkspaceDatabase.kt','/SemanticDatabase.kt','/EmbeddingModels.kt','/SemanticRetrievalService.kt'))]
violations=[p for p in protected if p in changed or p in deleted]
assert not violations and not deleted,(violations,deleted)
with tarfile.open('/workspace/baselines/local-ai-workspace-0.3.0-source.tar.gz') as archive:
    name=next(p for p in archive.getnames() if p.endswith('/semantic/v2/SemanticLayer.kt'))
    old=archive.extractfile(name).read().decode()
    current=(root/'app/src/main/java/com/localai/workspace/semantic/v2/SemanticLayer.kt').read_text()
    marker='    /** New generations'
    assert old[old.index(marker):]==current[current.index(marker):]
report={'baseline':'0.3.0','target':'0.3.1','changedFiles':changed,'newFiles':new,'deletedFiles':deleted,'protectedFilesChecked':len(protected),'protectedFilesChanged':violations,'indexingRetrievalBodyIdentical':True,'schemaMigrationRequired':False,'historicalPhysicalSuiteModified':False,'semanticV2PhysicalSuiteModified':False,'physical030Evidence':'USER_REPORTED_33_HISTORICAL_20_SEMANTIC_NO_JSON_SUPPLIED','physical031Evidence':'NOT_YET_DEVICE_VALIDATED','finalSourceHashes':{p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in changed+new}}
(output/'source-audit.json').write_text(json.dumps(report,indent=2))
print('Protected:',len(protected),'Changed:',len(changed),'New:',len(new),'Deleted:',len(deleted))
