"""Evaluate a local Eclipse benchmark ZIP against its sparse XML reference labels."""
import argparse
import json
import platform
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from evaluate_dataset import aggregate, pair_metrics, parse_pairs, sha256, write_report
from validate_dataset import require, source_lines


def reference_cases(archive):
    """Version 1 is the common source; later VERSION elements supply sparse answers."""
    cases = []
    for name in sorted(archive.namelist()):
        if not name.startswith('eclipseTest/') or not name.endswith('.xml'):
            continue
        root = ET.fromstring(archive.read(name))
        stem = Path(root.attrib['FILE']).stem
        require('/' not in stem and '\\' not in stem and ':' not in stem, 'Unsafe benchmark file name')
        for version in root.findall('VERSION'):
            number = int(version.attrib['NUMBER'])
            if number == 1:
                continue
            labels = defaultdict(set)
            for location in version.findall('LOCATION'):
                old, new = int(location.attrib['ORIG']), int(location.attrib['NEW'])
                require(old > 0 and (new == -1 or new > 0), 'Invalid benchmark line number')
                labels[old].add(new)
            require(bool(labels), 'No benchmark labels')
            require(all(-1 not in values or len(values) == 1 for values in labels.values()), 'Conflicting deletion labels')
            cases.append({'case_id': f'{stem}-1-{number}', 'old_member': f'eclipseTest/{stem}_1.java',
                          'new_member': f'eclipseTest/{stem}_{number}.java', 'reference_member': name,
                          'reference_checked': version.get('CHECKED') == 'TRUE', 'labels': dict(labels)})
    return cases


def score_reference(labels, predicted):
    """Ignore predictions for unlabeled old lines. Deletions have explicit -1 labels."""
    expected = {(old, new) for old, targets in labels.items() for new in targets if new != -1}
    scoped = {(old, new) for old, new in predicted if old in labels}
    targets = defaultdict(set)
    for old, new in predicted:
        targets[old].add(new)
    correct = change = spurious = eliminate = 0
    for old, reference in labels.items():
        actual = targets[old]
        wanted = reference - {-1}
        if actual == wanted:
            correct += 1
        elif not wanted:
            spurious += 1
        elif not actual:
            eliminate += 1
        else:
            change += 1
    return {**pair_metrics(expected, scoped), 'labeled_old_lines': len(labels),
            'correct_labeled_old_lines': correct, 'change': change, 'spurious': spurious,
            'eliminate': eliminate, 'labeled_line_accuracy': correct / len(labels),
            'missing_pairs': sorted(expected-scoped), 'unexpected_pairs': sorted(scoped-expected)}


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive',type=Path)
    parser.add_argument('--jar',required=True,type=Path)
    parser.add_argument('--java',default='java')
    parser.add_argument('--code-revision',required=True)
    parser.add_argument('--advanced',action='store_true')
    parser.add_argument('--max-lcs-cells',type=int,help='Optional explicit resource guard, supported by the integrated CLI')
    parser.add_argument('--timeout',type=float,default=60)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(argv)
    require(args.output.resolve() not in {args.archive.resolve(),args.jar.resolve()},'Output would overwrite an input')
    require(0 < args.timeout < float('inf'), 'Timeout must be finite and positive')
    require(args.max_lcs_cells is None or args.max_lcs_cells > 0, 'Cell limit must be positive')
    version=subprocess.run([args.java,'-version'],capture_output=True,text=True,timeout=args.timeout,check=True)
    results=[]
    with zipfile.ZipFile(args.archive) as archive, tempfile.TemporaryDirectory() as directory:
        root=Path(directory)
        cases=reference_cases(archive)
        require(bool(cases),'No Eclipse benchmark cases found')
        for case in cases:
            entry={key:value for key,value in case.items() if key!='labels'}
            try:
                old=root/'old.java'; new=root/'new.java'
                old.write_bytes(archive.read(case['old_member']))
                new.write_bytes(archive.read(case['new_member']))
                old_count,new_count=len(source_lines(old)),len(source_lines(new))
                require(all(1<=o<=old_count and all(n == -1 or 1<=n<=new_count for n in ns) for o,ns in case['labels'].items()),'Reference line outside source file')
                entry.update(old_line_count=old_count,new_line_count=new_count,old_sha256=sha256(old),new_sha256=sha256(new))
                command=[args.java,'-jar',str(args.jar.resolve()),'map',str(old),str(new)]
                if args.advanced: command.append('--advanced')
                if args.max_lcs_cells is not None: command.extend(['--max-lcs-cells',str(args.max_lcs_cells)])
                started=time.perf_counter()
                process=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=args.timeout)
                entry['elapsed_ms_including_jvm_startup']=(time.perf_counter()-started)*1000
                require(process.returncode==0,f'CLI exit {process.returncode}: {process.stderr[:2000]}')
                predicted=parse_pairs(process.stdout,old_count,new_count)
                entry.update(status='ok',**score_reference(case['labels'],predicted))
            except (OSError,ValueError,KeyError,subprocess.SubprocessError) as error:
                entry.update(status='error',error=str(error))
            results.append(entry)
            print(case['case_id'],entry['status'],entry.get('error',''),flush=True)
    successful=[case for case in results if case['status']=='ok']
    line_counts={key:sum(case[key] for case in successful) for key in ('labeled_old_lines','correct_labeled_old_lines','change','spurious','eliminate')}
    checked=[case for case in successful if case['reference_checked']]
    pooled=aggregate(results)
    for key in ('expected_split_groups','correct_split_groups','split_group_exact_accuracy','unmatched_classification_accuracy'):
        pooled.pop(key,None)
    report={'created_utc':datetime.now(timezone.utc).isoformat(),'archive_sha256':sha256(args.archive),
            'archive_name':args.archive.name,'jar_sha256':sha256(args.jar),'evaluator_sha256':sha256(Path(__file__)),
            'code_revision':args.code_revision,'mode':'advanced' if args.advanced else 'baseline',
            'java_version':(version.stderr+version.stdout).strip(),'python_version':platform.python_version(),'platform':platform.platform(),
            'settings':{'max_lcs_cells':args.max_lcs_cells or 4000000,'candidate_limit':15,'context_radius':4,'minimum_score':0.65,'max_split_fragments':4},
            'reference_scope':'Only labeled old lines in XML VERSION >= 2; version 1 is common original. NEW=-1 denotes deletion.',
            'limitations':['Archive and original sources remain local; redistribution permission unconfirmed.',
                           'DoubleCache references are marked CHECKED=FALSE in the supplied archive; reported separately.',
                           'Historical slide percentages use other tools/protocols and are not reproduced by these pair metrics.',
                           'Memory guard is recorded in settings; failures stay visible and are excluded from successful-case scores. An increased guard changes resource allowance only, not similarity thresholds.',
                           'No tuning on this benchmark; no labels inferred for unannotated lines.'],
            'complete':len(successful)==len(results),**pooled,**line_counts,
            'labeled_line_accuracy':line_counts['correct_labeled_old_lines']/line_counts['labeled_old_lines'] if line_counts['labeled_old_lines'] else None,
            'checked_reference_micro':aggregate(checked)['micro_over_successful_cases'],'cases':results}
    write_report(args.output,report)
    print('Saved',args.output)
    return 0 if report['complete'] else 1


if __name__=='__main__':
    sys.exit(main())
