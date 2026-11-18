#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Offline CI checks. Uses stdlib only; no services, sockets, installs or Git."""
import sys
sys.dont_write_bytecode = True
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import unicodedata
from urllib.parse import urlsplit

HERE = Path(__file__).resolve().parent

def read(path):
    return json.loads(path.read_text())

def require(value):
    if not value:
        raise AssertionError('check failed')

class Report:
    def __init__(self, product, kind):
        self.data = {'formatVersion':1, 'product':product, 'kind':kind,
                     'checks':[], 'notVerified':[]}

    def check(self, name, fn):
        try:
            require(fn() is not False)
            self.data['checks'].append({'id':name, 'status':'passed'})
        except Exception as error:
            self.data['checks'].append({'id':name, 'status':'failed', 'reason':type(error).__name__})

    def skip(self, name, reason):
        self.data['checks'].append({'id':name, 'status':'skipped', 'reason':reason})

    def finish(self, path):
        for status in ('passed','failed','skipped'):
            self.data[status] = sum(c['status']==status for c in self.data['checks'])
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.data, indent=2)+'\n')
        print(json.dumps({k:self.data[k] for k in ('product','kind','passed','failed','skipped')}))
        return 1 if self.data['failed'] else 0

class Schema:
    """Strict validator for the frozen contract's JSON Schema subset.

    New keywords fail inspection until supported. Not a general OpenAPI validator.
    """
    supported = {'$ref','type','properties','additionalProperties','required','items',
                 'minLength','maxLength','minimum','maximum','maxItems','pattern',
                 'format','enum','const','oneOf','anyOf','writeOnly'}

    def __init__(self, spec):
        self.schemas = spec['components']['schemas']

    def inspect(self, schema):
        require(set(schema) <= self.supported)
        if '$ref' in schema:
            require(schema['$ref'].startswith('#/components/schemas/'))
            require(schema['$ref'].rsplit('/',1)[1] in self.schemas)
        for child in schema.get('properties',{}).values(): self.inspect(child)
        if 'items' in schema: self.inspect(schema['items'])
        for key in ('anyOf','oneOf'):
            for child in schema.get(key,[]): self.inspect(child)
        if 'format' in schema: require(schema['format']=='uri')

    def valid(self, schema, value):
        if '$ref' in schema:
            if not self.valid(self.schemas[schema['$ref'].rsplit('/',1)[1]], value): return False
        for keyword in ('anyOf','oneOf'):
            if keyword in schema:
                n = sum(self.valid(s,value) for s in schema[keyword])
                if not (n >= 1 if keyword=='anyOf' else n==1): return False
        if 'const' in schema and (type(value)!=type(schema['const']) or value!=schema['const']): return False
        if 'enum' in schema and value not in schema['enum']: return False
        types = schema.get('type',[])
        if isinstance(types,str): types=[types]
        matches = {'object':isinstance(value,dict), 'array':isinstance(value,list),
                   'string':isinstance(value,str), 'integer':type(value)==int,
                   'number':type(value) in (int,float), 'boolean':type(value)==bool,
                   'null':value is None}
        if types and not any(matches[t] for t in types): return False
        if isinstance(value,dict):
            if not set(schema.get('required',[])) <= value.keys(): return False
            props=schema.get('properties',{})
            if schema.get('additionalProperties') is False and not value.keys()<=props.keys(): return False
            if any(not self.valid(props[k],v) for k,v in value.items() if k in props): return False
        if isinstance(value,list):
            if len(value)>schema.get('maxItems',len(value)): return False
            if 'items' in schema and any(not self.valid(schema['items'],v) for v in value): return False
        if isinstance(value,str):
            if not schema.get('minLength',0)<=len(value)<=schema.get('maxLength',len(value)): return False
            if 'pattern' in schema and not re.search(schema['pattern'],value): return False
            if schema.get('format')=='uri':
                try:
                    u=urlsplit(value)
                    if not u.scheme or re.search(r'[\s<>"{}|\\^`]',value): return False
                    if u.scheme in ('https','http') and not u.netloc: return False
                except ValueError: return False
        if type(value) in (int,float):
            if not schema.get('minimum',value)<=value<=schema.get('maximum',value): return False
        return True

