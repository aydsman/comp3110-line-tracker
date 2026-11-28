import io
import unittest
import zipfile
from evaluate_existing import reference_cases,score_reference


class ExistingBenchmarkTests(unittest.TestCase):
    def test_sparse_predictions_ignore_unlabeled_lines_and_count_deletions(self):
        scores=score_reference({1:{2},3:{-1},4:{5}}, {(1,2),(2,99),(3,4)})
        self.assertEqual(scores['true_positives'],1)
        self.assertEqual(scores['predicted_pairs'],2)
        self.assertEqual(scores['expected_pairs'],2)
        self.assertEqual(scores['correct_labeled_old_lines'],1)
        self.assertEqual(scores['spurious'],1)
        self.assertEqual(scores['eliminate'],1)
        self.assertEqual(scores['change'],0)

    def test_all_split_targets_required_for_line_correctness(self):
        self.assertEqual(score_reference({1:{2,3}}, {(1,2),(1,3)})['labeled_line_accuracy'],1)
        partial=score_reference({1:{2,3}}, {(1,2)})
        self.assertEqual(partial['change'],1)
        self.assertAlmostEqual(partial['f1'],2/3)

    def test_version_one_is_source_not_a_second_reference(self):
        buffer=io.BytesIO()
        with zipfile.ZipFile(buffer,'w') as archive:
            archive.writestr('eclipseTest/A.xml','<TEST FILE="A.java"><VERSION NUMBER="1"><LOCATION ORIG="1" NEW="1" /></VERSION><VERSION NUMBER="2" CHECKED="FALSE"><LOCATION ORIG="1" NEW="-1" /></VERSION></TEST>')
        with zipfile.ZipFile(buffer) as archive:
            cases=reference_cases(archive)
        self.assertEqual(len(cases),1)
        self.assertFalse(cases[0]['reference_checked'])
        self.assertEqual(cases[0]['labels'],{1:{-1}})


if __name__=='__main__': unittest.main()
