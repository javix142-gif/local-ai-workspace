import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tarfile

ROOT = Path('/workspace/local-ai-workspace')
TEST = Path('/workspace/.runtime-validation')
DOC = ROOT / 'docs/validation/storage-cleanup-2026-10-05'
BACKUP = Path('/workspace/backups/local-ai-workspace-source-2026-10-05.tar.gz')
STATE = DOC / 'cleanup.json'
GENERATED = ['app/build', 'litert-compat/build', 'llama-runtime/build',
             'llama-runtime/.cxx', '.gradle', '.kotlin']
APKS = [ROOT / 'app/build/outputs/apk/release' / name for name in [
    'local-ai-workspace-0.1.6-litert-cpu-stability-arm64.apk',
    'local-ai-workspace-0.1.7-litert-chat-latency-arm64.apk']]
MODEL = TEST / 'Qwen3.5-2B_int8.litertlm'
REMOVED_MODELS = {
    TEST / 'Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm':
        '2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b',
    TEST / 'qwen2.5-1.5b-instruct-q4_k_m.gguf':
        '6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e',
}
DIRS = [ROOT / name for name in GENERATED if name != 'app/build'] + [
    TEST / 'cache', TEST / 'cache-sdk-0161-default',
    TEST / 'cache-sdk-0171-fp32', TEST / 'llama-cpu-build']
WORKTREE = TEST / 'llama-cpu-reference'
TOOLS = Path('/workspace/.toolchain')


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as source:
        while chunk := source.read(4 * 1024 * 1024):
            value.update(chunk)
    return value.hexdigest()


def size(path):
    return int(subprocess.check_output(['du', '-sb', '--', str(path)],
                                      text=True).split()[0])


def disk():
    value = os.statvfs('/workspace')
    return {'capacityBytes': value.f_blocks * value.f_frsize,
            'availableBytes': value.f_bavail * value.f_frsize}


def excluded(rel):
    return any(rel == PurePosixPath(name) or PurePosixPath(name) in rel.parents
               for name in GENERATED)


def source_files():
    for folder, dirs, files in os.walk(ROOT, followlinks=False):
        base = Path(folder)
        dirs[:] = [name for name in dirs if not excluded(
            PurePosixPath(str((base / name).relative_to(ROOT))))]
        for name in files:
            path = base / name
            rel = path.relative_to(ROOT)
            if path.is_symlink():
                yield str(rel), {'type': 'symlink', 'target': os.readlink(path)}
            else:
                yield str(rel), {'type': 'file', 'sha256': digest(path),
                                 'bytes': path.stat().st_size}


def save(state):
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + '\n')


def assert_safe(path):
    assert path.parent.resolve() == path.parent, ('Linked parent', str(path))
    assert not path.is_symlink(), ('Linked cleanup target', str(path))
    assert path.is_relative_to(ROOT) or path.is_relative_to(TEST), str(path)


def remove(path):
    assert_safe(path)
    if path.is_dir():
        shutil.rmtree(path)
    else:
        path.unlink()


def prepare():
    assert ROOT.is_dir() and TEST.is_dir() and TOOLS.is_dir()
    assert not STATE.exists()
    assert DOC.exists() == BACKUP.exists()
    reuse_prepared_backup = BACKUP.exists()
    assert all(path.is_file() for path in APKS + [MODEL])
    assert subprocess.check_output(['git', '-C', str(WORKTREE), 'status',
                                    '--porcelain'], text=True).strip() == ''
    before = {'workspaceBytes': size(Path('/workspace')),
              'projectBytes': size(ROOT), 'disk': disk()}
    DOC.mkdir(parents=True, mode=0o700, exist_ok=reuse_prepared_backup)
    Path('/workspace/backups').mkdir(exist_ok=True, mode=0o700)
    for module in ['app', 'litert-compat', 'llama-runtime']:
        for name in ['reports', 'test-results']:
            source = ROOT / module / 'build' / name
            if source.exists() and not (DOC / 'build-evidence' / module / name).exists():
                shutil.copytree(source, DOC / 'build-evidence' / module / name,
                                symlinks=True)
    for name in ['native-debug-symbols', 'mapping', 'logs']:
        source = ROOT / 'app/build/outputs' / name
        if source.exists() and not (DOC / 'build-evidence/app/outputs' / name).exists():
            shutil.copytree(source, DOC / 'build-evidence/app/outputs' / name,
                            symlinks=True)

    apk_data = {str(path): {'bytes': path.stat().st_size, 'sha256': digest(path)}
                for path in APKS}
    assert apk_data[str(APKS[1])]['sha256'] == (
        'c4d539fb6e15293a320e52673d622281c6c6ada2ebcd63bfc34ac2886e28703a')
    model_data = {'path': str(MODEL), 'bytes': MODEL.stat().st_size,
                  'sha256': digest(MODEL)}
    assert model_data['sha256'] == (
        '8ae5027136e0b89039ed76eedfb61f0cf4b7277024d99bf765359264295b86b1')
    for path, expected in REMOVED_MODELS.items():
        assert path.is_file() and not path.is_symlink()
        assert digest(path) == expected, ('Unexpected candidate', str(path))
    protected = dict(source_files())
    for path in DIRS + [WORKTREE]:
        assert_safe(path)

    def filter_tar(info):
        rel = PurePosixPath(info.name).relative_to(ROOT.name)
        return None if excluded(rel) else info

    if not reuse_prepared_backup:
        with tarfile.open(BACKUP, 'w:gz', compresslevel=6) as archive:
            archive.add(ROOT, arcname=ROOT.name, filter=filter_tar)
        os.chmod(BACKUP, 0o600)
    verified = set()
    with tarfile.open(BACKUP, 'r|gz') as archive:
        for member in archive:
            rel = str(PurePosixPath(member.name).relative_to(ROOT.name))
            if rel not in protected:
                continue
            entry = protected[rel]
            if entry['type'] == 'file':
                value = hashlib.sha256()
                source = archive.extractfile(member)
                while chunk := source.read(4 * 1024 * 1024):
                    value.update(chunk)
                assert value.hexdigest() == entry['sha256'], rel
            else:
                assert member.issym() and member.linkname == entry['target'], rel
            verified.add(rel)
    assert verified == set(protected)
    state = {
        'date': '2026-10-05', 'preparedAtUtc': datetime.datetime.now(
            datetime.timezone.utc).isoformat(), 'status': 'PREPARED',
        'before': before, 'preservedApks': apk_data, 'preservedModel': model_data,
        'sourceBackup': {'path': str(BACKUP), 'bytes': BACKUP.stat().st_size,
                         'sha256': digest(BACKUP), 'archiveContentVerified': True},
        'protectedSourceFiles': protected,
        'targets': [str(ROOT / 'app/build') + ' (except two signed APKs)'] +
                   [str(path) for path in DIRS] + [str(WORKTREE)] +
                   [str(path) for path in REMOVED_MODELS],
        'removedCandidates': {str(path): {'sha256': expected,
            'bytes': path.stat().st_size} for path, expected in REMOVED_MODELS.items()},
        'sdkAndToolchainsRetained': True,
        'originalPhoneFilesTouched': False,
        'newBuildOrTestsExecuted': False,
    }
    save(state)
    print(json.dumps({'status': 'PREPARED', 'protectedFiles': len(protected),
                      'backupBytes': BACKUP.stat().st_size,
                      'signedApksVerified': len(APKS), 'originalModelVerified': True}), flush=True)


