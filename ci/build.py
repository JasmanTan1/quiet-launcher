#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Execute approved candidate commands in a network namespace, collect exact counts.

Hosted runner entry only. Local operators use the separate lane instructions.
No dependencies are installed; caches and browser binaries must be provisioned.
"""
import sys
sys.dont_write_bytecode = True
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import struct
import xml.etree.ElementTree as ET
import zipfile

# -I omits the script directory. Load the sibling explicitly without changing sys.path.
import runpy
lib=runpy.run_path(str(Path(__file__).with_name('check.py')))
Report=lib['Report']; require=lib['require']; preflight=lib['preflight']


def junit(path):
    tree=ET.parse(path)
    require(tree.getroot().tag in ('testsuite','testsuites'))
    counts={'passed':0,'failed':0,'skipped':0}
    cases=list(tree.getroot().iter('testcase'))
    require(cases)
    for case in cases:
        status='failed' if case.find('failure') is not None or case.find('error') is not None else 'skipped' if case.find('skipped') is not None else 'passed'
        counts[status]+=1
    # A suite-level error without a testcase must also fail verification.
    require(not any(node.tag in ('error','failure') for suite in tree.getroot().iter('testsuite') for node in suite))
    return counts


def unsigned(path):
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist()
        require(not any(n.upper().startswith('META-INF/') and n.upper().endswith(('.RSA','.DSA','.EC','.SF')) for n in names))
        if path.suffix=='.apk':
            # An APK Signing Block lies directly before the ZIP central directory.
            with path.open('rb') as stream:
                stream.seek(max(0,archive.start_dir-16))
                require(stream.read(16)!=b'APK Sig Block 42')
    return True


def artifact_manifest(root,product):
    android=root/('tv' if product=='bgm' else '.')
    files=[android/'app/build/outputs/apk/debug/app-debug.apk',
           android/'app/build/outputs/apk/release/app-release-unsigned.apk',
           android/'app/build/outputs/bundle/release/app-release.aab']
    rows=[]
    for p in files:
        require(p.is_file())
        if 'release' in p.parts: unsigned(p)
        rows.append({'path':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size})
    if product=='bgm':
        for sub in ('coordinator/src','coordinator/web'):
            require((root/sub).is_dir())
            outputs=sorted(p for p in (root/sub).rglob('*') if p.is_file())
            require(outputs)
            for p in outputs:
                rows.append({'path':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size})
    return rows


def screenshots(root,product):
    expected=json.loads(Path(__file__).with_name('screenshot-cases.json').read_text())[product]
    rows=[]
    for name in expected:
        path=root/'.ci-results/screenshots'/ (name+'.png')
        data=path.read_bytes()
        require(data[:8]==b'\x89PNG\r\n\x1a\n')
        pos=8; seen=[]
        while pos<len(data):
            length=struct.unpack('>I',data[pos:pos+4])[0]
            kind=data[pos+4:pos+8]
            require(pos+12+length<=len(data))
            require(kind not in (b'tEXt',b'zTXt',b'iTXt',b'eXIf',b'tIME',b'acTL'))
            seen.append(kind); pos+=12+length
        require(pos==len(data) and seen[0]==b'IHDR' and seen[-1]==b'IEND' and b'IDAT' in seen)
        rows.append({'case':name,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)})
    return rows


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--product',choices=['bgm','launcher'],required=True)
    parser.add_argument('--root',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args(); root=args.root.resolve(); report=Report(args.product,'application-verification')
    args.report=args.report.resolve()
    android=root/('tv' if args.product=='bgm' else '.')
    # Guards precede commands and logs. Never run this on a local/private runner.
    def guard():
        require(os.environ.get('GITHUB_ACTIONS')=='true')
        require(os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted')
        # Capture this in the workflow before unshare. Access to /proc/1/ns/net
        # is restricted on some hosts after dropping privileges.
        parent_netns = os.environ.get('CI_PARENT_NETNS', '')
        require(parent_netns.isdecimal())
        require(os.stat('/proc/self/ns/net').st_ino!=int(parent_netns))
        interfaces={line.split(':',1)[0].strip() for line in Path('/proc/net/dev').read_text().splitlines()[2:]}
        require(interfaces<={'lo'})
        require(not (root/'.git').exists())
        require(not any(android.rglob('build')))
        require(not any((root/p).exists() for p in ('.ci-results',)))
    report.check('isolated-hosted-fresh-copy',guard)
    if report.data['checks'][-1]['status']=='failed': return report.finish(args.report) or 1
    report.check('candidate-preflight',lambda: preflight(report,root,args.product))
    if any(c['status']=='failed' for c in report.data['checks']): return report.finish(args.report) or 1
    results=root/'.ci-results'; results.mkdir()
    env={k:v for k,v in os.environ.items() if k in ('PATH','JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','GRADLE_USER_HOME','LANG','LC_ALL','TMPDIR','AMBIENT_FFMPEG')}
    env.update({'CI':'true','GRADLE_OPTS':'-Dorg.gradle.daemon=false -Dorg.gradle.vfs.watch=false',
                'LIBGL_ALWAYS_SOFTWARE':'1','CUDA_VISIBLE_DEVICES':'','HIP_VISIBLE_DEVICES':'',
                'npm_config_offline':'true','npm_config_ignore_scripts':'true',
                'PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD':'1'})
    env['CI_HTTP_PARENT_NETNS']=str(os.stat('/proc/self/ns/net').st_ino)
    gradle=['sh','gradlew','--offline','--no-daemon','--max-workers=2',
            '--dependency-verification=strict','--console=plain',
            '-Dorg.gradle.java.installations.auto-download=false',
            ':app:testDebugUnitTest',':app:verifyPaparazziDebug',':app:lintDebug',
            ':app:assembleDebug',':app:assembleRelease',':app:bundleRelease']
    commands=[('android',gradle,android)]
    if args.product=='bgm':
        node=['node','--import',str(root/'ci/no-network.mjs'),'--test','--test-reporter=junit']
        unit=sorted(str(p) for p in (root/'coordinator/test').glob('*.test.mjs') if not p.name.endswith('.contract.test.mjs') and p.name!='http.test.mjs')
        contract=sorted(str(p) for p in (root/'coordinator/test').glob('*.contract.test.mjs'))
        commands += [
          ('coordinator-unit',node+['--test-reporter-destination='+str(results/'unit.xml')]+unit if unit else ['false'],root),
          ('runtime-contract',node+['--test-reporter-destination='+str(results/'contract.xml')]+contract if contract else ['false'],root),
        ]
        if (root/'coordinator/test/http.test.mjs').is_file():
            commands.append(('mock-http',['unshare','--user','--map-root-user','--net',
                             sys.executable,'-I',str(root/'ci/http-tests.py'),
                             '--root',str(root),'--report',str(results/'http.xml')],root))
        for path in sorted((root/'coordinator').rglob('*.mjs')):
            commands.append(('syntax-'+hashlib.sha256(str(path.relative_to(root)).encode()).hexdigest()[:12],['node','--check',str(path)],root))
        # Optional browser tooling must already exist; this runner never installs it.
        commands.append(('web-screenshots',['node','node_modules/@playwright/test/cli.js','test','--config','ci/playwright.config.ts'],root))
    for name,command,cwd in commands:
        def execute(cmd=command,d=cwd,n=name):
            # Logs stay local; never print candidate stdout or upload reports.
            with (results/(n+'.log')).open('wb') as log:
                result=subprocess.run(cmd,cwd=d,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=1800,check=False)
            require(result.returncode==0)
        report.check('command:'+name,execute)
    counts={'passed':0,'failed':0,'skipped':0}
    xmls=sorted((android/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    if args.product=='bgm':
        xmls += [results/'unit.xml',results/'contract.xml',results/'screenshots.xml']
        if (root/'coordinator/test/http.test.mjs').is_file(): xmls.append(results/'http.xml')
    def collect():
        require(xmls)
        for p in xmls:
            for key,n in junit(p).items(): counts[key]+=n
        require(counts['passed']>0 and counts['failed']==0 and counts['skipped']==0)
    report.check('exact-application-test-counts',collect)
    report.data['testCounts']=counts
    def lint():
        path=android/'app/build/reports/lint-results-debug.xml'
        issues=list(ET.parse(path).getroot().iter('issue'))
        report.data['lintCounts']={'errors':sum(i.get('severity') in ('Fatal','Error') for i in issues),'warnings':sum(i.get('severity')=='Warning' for i in issues)}
        require(report.data['lintCounts']['errors']==0)
    report.check('android-lint-report',lint)
    def artifacts(): report.data['artifacts']=artifact_manifest(root,args.product)
    report.check('required-debug-and-unsigned-release-artifacts',artifacts)
    def screenshot_outputs(): report.data['screenshots']=screenshots(root,args.product)
    report.check('required-sanitized-screenshot-outputs',screenshot_outputs)
    # Matrix evidence must name actual testcases; a reference oracle cannot qualify.
    def matrix():
        evidence=json.loads((root/'ci/implementation-coverage.json').read_text())
        required={c['id'] for c in lib['read'](Path(__file__).with_name('contract')/'fixtures/verification-matrix.json')['checks']}
        required = required-{'release_boundary','launcher','screenshots_launcher'} if args.product=='bgm' else {'launcher','screenshots_launcher'}
        names={c.get('classname','')+'::'+c.get('name','') for p in xmls for c in ET.parse(p).getroot().iter('testcase') if c.find('skipped') is None and c.find('failure') is None and c.find('error') is None}
        require(required<=evidence.keys())
        require(all(isinstance(evidence[k],list) and evidence[k] and all(name in names for name in evidence[k]) for k in required))
    report.check('implementation-matrix-coverage',matrix)
    report.data['notVerified']=['P4 final binary/privacy/asset approval and visual screenshot review.','Real-device behavior and real-provider compatibility.','SDK and full transitive dependency provenance require separate review.']
    report.skip('release-acceptance','P4 artifact/privacy/asset and visual review evidence remains a separate gate.')
    return report.finish(args.report)  # Release acceptance remains separate and explicitly skipped.

if __name__=='__main__': sys.exit(main())
