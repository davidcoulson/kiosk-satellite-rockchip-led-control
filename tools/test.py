#!/usr/bin/env python3
"""Run device-independent lifecycle and native protocol tests."""
import json,os,shutil,subprocess,tempfile
import sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=Path(os.environ.get('JAVA_HOME',Path(shutil.which('javac')).resolve().parents[1]))
manifest=json.loads((root/'kiosk-satellite-plugin.json').read_text())
assert manifest['apiVersion']==1 and manifest['id']=='rockchip-led-control'
assert len(manifest['settings'])<=20
assert {s['type'] for s in manifest['settings']}=={'boolean','number','color','select'}
with tempfile.TemporaryDirectory(prefix='rockchip-led-test-') as temp:
    sources=[*sorted((root/'sdk/src').rglob('*.java')),*sorted((root/'src').rglob('*.java')),*sorted((root/'test').rglob('*.java'))]
    subprocess.run([str(java/'bin/javac'),'--release','8','-d',temp,*map(str,sources)],check=True)
    subprocess.run([str(java/'bin/java'),'-ea','-cp',temp,'me.jxl.kiosk.plugins.rockchip.LedTest'],check=True)
    subprocess.run([str(java/'bin/java'),'-ea','-cp',temp,'ManifestContractTest'],check=True)
    native=Path(temp)/'native-test'
    # jni.h includes jni_md.h from a per-OS subdirectory -- linux/ on the
    # build machines, darwin/ on a Mac. Hardcoding linux/ made this test
    # uncompilable on macOS ("jni_md.h file not found"), so find whichever
    # one this JDK actually ships rather than assuming the platform.
    md=next(iter(sorted((java/'include').glob('*/jni_md.h'))),None)
    assert md is not None, f'no jni_md.h under {java}/include'
    subprocess.run(['cc','-Wall','-Wextra','-Werror','-I'+str(java/'include'),'-I'+str(md.parent),str(root/'test/native_test.c'),'-o',str(native)],check=True)
    subprocess.run([str(native)],check=True)
print('PASS: manifest shape and native open mode, ioctl numbers, scalar arguments, error propagation, range rejection and descriptor cleanup.')

subprocess.run([sys.executable, str(root / 'tools/test_android_sdk.py')], check=True)