def prune_build(path):
    if path in APKS:
        return
    if any(path in keep.parents for keep in APKS):
        assert path.is_dir() and not path.is_symlink()
        for child in list(path.iterdir()):
            prune_build(child)
    else:
        remove(path)


def clean():
    state = json.loads(STATE.read_text())
    assert state['status'] == 'PREPARED'
    assert digest(BACKUP) == state['sourceBackup']['sha256']
    assert subprocess.check_output(['git', '-C', str(WORKTREE), 'status',
                                    '--porcelain'], text=True).strip() == ''
    subprocess.run(['git', '-C', str(TOOLS / 'llama.cpp'), 'worktree', 'remove',
                    '--', str(WORKTREE)], check=True)
    prune_build(ROOT / 'app/build')
    for path in DIRS:
        remove(path)
    for path in REMOVED_MODELS:
        remove(path)
    state['status'] = 'CLEANED_PENDING_VERIFICATION'
    save(state)
    print('Allowlisted build/cache/discarded-model cleanup finished', flush=True)


def verify():
    state = json.loads(STATE.read_text())
    assert state['status'] == 'CLEANED_PENDING_VERIFICATION'
    for rel, entry in state['protectedSourceFiles'].items():
        path = ROOT / rel
        if entry['type'] == 'file':
            assert path.is_file() and digest(path) == entry['sha256'], rel
        else:
            assert path.is_symlink() and os.readlink(path) == entry['target'], rel
    for name, entry in state['preservedApks'].items():
        assert digest(Path(name)) == entry['sha256'], name
    assert digest(MODEL) == state['preservedModel']['sha256']
    assert digest(BACKUP) == state['sourceBackup']['sha256']
    assert all(not path.exists() for path in DIRS + list(REMOVED_MODELS) + [WORKTREE])
    assert (TOOLS / 'jdk-17/bin/java').is_file()
    assert (TOOLS / 'android-sdk/cmake/3.31.6/bin/cmake').is_file()
    assert (TOOLS / 'android-sdk/ndk').is_dir()
    after = {'workspaceBytes': size(Path('/workspace')),
             'projectBytes': size(ROOT), 'disk': disk()}
    state['after'] = after
    state['netDiskBytesRecovered'] = (after['disk']['availableBytes'] -
                                      state['before']['disk']['availableBytes'])
    state['netWorkspaceBytesRecovered'] = (state['before']['workspaceBytes'] -
                                           after['workspaceBytes'])
    state['status'] = 'PASS'
    state['verification'] = {'protectedFilesSha256': 'PASS', 'signedApksSha256': 'PASS',
                            'originalModelSha256': 'PASS', 'sourceBackupSha256': 'PASS',
                            'sdkRetained': 'PASS', 'deletedTargetsAbsent': 'PASS'}
    save(state)
    print(json.dumps({k: state[k] for k in ['status', 'before', 'after',
                'netDiskBytesRecovered', 'netWorkspaceBytesRecovered']}, indent=2), flush=True)


{'prepare': prepare, 'clean': clean, 'verify': verify}[sys.argv[1]]()
