package fl.trainingmodule.wer;

import static java.lang.Double.NaN;
import static java.lang.Double.isNaN;

import java.io.IOException;

public class WER_LER_RemovedSpaces {

    private static boolean True;
    private static boolean False;

    public static int min(int val1,int val2, int val3)
    {
        int min, m_min;
        int Inf= Integer.MAX_VALUE;
        min = Math.min(isNaN(val1) ? Inf : val1, isNaN(val2) ? Inf : val2); // returns minimum
        if (min==Inf)
            min= (int) NaN;

        m_min = Math.min(isNaN(val3) ? Inf : val3, isNaN(min) ? Inf : min); // returns minimum
        if (m_min==Inf)
            m_min= (int) NaN;

        return m_min;
    }


    public static int _levenshtein_distance (String[] reference, String[] hypothesis){
//    Levenshtein distance is a string metric for measuring the difference
//    between two sequences. Informally, the levenshtein disctance is defined as
//    the minimum number of single-character edits (substitutions, insertions or
//    deletions) required to change one word into the other. We can naturally
//    extend the edits to word level when calculate levenshtein disctance for
//    two sentences.

        int m = reference.length;
        int n = hypothesis.length;
        //System.out.println("Reference and hypothesis are: "+ m + " " + n);
//        for(int i=0;i<reference.length;i++){
//            System.out.println(reference[i]);
//        }

        String[] tmp;
        int tmp_int;
        int prev_row_idx;
        int cur_row_idx;
        int s_num;
        int d_num;
        int i_num;

        //special case
        if (reference.equals(hypothesis))
            return 0;
        if (m == 0)
            return n;
        if (n == 0)
            return m;

        if (m < n) {
            tmp = hypothesis;
            hypothesis = reference;
            reference = tmp;
            tmp_int = m;
            m = n;
            n = tmp_int;
        }

        //calculate levenshtein distance
        int[][] distance = new int[2][n+1];
        for (int j = 0; j < (n+1); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i < (m+1); i++)
        {
            prev_row_idx = ((i - 1) % 2);
            //System.out.println(String.valueOf(prev_row_idx)+" : prev row idx");
            cur_row_idx = (i % 2);
            //System.out.println(String.valueOf(cur_row_idx)+" : cur_row_idx");
            distance[cur_row_idx][0] = i;
            for(int j=1;j<(n+1);j++)
            {
                //System.out.println("Reference is:"+ref[i-1]);
                //System.out.println("Hypothesis is:"+hyp[j-1]);
                if (reference[i-1].equals(hypothesis[j-1])) {
                    distance[cur_row_idx][j] = distance[prev_row_idx][j - 1];
                }
                else {
                    s_num = distance[prev_row_idx][j - 1] + 1;
                    i_num = distance[cur_row_idx][j - 1] + 1;
                    d_num = distance[prev_row_idx][j] + 1;
                    distance[cur_row_idx][j] = min(s_num, i_num, d_num);
                }
            }
        }
        return (distance[m % 2][n]);
    }

