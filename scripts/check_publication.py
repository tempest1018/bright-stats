"""Check the exact staged public delta without printing suspected secret values."""
import hashlib,json,pathlib,re,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
ALLOWED_ROOT={'.gitattributes','.gitignore','README.md','SECURITY.md','THIRD_PARTY.md','dependencies.lock.json'}
ALLOWED_DOCS={'docs/'+name for name in ['INSTALLATION.md','USER_GUIDE.md','KNOWN_ISSUES.md','DEVELOPMENT.md']}
ALLOWED_SCRIPTS={'scripts/'+name for name in ['build_public.py','run_public.py','check_publication.py']}
ALLOWED_EXAMPLES={'examples/'+name for name in ['README.md','demo.csv','pls.json']}
PATTERNS={'private key':r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----','GitHub token':r'(?:gh[pousr]_[A-Za-z0-9]{24,}|github_pat_[A-Za-z0-9_]{25,})','OpenAI credential':r'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{30,}', 'Google credential':r'\bAIza[A-Za-z0-9_-]{30,}', 'Slack credential':r'\bxox[baprs]-[A-Za-z0-9-]{20,}', 'AWS credential':r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b','private Windows profile':r'(?i)[A-Z]:[\\/]Users[\\/](?!Public\b)[^\\/\s]+','private host context':r'(?i)\bspark-[0-9a-f]{4}\b|t(?:ailscale|ailnet)','hardcoded credential':r'(?im)(?:api[_-]?key|access[_-]?token|password|secret)\s*[:=]\s*[\'\"][A-Za-z0-9_+/=-]{20,}[\'\"]'}
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def main():
 paths=git('diff','--cached','--name-only','--diff-filter=ACMR','-z').decode('utf-8').split('\0');paths=[path for path in paths if path];findings=[];manifest=[]
 if not paths:raise SystemExit('No staged publication files')
 for name in paths:
  if not (name.startswith('src/main/') or name.startswith('src/test/') or name in ALLOWED_ROOT|ALLOWED_DOCS|ALLOWED_SCRIPTS|ALLOWED_EXAMPLES):findings.append({'path':name,'category':'outside public allowlist'})
  content=git('show',':'+name);manifest.append({'path':name,'bytes':len(content),'sha256':hashlib.sha256(content).hexdigest()})
  if len(content)>2000000:findings.append({'path':name,'category':'unexpected large file'})
  try:text=content.decode('utf-8')
  except UnicodeDecodeError:findings.append({'path':name,'category':'binary/non-UTF8 file'});continue
  for category,pattern in PATTERNS.items():
   if re.search(pattern,text):findings.append({'path':name,'category':category})
 if findings:print(json.dumps({'state':'failed','findings':findings},indent=2));raise SystemExit(1)
 print(json.dumps({'state':'passed','files':len(manifest),'bytes':sum(item['bytes'] for item in manifest),'files_manifest':manifest},indent=2))
if __name__=='__main__':main()