class FixtureTransport:
    """Inject request(case_name, operation_id, request) into a client test.

    Matches complete requests; never listens or connects. Models fixture responses,
    not authentication or coordinator implementation.
    """
    def __init__(self, cases):
        self.cases={c['name']:c for c in cases}
        require(len(self.cases)==len(cases))
        self.calls=[]

    def request(self, case_name, operation_id, request):
        case=self.cases[case_name]
        require(operation_id==case['operationId'] and request==case['request'])
        self.calls.append(case_name)
        return case['status'], copy.deepcopy(case.get('headers',{})), copy.deepcopy(case['response'])

def transition(domain, x):
    """Reference oracle only; product tests must call their own implementation."""
    if domain=='deadline':
        n=max(0,x['expiresAt']-x['serverNow']-x['elapsedMs'])
        return {'visible':n>0,'remainingMs':n}
    if domain=='command':
        return {'execute':not x['entering'] and x['incomingN']>x['seenN'], 'seenN':max(x['seenN'],x['incomingN'])}
    if domain=='queue_insert':
        q=[t for t in x['queue'] if t!=x['track']]
        i=q.index(x['current']) if x['current'] in q else len(q)
        if x['where']=='next': i=i+1 if x['current'] in q else 0
        q.insert(i,x['track']); return {'queue':q}
    if domain=='party_order':
        rows=sorted(x['waiting'],key=lambda r:r['id'])
        if x['order']=='first_come': return {'ids':[r['id'] for r in rows]}
        groups={}
        for row in rows: groups.setdefault(row['guestId'],[]).append(row['id'])
        guests=sorted(groups,key=lambda g:(x['lastTurn'].get(g,0),groups[g][0]))
        return {'ids':[groups[g][i] for i in range(max(map(len,groups.values()),default=0)) for g in guests if i<len(groups[g])]}
    if domain=='timer_complete':
        ignored=x['storedTimerId']!=x['completedTimerId']
        return {'ignored':ignored,'timerId':x['storedTimerId'] if ignored else None,'activePlaylistId':x['activePlaylistId']}
    if domain=='resume': return {'stored':x['sessionMatches'] and x['sampleN']>x['storedN']}
    if domain=='message':
        text=re.sub(r'[\x00-\x1f\x7f-\x9f\u061c\u200b\u200e\u200f\u202a-\u202e\u2060-\u206f\ufeff\ufff9-\ufffb]',' ',x['raw'])
        text=unicodedata.normalize('NFC',' '.join(text.split()))
        return {'text':text if 1<=len(text)<=60 else None}
    if domain=='party_access':
        return {'error':'ended' if x['ended'] or x['now']>=x['expiresAt'] else 'off' if not x['enabled'] else 'blocked' if x['blocked'] else None}
    if domain=='guest_control': return {'allowed':x['hasCurrent'] and x['guestId']==x['currentGuestId']}
    if domain=='block': return {'skipCurrent':x['guestId']==x['currentGuestId'],'remainingRequestIds':[r['id'] for r in x['waiting'] if r['guestId']!=x['guestId']]}
    if domain=='timer_schedule': return {'chimeTimesMs':[1200,3600],'restoreStartsMs':5600,'finished':x['elapsedMs']>=8100,'stopPlayback':False}
    raise ValueError('unknown transition domain')