    public static float cer(String reference, String hypothesis, Boolean ignore_case, Boolean remove_space)
    {
//        """Calculate charactor error rate (CER). CER compares reference text and
//        hypothesis text in char-level. CER is defined as:
//        .. math::
//            CER = (Sc + Dc + Ic) / Nc
//        where
//        .. code-block:: text
//            Sc is the number of characters substituted,
//            Dc is the number of characters deleted,
//            Ic is the number of characters inserted
//            Nc is the number of characters in the reference
//        We can use levenshtein distance to calculate CER. Chinese input should be
//        encoded to unicode. Please draw an attention that the leading and tailing
//        space characters will be truncated and multiple consecutive space
//        characters in a sentence will be replaced by one space character.
//        :param reference: The reference sentence.
//        :type reference: basestring
//        :param hypothesis: The hypothesis sentence.
//        :type hypothesis: basestring
//        :param ignore_case: Whether case-sensitive or not.
//        :type ignore_case: bool
//        :param remove_space: Whether remove internal space characters
//        :type remove_space: bool
//        :return: Character error rate.
//        :rtype: float
//        :raises ValueError: If the reference length is zero.
//        """
        int ref_len=0;
        if (ignore_case) {
            reference = reference.toLowerCase();
            hypothesis = hypothesis.toLowerCase();
        }

        if (remove_space) {
            reference = reference.replaceAll("\\s+","");
            hypothesis = hypothesis.replaceAll("\\s+","");
        }

        //System.out.println("Reference is:"+reference);
        //System.out.println("Hypothesis is:"+hypothesis);
        String ref1 = reference.trim().replaceAll(" +", " ");
        String hyp1 = hypothesis.trim().replaceAll(" +", " ");
        //System.out.println("Reference is:"+ref1);
        //System.out.println("Hypothesis is:"+hyp1);
        String[] ref = ref1.split("");
        String[] hyp = hyp1.split("");
        float edit_distance = _levenshtein_distance(ref, hyp);
        //System.out.println("Edit distance is:"+edit_distance);
        ref_len = ref.length;
        //System.out.println("Reference length is:"+ref_len);

        if (reference.isEmpty()) {
            System.err.print("Length of reference should be greater than 0.");
            System.exit(0);
        }

        float cer = (edit_distance/ref_len);
        return cer;
    }

    public static float wer(String reference, String hypothesis, Boolean ignore_case, String delimiter)
    {
//        """Calculate word error rate (WER). WER compares reference text and
//        hypothesis text in word-level. WER is defined as:
//        .. math::
//            WER = (Sw + Dw + Iw) / Nw
//        where
//        .. code-block:: text
//            Sw is the number of words subsituted,
//            Dw is the number of words deleted,
//            Iw is the number of words inserted,
//            Nw is the number of words in the reference
//        We can use levenshtein distance to calculate WER. Please draw an attention
//        that empty items will be removed when splitting sentences by delimiter.
//        :param reference: The reference sentence.
//        :type reference: basestring
//        :param hypothesis: The hypothesis sentence.
//        :type hypothesis: basestring
//        :param ignore_case: Whether case-sensitive or not.
//        :type ignore_case: bool
//        :param delimiter: Delimiter of input sentences.
//        :type delimiter: char
//        :return: Word error rate.
//        :rtype: float
//        :raises ValueError: If word number of reference is zero.
//        """
        int ref_len=0;
        if (ignore_case) {
            reference = reference.toLowerCase();
            hypothesis = hypothesis.toLowerCase();
        }

        //System.out.println("Reference is:"+reference);
        //System.out.println("Hypothesis is:"+hypothesis);

        String ref1 = reference.trim().replaceAll(" +", " ");
        String hyp1 = hypothesis.trim().replaceAll(" +", " ");
        String[] ref = ref1.split(delimiter);
        String[] hyp = hyp1.split(delimiter);
        float edit_distance = _levenshtein_distance(ref, hyp);

        //System.out.println("Edit distance is:"+edit_distance);
        ref_len = ref.length;
        //System.out.println("Reference length is:"+ref_len);

        if (reference.isEmpty()) {
            System.err.print("Length of reference should be greater than 0.");
            System.exit(0);
        }

        float wer = (edit_distance/ref_len);
        return wer;
    }

    public static void main (String[] args) throws IOException {

        String  ref = "keep in mind that smoking is prohibited anywhere on this flight  including the use and charging of electronic cigarettes including the washroom";
        String hyp = "keep in mind that smoking is prohibited anywhere on this flight including the use and charging of electronic cigaretes including the washroom";
        //System.out.println(ref);
        //System.out.println(hyp);

//        float cer = cer(ref,hyp, Boolean.FALSE, Boolean.FALSE);
//        System.out.println("cer: "+cer);

        float wer = wer(ref,hyp, Boolean.FALSE, " ");
        System.out.println("wer: "+wer);


    }

}
