#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Run the mock HTTP suite in a fresh namespace containing only loopback.

Invoke with unshare --user --map-root-user --net. The caller records its own
namespace as CI_HTTP_PARENT_NETNS before unshare. Never use the host namespace.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    parent = os.environ.get('CI_HTTP_PARENT_NETNS', '')
    if not parent.isdecimal() or int(parent) == os.stat('/proc/self/ns/net').st_ino:
        raise RuntimeError('A fresh mock HTTP network namespace is required')
    interfaces = {line.split(':', 1)[0].strip()
                  for line in Path('/proc/net/dev').read_text().splitlines()[2:]}
    if interfaces != {'lo'}:
        raise RuntimeError('Mock HTTP namespace must contain only loopback')
    root = args.root.resolve()
    test = root / 'coordinator/test/http.test.mjs'
    if not test.is_file() or test.is_symlink():
        raise RuntimeError('Explicit mock HTTP suite missing')
    # The only interface is created by the fresh namespace. Enabling it cannot
    # reach any host service. All upstream provider transports remain mocks.
    subprocess.run(['ip', 'link', 'set', 'lo', 'up'], check=True, timeout=10)
    return subprocess.run(['node', '--test', '--test-reporter=junit',
                           '--test-reporter-destination=' + str(args.report.resolve()),
                           str(test)], cwd=root / 'coordinator', check=False,
                          timeout=120).returncode


if __name__ == '__main__':
    sys.exit(main())
