"""Portable public source build: Python 3.10+, JDK 25+, pinned Maven Central artifacts."""
import argparse,hashlib,json,os,pathlib,platform,shutil,subprocess,sys,urllib.request,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def host_platform():
 if platform.machine().lower() not in ('amd64','x86_64'):raise RuntimeError('This preview supports Windows/Linux x86-64 only; ARM64 packaging is pending.')
 if sys.platform=='win32':return 'win'
 if sys.platform.startswith('linux'):return 'linux'
 raise RuntimeError('This preview supports Windows/Linux x86-64 only.')
def tool(name):
 suffix='.exe' if os.name=='nt' else '';home=os.environ.get('JAVA_HOME');candidate=pathlib.Path(home)/'bin'/(name+suffix) if home else None
 if candidate and candidate.is_file():return str(candidate)
 found=shutil.which(name)
 if not found:raise RuntimeError('Install JDK 25 or newer and set JAVA_HOME or PATH; missing '+name)
 return found
def run(arguments):subprocess.run([str(item) for item in arguments],cwd=ROOT,check=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--offline',action='store_true',help='Require all dependency artifacts already cached');parser.add_argument('--test',action='store_true',help='Run self-contained public source tests');args=parser.parse_args();host=host_platform();java,javac,jar=tool('java'),tool('javac'),tool('jar');check=subprocess.run([javac,'-version'],capture_output=True,text=True,check=True)
 import re
 match=re.search(r'javac (\d+)',check.stdout+check.stderr)
 if not match or int(match.group(1))<25:raise RuntimeError('JDK 25 or newer is required.')
 lock=json.loads((ROOT/'dependencies.lock.json').read_text(encoding='utf-8'));cache=ROOT/'.tools/public-dependencies';cache.mkdir(parents=True,exist_ok=True);dependencies=[]
 for item in lock['artifacts']:
  if item['platform'] not in ('all',host):continue
  name=item['name']
  if pathlib.Path(name).name!=name or not item['url'].startswith('https://repo.maven.apache.org/maven2/'):raise RuntimeError('Invalid dependency lock entry')
  path=cache/name
  if not path.exists() or sha(path)!=item['sha256']:
   if args.offline:raise RuntimeError('Missing or invalid cached dependency: '+name)
   request=urllib.request.Request(item['url'],headers={'User-Agent':'Bright-Stats-source-build'})
   with urllib.request.urlopen(request,timeout=90) as response:data=response.read(item['bytes']+1)
   if len(data)!=item['bytes'] or hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('Dependency integrity check failed: '+name)
   temporary=path.with_suffix('.download');temporary.write_bytes(data);os.replace(temporary,path)
  dependencies.append(path)
 destination=ROOT/'build/public'/('windows-x64' if host=='win' else 'linux-x64');classes=destination/'classes';libraries=destination/'lib';tests=destination/'test-classes'
 if not destination.resolve().is_relative_to((ROOT/'build/public').resolve()):raise RuntimeError('Build destination escaped its workspace')
 temporary=destination/'tmp';temporary.mkdir(parents=True,exist_ok=True)
 # Delete only generated subdirectories after checking their exact resolved workspace roots.
 for generated in (classes,libraries,tests):
  if generated.resolve().parent!=destination.resolve() or generated.name not in ('classes','lib','test-classes') or generated.is_symlink():raise RuntimeError('Unsafe build output path')
  if generated.exists():shutil.rmtree(generated)
  generated.mkdir(parents=True)
 for dependency in dependencies:shutil.copyfile(dependency,libraries/dependency.name)
 cp=os.pathsep.join(map(str,dependencies));sources=sorted((ROOT/'src/main/java').rglob('*.java'));fingerprints={p.relative_to(ROOT).as_posix():sha(p) for p in sorted((ROOT/'src').rglob('*')) if p.is_file()}
 arguments=destination/'compile.args';arguments.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources),encoding='utf-8');run([javac,'-encoding','UTF-8','--release','25','-cp',cp,'-d',classes,'@'+str(arguments)])
 shutil.copytree(ROOT/'src/main/resources',classes,dirs_exist_ok=True);manifest=destination/'MANIFEST.MF';line='Class-Path: '+' '.join('lib/'+p.name for p in dependencies if not p.name.startswith('javafx-'));lines=[]
 while len(line)>70:lines.append(line[:70]);line=' '+line[70:]
 lines.append(line);manifest.write_text('Manifest-Version: 1.0\nMain-Class: org.brightstats.cli.Main\n'+'\n'.join(lines)+'\n\n',encoding='utf-8');product=destination/'bright-stats.jar';run([jar,'-J-Djava.io.tmpdir='+str(temporary),'--create','--file',product,'--manifest',manifest,'--date=2026-11-01T00:00:00Z','-C',classes,'.']);run([java,'-jar',product,'--version']);run([java,'-jar',product,'pls',ROOT/'examples/pls.json'])
 notices=destination/'notices';notices.mkdir(exist_ok=True)
 for dependency in dependencies:
  with zipfile.ZipFile(dependency) as archive:
   target=notices/dependency.stem;target.mkdir(exist_ok=True)
   for entry in archive.namelist():
    name=pathlib.PurePosixPath(entry).name
    if entry.startswith('META-INF/') and '/' not in entry[len('META-INF/'):] and any(token in name.upper() for token in ('LICENSE','NOTICE','COPYING')):(target/name).write_bytes(archive.read(entry))
 checked=[]
 if args.test:
  test_sources=sorted((ROOT/'src/test/java').rglob('*.java'));arguments.write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in test_sources),encoding='utf-8');test_cp=str(classes)+os.pathsep+cp;run([javac,'-encoding','UTF-8','--release','25','-cp',test_cp,'-d',tests,'@'+str(arguments)])
  for name in ['data.BoundedInputChecks','data.DataTransformsChecks','data.WorkspaceChecks','data.PreferencesChecks','stats.PairwiseChecks','stats.HigherOrderChecks','stats.AdjustedCopulaChecks','stats.ComponentAdjustedCopulaChecks','data.ExtendedInputChecks','stats.HierarchyPipelineChecks','stats.LatentInteractionChecks','stats.FimlChecks','stats.ImputationChecks','stats.OrdinalChecks','data.OrdinalDataChecks','stats.SimulationChecks','stats.ClusterChecks','data.ClusterDataChecks','stats.RegularizedChecks','stats.BayesianChecks','stats.ExtendedLatentChecks','stats.SensitivityChecks','report.ReproducibilityChecks','desktop.ExampleChecks']:
   output=destination/'test-results'/name;output.mkdir(parents=True,exist_ok=True);run([java,'-Xmx512m','--module-path',libraries,'--add-modules','javafx.controls','-cp',str(tests)+os.pathsep+test_cp,'org.brightstats.'+name,output]);checked.append(name)
 if fingerprints!={p.relative_to(ROOT).as_posix():sha(p) for p in sorted((ROOT/'src').rglob('*')) if p.is_file()}:raise RuntimeError('Source changed during the public build')
 receipt={'state':'passed','platform':host,'artifact':product.relative_to(ROOT).as_posix(),'sha256':sha(product),'production_sources':len(sources),'public_tests':checked,'dependency_sha256_verified':len(dependencies),'private_reference_files_required':False};(destination/'build-receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
