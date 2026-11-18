#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Copy explicit public candidate trees into an empty build directory; no Git."""
import sys
sys.dont_write_bytecode = True
import argparse
from pathlib import Path
import runpy
import shutil

lib=runpy.run_path(str(Path(__file__).with_name('check.py')))

def main():
    p=argparse.ArgumentParser(); p.add_argument('--root',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True); p.add_argument('--product',choices=['bgm','launcher'],required=True)
    a=p.parse_args(); root=a.root.resolve(); output=a.output.resolve()
    if output.exists() or output==root or root in output.parents:
        raise ValueError('Output must be a new directory outside the source tree')
    names=['ci']
    if a.product=='bgm': names+=['tv','coordinator']
    else: names+=['app','gradle','gradlew','gradlew.bat','build.gradle.kts','settings.gradle.kts','gradle.properties']
    if (root/'tests').exists(): names+=['tests']
    # Collect all accepted paths before writing. Never open forbidden paths.
    files=[]
    for name in names:
        path=root/name
        if path.is_symlink(): raise ValueError('Symlink rejected')
        if not path.exists():
            if name=='tests': continue
            raise ValueError('Required candidate component missing')
        files+=list(lib['safe_files'](path)) if path.is_dir() else [path]
    output.mkdir(parents=True)
    for path in files:
        target=output/path.relative_to(root); target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(path,target)
    print('Fresh source copy prepared')
if __name__=='__main__': main()
