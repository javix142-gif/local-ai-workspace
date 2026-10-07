# Offline CPython tool policy. OS isolation is the browser worker/MEMFS boundary,
# not the Python import filter (which is only defense in depth).
import ast as _ast, builtins as _b, json as _json, os as _os, posixpath as _path
import math, statistics, csv, datetime, collections, io
_allowed = frozenset(('math','statistics','json','csv','datetime','collections','io'))
_original_import = _b.__import__
_original_open = _b.open

def _safe_import(name, globals=None, locals=None, fromlist=(), level=0):
    if level or name.split('.')[0] not in _allowed:
        raise PermissionError('Import not authorized: ' + name)
    return _original_import(name, globals, locals, fromlist, level)

def _safe_open(path, *args, **kwargs):
    if not isinstance(path, str):
        raise PermissionError('Only sandbox file paths are accepted')
    resolved = _path.normpath(_path.join('/work', path))
    if not resolved.startswith('/work/'):
        raise PermissionError('Filesystem is limited to /work')
    return _original_open(resolved, *args, **kwargs)

_safe = dict(vars(_b))
for _name in ('eval','exec','compile','input','breakpoint','help'):
    _safe.pop(_name, None)
_safe['__import__'] = _safe_import
_safe['open'] = _safe_open
_os.makedirs('/work', exist_ok=True)
_os.chdir('/work')

def _run_json(code):
    try:
        if not isinstance(code, str) or not 0 < len(code) <= 8000:
            return _json.dumps({'status':'INVALID_ARGUMENTS','error':'Code exceeds 8000 characters'})
        tree = _ast.parse(code, filename='<python tool>')
        if sum(1 for _ in _ast.walk(tree)) > 2500:
            raise PermissionError('Code exceeds AST limit')
        for node in _ast.walk(tree):
            if isinstance(node, _ast.Import):
                for item in node.names:
                    if item.name.split('.')[0] not in _allowed:
                        raise PermissionError('Import not authorized: ' + item.name)
            if isinstance(node, _ast.ImportFrom) and (node.level or (node.module or '').split('.')[0] not in _allowed):
                raise PermissionError('Import not authorized')
        exec(compile(tree, '<python tool>', 'exec'), {'__builtins__':_safe,'__name__':'__main__'})
        return _json.dumps({'status':'SUCCESS'})
    except PermissionError as error:
        return _json.dumps({'status':'POLICY_REJECTED','error':str(error)[:1000]})
    except MemoryError:
        return _json.dumps({'status':'MEMORY_LIMIT','error':'WASM memory limit reached'})
    except Exception as error:
        return _json.dumps({'status':'ERROR','error':type(error).__name__ + ': ' + str(error)[:1000]})
