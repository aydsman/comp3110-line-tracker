"""Check text/JSON equivalence, preserved inputs, exports and errors in an integrated CLI."""
import argparse
import json
import subprocess
import tempfile
from datetime import datetime,timezone
from pathlib import Path
from evaluate_dataset import parse_pairs,sha256,write_report
from validate_dataset import require,source_lines


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--java',default='java')
    parser.add_argument('--code-revision',required=True)
    parser.add_argument('--output',type=Path,default=Path('results/cli-audit.json'))
    args=parser.parse_args()
    root=Path(__file__).resolve().parent
    results=[]
    command=[args.java,'-jar',str(args.jar.resolve())]
    def run(*parts):
        return subprocess.run(command+list(map(str,parts)),capture_output=True,text=True,encoding='utf-8',timeout=60)
    for relative in ('datasets/new/manifest.json','datasets/development/manifest.json'):
        manifest=root/relative
        for case in json.loads(manifest.read_text()):
            old,new=manifest.parent/case['old'],manifest.parent/case['new']
            originals={path:sha256(path) for path in (old,new,manifest.parent/case['expected'])}
            for advanced in (False,True):
                options=['--advanced'] if advanced else []
                entry={'case_id':case['id'],'mode':'advanced' if advanced else 'baseline'}
                try:
                    text=run('map',old,new,*options)
                    exported=run('map',old,new,*options,'--format','json')
                    require(text.returncode==exported.returncode==0,'CLI comparison failed')
                    data=json.loads(exported.stdout)
                    pairs={(o,n) for group in data['matches'] for o in group['old_lines'] for n in group['new_lines']}
                    parsed=parse_pairs(text.stdout,len(source_lines(old)),len(source_lines(new)))
                    require(pairs==parsed,'Text/JSON mapping pairs differ')
                    require(set(data['deleted'])==set(range(1,len(source_lines(old))+1))-{o for o,n in pairs},'Deleted coverage differs')
                    require(set(data['added'])==set(range(1,len(source_lines(new))+1))-{n for o,n in pairs},'Added coverage differs')
                    require(all(sha256(path)==digest for path,digest in originals.items()),'Input changed')
                    entry.update(status='pass',pair_count=len(pairs))
                except (ValueError,OSError,KeyError,subprocess.SubprocessError) as error:
                    entry.update(status='fail',error=str(error))
                results.append(entry)
    with tempfile.TemporaryDirectory(prefix='line tracker audit ') as directory:
        folder=Path(directory)
        old=folder/'old file.txt'; new=folder/'new file.txt'; output=folder/'export.json'
        old.write_text('return alpha + beta;\ndone();\n')
        new.write_text('return alpha +\nbeta;\ndone();\n')
        checks=[('unknown option',lambda:run('map',old,new,'--unknown').returncode==2),
                ('missing input',lambda:run('map',folder/'missing.txt',new).returncode==1),
                ('invalid threshold',lambda:run('map',old,new,'--advanced','--minimum-score','NaN').returncode==2),
                ('memory guard',lambda:run('map',old,new,'--max-lcs-cells','1').returncode==1)]
        first=run('map',old,new,'--advanced','--format','json','--output',output)
        exported=output.read_bytes() if output.exists() else b''
        checks.extend([
            ('JSON export to spaced path',lambda:first.returncode==0 and first.stdout=='' and json.loads(exported)['matches'][0]['kind']=='split'),
            ('existing export preserved',lambda:run('map',old,new,'--output',output).returncode==1 and output.read_bytes()==exported),
            ('input export refused',lambda:run('map',old,new,'--output',old).returncode==1 and old.read_text()=='return alpha + beta;\ndone();\n')])
        for name,check in checks:
            try:
                require(check(),name)
                results.append({'check':name,'status':'pass'})
            except (OSError,ValueError,subprocess.SubprocessError) as error:
                results.append({'check':name,'status':'fail','error':str(error)})
    failed=sum(result['status']=='fail' for result in results)
    report={'created_utc':datetime.now(timezone.utc).isoformat(),'code_revision':args.code_revision,
            'jar_sha256':sha256(args.jar),'audit_sha256':sha256(Path(__file__)),'failed_checks':failed,
            'passed_checks':len(results)-failed,'limitations':['CLI text/JSON equivalence only; actual GUI and final release candidate still require testing.'],'checks':results}
    write_report(args.output,report)
    print(f'{len(results)-failed} passed, {failed} failed; {args.output}')
    return bool(failed)


if __name__=='__main__': raise SystemExit(main())