def contract_checks(report):
    root=HERE/'contract'; spec=read(root/'openapi.json'); validator=Schema(spec)
    for path,digest in read(HERE/'contract-hashes.json').items():
        report.check('frozen:'+path,lambda p=path,d=digest: hashlib.sha256((root/p).read_bytes()).hexdigest()==d)
    for name,s in validator.schemas.items(): report.check('schema:'+name,lambda s=s: validator.inspect(s))
    for case in read(root/'fixtures/schema-cases.json'):
        report.check('schema-vector:'+case['name'],lambda c=case: validator.valid({'$ref':'#/components/schemas/'+c['schema']},c['value'])==c['valid'])
    operations={o['operationId']:o for methods in spec['paths'].values() for o in methods.values()}
    cases=read(root/'fixtures/http-cases.json'); transport=FixtureTransport(cases)
    for case in cases:
        def check_case(c=case):
            status,headers,body=transport.request(c['name'],c['operationId'],c['request'])
            op=operations[c['operationId']]
            require(validator.valid(op['responses'][str(status)]['content']['application/json']['schema'],body))
            if 'body' in c['request']:
                require(validator.valid(op['requestBody']['content']['application/json']['schema'],c['request']['body']))
            for p in op['parameters']:
                if p['in'] not in ('header','query'): continue
                values=c['request'].get('headers' if p['in']=='header' else 'query',{})
                require(not p['required'] or p['name'] in values)
                if p['name'] in values: require(validator.valid(p['schema'],values[p['name']]))
            require(status!=429 or int(headers['Retry-After'])>0)
        report.check('mock-http:'+case['name'],check_case)
    for case in read(root/'fixtures/transition-cases.json'):
        report.check('reference-vector:'+case['name'],lambda c=case: transition(c['domain'],c['input'])==c['expected'])
    report.data['notVerified']=['Product implementations, provider transports and device behavior; reference vectors are harness checks only.']

# Paths are rejected before opening. This is a build preflight, not the P4 audit.
FORBIDDEN_PARTS={'.git','.gradle','node_modules','build','.aws','.codex','.agents',
                 '.ssh','.claude','secrets','credentials','cookies','.npmrc'}
def safe_files(root):
    def walk(path):
        for p in sorted(path.iterdir()):
            lower=p.name.lower()
            require(not p.is_symlink())
            require(not ('.env' in lower or
                    any(s in lower for s in ('keystore','credential','cookie','google-services.json',
                                            'auth.json','api_key','api-key','id_rsa','id_ed25519',
                                            'service-account','secrets.','secret.json','token.json')) or
                    lower in ('local.properties','signing.properties') or
                    lower.endswith(('.jks','.p12','.pfx','.pem','.key','.db','.sqlite','.sqlite3',
                                    '.db-wal','.db-shm','.sqlite-wal','.sqlite-shm'))))
            require(lower not in FORBIDDEN_PARTS)
            if p.is_dir(): yield from walk(p)
            elif p.is_file(): yield p
    yield from walk(root)

