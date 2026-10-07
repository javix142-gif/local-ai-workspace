from pathlib import Path
import xml.etree.ElementTree as ET
import json,hashlib
root=Path('docs/validation/hotfix024')
summary={}
for module in ('app','litert-compat'):
 files=list(Path(module+'/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
 rows=[]
 for file in files:
  r=ET.parse(file).getroot();rows.append({'suite':r.attrib['name'],**{key:int(r.attrib.get(key,'0')) for key in ['tests','failures','errors','skipped']}})
 summary[module]={'suites':len(files),**{key:sum(row[key] for row in rows) for key in ['tests','failures','errors','skipped']},'results':rows}
root.joinpath('jvm-test-summary.json').write_text(json.dumps(summary,indent=2))
issues=ET.parse('app/build/reports/lint-results-debug.xml').getroot().findall('issue')
lint={key:sum(i.attrib.get('severity','').lower()==key for i in issues) for key in ('error','warning','information')}
root.joinpath('lint-summary.json').write_text(json.dumps(lint,indent=2))
expected=json.loads(root.joinpath('source-final.json').read_text())
assert all(Path(p).exists() and hashlib.sha256(Path(p).read_bytes()).hexdigest()==h for p,h in expected.items()),'Source changed after checks started'
print(json.dumps({k:{kk:vv for kk,vv in v.items() if kk!='results'} for k,v in summary.items()},indent=2));print('Lint:',lint)
assert all(v['failures']==0 and v['errors']==0 for v in summary.values())
assert lint['error']==0
