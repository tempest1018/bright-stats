"""Launch a public source build using the user's JDK; no private tool receipts."""
import argparse,os,pathlib,subprocess
from build_public import ROOT,host_platform,tool
parser=argparse.ArgumentParser();parser.add_argument('--example',action='store_true');args=parser.parse_args();host=host_platform();app=ROOT/'build/public'/('windows-x64' if host=='win' else 'linux-x64');product=app/'bright-stats.jar'
if not product.is_file():raise SystemExit('Run python scripts/build_public.py first.')
command=[tool('java'),'-Xmx2g','--module-path',str(app/'lib'),'--add-modules','javafx.controls','--enable-native-access=javafx.graphics','-cp',str(product)+os.pathsep+str(app/'lib/*'),'org.brightstats.desktop.Launcher']
if args.example:command+=['--open',str(ROOT/'examples/pls.json')]
subprocess.run(command,check=True,cwd=ROOT)