def preflight(report,root,product):
    require(root.is_dir())
    files=list(safe_files(root))
    report.check('fresh-export-no-secrets-or-builds',lambda: bool(files))
    baseline=read(HERE/'toolchains.json'); android=root/('tv' if product=='bgm' else '.')
    for filename,key in [('gradle/wrapper/gradle-wrapper.jar','wrapper.jar'),('gradlew','gradlew'),('gradlew.bat','gradlew.bat')]:
        report.check('wrapper:'+key,lambda f=filename,k=key: hashlib.sha256((android/f).read_bytes()).hexdigest()==baseline['wrapper'][k])
    def props_check():
        props={}
        for line in (android/'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines():
            line=line.strip()
            if not line or line.startswith('#'): continue
            key,value=line.split('=',1)
            require(key not in props); props[key]=value
        require(props['distributionUrl']=='https\\://services.gradle.org/distributions/gradle-8.9-bin.zip')
        require(props['distributionSha256Sum']==baseline['wrapper']['bin.zip'])
        require(props['validateDistributionUrl']=='true')
    report.check('distribution-url-and-checksum',props_check)
    def versions():
        plugin_base=baseline['android'][product]['plugins']
        dep_base=set(baseline['android'][product]['dependencies'])
        text='\n'.join(p.read_text() for p in files if p.name.endswith('.gradle.kts'))
        require('includeBuild' not in text and 'signingConfig' not in text and 'git ' not in text)
        require(not any(p.name=='libs.versions.toml' for p in files))
        plugins=re.findall(r'id\("([^\"]+)"\) version "([^\"]+)"',text)
        require(plugins and all(plugin_base.get(n)==v for n,v in plugins))
        # Approved literal plugin pins also contain `version "..."`. Remove
        # those validated declarations before rejecting alternate version logic.
        remaining=re.sub(r'id\("([^\"]+)"\) version "([^\"]+)"','',text)
        require(not re.search(r'version\s*\(|version\s*=|version\s+"|classpath\s*\(',remaining))
        deps=re.findall(r'(?:implementation|testImplementation|debugImplementation|androidTestImplementation)\s*\(\s*(?:platform\s*\(\s*)?"([^\"]+)"',text)
        require(deps and all(d in dep_base for d in deps))
        require('org.ambientroom.tv' in text if product=='bgm' else 'org.quietlauncher.home' in text)
        for name,value in baseline['android'][product]['sdk'].items():
            require(re.findall(r'\b'+name+r'\s*=\s*(\d+)',text)==[str(value)])
    report.check('retained-android-versions',versions)
    def dependency_trust():
        import xml.etree.ElementTree as ET
        meta=ET.parse(android/'gradle/verification-metadata.xml').getroot()
        ns={'v':'https://schema.gradle.org/dependency-verification'}
        require(meta.find('v:configuration/v:verify-metadata',ns).text=='true')
        require(not meta.findall('.//v:trusted-artifact',ns))
        artifacts=meta.findall('.//v:artifact',ns); require(artifacts)
        for artifact in artifacts:
            hashes=artifact.findall('v:sha256',ns)
            require(hashes and all(re.fullmatch('[0-9a-f]{64}',h.get('value','')) for h in hashes))
        require(any(p.name.endswith('.lockfile') for p in files))
    report.check('reviewed-android-locks-and-metadata',dependency_trust)
    if product=='bgm':
        def npm_check():
            lock=read(root/'coordinator/package-lock.json')
            require(lock['lockfileVersion']==3)
            count=0
            for location,item in lock['packages'].items():
                if not location or item.get('link') or 'node_modules/' not in location: continue
                name=location.rsplit('node_modules/',1)[1]
                require(item.get('integrity') and item.get('resolved','').startswith('https://registry.npmjs.org/'))
                record={k:item[k] for k in ('version','integrity','resolved')}
                require(record in baseline['npm'].get(name,[])); count+=1
            # A dependency-free native Node candidate legitimately has zero packages.
            for rel in ('coordinator/package.json',):
                package=read(root/rel)
                require(package.get('engines',{}).get('node') in ('>=22','>=22.23.3'))
                require(package.get('private') is True)
                for group in ('dependencies','devDependencies','optionalDependencies'):
                    for name,version in package.get(group,{}).items():
                        require(any(v['version']==version for v in baseline['npm'].get(name,[])))
        report.check('retained-npm-lock-provenance',npm_check)
    report.data['notVerified']=['Full P4 source/asset audit, runtime tests, SDK provenance and build outputs.']

def main():
    p=argparse.ArgumentParser(); p.add_argument('mode',choices=['contract','preflight'])
    p.add_argument('--product',choices=['bgm','launcher'],required=True)
    p.add_argument('--root',type=Path); p.add_argument('--report',type=Path,required=True)
    args=p.parse_args(); report=Report(args.product,args.mode)
    if args.mode=='contract': contract_checks(report)
    else:
        report.check('candidate-preflight',lambda: preflight(report,args.root,args.product))
    return report.finish(args.report)
if __name__=='__main__': sys.exit(main())
