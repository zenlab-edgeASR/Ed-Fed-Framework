package fl.trainingmodule.spectrogram;

import android.util.Log;


import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.io.IOException;
import java.util.Arrays;


public class MelSpectrogram {

    private static double[][] MAG;
    private static double[][] pSpec;
    private static int L;
    private static double W, N, SP, nfft;
    private static double[][] y;
    private static float[][] logfeat;

    public static double[] hanning(double W){
        int i;
        int k=1;
        double[] wnd = new double[(int) W];
        for( i=0; i<=((W/2)-1); i++)
        {
            wnd[i]=0.5-0.5*Math.cos(2*Math.PI*(i/(W-1)));
        }
        for(i= (int) (W/2); i<W; i++)
        {
            wnd[i]=wnd[(int) ((W/2)-k)];
            k++;
        }
        return wnd;
    }
    private static double[][] segment_y(double[] noisy, double W, double SP, double[] wnd){
        double L=noisy.length;
        int i;
        int start=0;
        double [][] Seg = new double [(int) W][(int) N];
        double[] segment = new double[(int) W];
        for (i=0;i<N;i++)
        {
            for (int j = 0; j<(int) W; j++) {
                segment[j]= (double) noisy[start+j];
                Seg[j][i] = segment[j] * wnd[j];
            }
            start= (int) (start+SP);
        }
        return Seg;
    }

    private static double[][] ypad(double[][] y, double nfft){
        double L=y.length;
        int i;
        int start=0;
        double [][] Seg = new double [(int) nfft][(int) y[0].length];
        for (i=0;i<(y[0].length);i++)
        {
            for (int j = (int) W; j<(int) nfft; j++) {
                Seg[j][i]= 0.0;
            }
        }
        return Seg;
    }

    //mel to hz, htk, librosa
    private static double[] melToFreqS(double[] mels) {
        double[] freqs = new double[mels.length];
        for (int i = 0; i < mels.length; i++) {
            freqs[i] = 700.0 * (Math.pow(10, mels[i] / 2595.0) - 1.0);
        }
        return freqs;
    }

    // hz to mel, htk, librosa
    protected static double[] freqToMelS(double[] freqs) {
        double[] mels = new double[freqs.length];
        for (int i = 0; i < freqs.length; i++) {
            mels[i] = 2595.0 * log10(1.0 + freqs[i] / 700.0);
        }
        return mels;
    }

    // hz to mel, Slaney, librosa
    private static double[] freqToMel(double[] freqs) {
        final double f_min = 0.0;
        final double f_sp = 200.0 / 3;
        double[] mels = new double[freqs.length];

        // Fill in the log-scale part

        final double min_log_hz = 1000.0;                         // beginning of log region (Hz)
        final double min_log_mel = (min_log_hz - f_min) / f_sp;  // # same (Mels)
        final double logstep = Math.log(6.4) / 27.0;              // step size for log region

        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] < min_log_hz) {
                mels[i] = (freqs[i] - f_min) / f_sp;
            } else {
                mels[i] = min_log_mel + Math.log(freqs[i] / min_log_hz) / logstep;
            }
        }
        return mels;
    }

    //mel to hz, Slaney, librosa
    private static double[] melToFreq(double[] mels) {
        // Fill in the linear scale
        final double f_min = 0.0;
        final double f_sp = 200.0 / 3;
        double[] freqs = new double[mels.length];

        // And now the nonlinear scale
        final double min_log_hz = 1000.0;                         // beginning of log region (Hz)
        final double min_log_mel = (min_log_hz - f_min) / f_sp;  // same (Mels)
        final double logstep = Math.log(6.4) / 27.0;

        for (int i = 0; i < mels.length; i++) {
            if (mels[i] < min_log_mel) {
                freqs[i] = f_min + f_sp * mels[i];
            } else {
                freqs[i] = min_log_hz * Math.exp(logstep * (mels[i] - min_log_mel));
            }
        }
        return freqs;
    }

    // log10
    private static double log10(double value) {
        return Math.log(value) / Math.log(10);
    }

    private static double[][] getMAG(double[][] y, int nfft)
    {
        //Log.d("inside getMAG -------->","1");
        FastFourierTransformer fastFourierTransformer = new FastFourierTransformer(DftNormalization.STANDARD);
        //Log.d("inside getMAG -------->","2");
        double[] y_segment = new double[(int) nfft];
        double[][] Yabs = new double[(int) ((nfft / 2) + 1)][(int) N];
        Complex[] Y = new Complex[(int) (W)];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < W; j++) {
                y_segment[j] = (y[j][i]);
            }
            Y = fastFourierTransformer.transform(y_segment, TransformType.FORWARD);
            for (int a = 0; a <= (nfft / 2); a++) {
                Yabs[a][i] = Math.pow((Math.pow(Y[a].getReal(), 2) + Math.pow(Y[a].getImaginary(), 2)), 0.5);
            }
        }
        //Log.d("inside getMAG -------->","3");
        return Yabs;
    }

    private static double[][] getPOW(double[][] y)
    {
        double[][] pSpec = new double[y.length][y[0].length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < y[0].length; j++) {
                pSpec[i][j] = Math.pow(y[i][j],2);
            }
        }
        return pSpec;
    }
    private static double[] linspace(double fMin, double fMax, double nmels) {
        double[] arr= new double[(int) nmels];
        double step = (fMax - fMin) / (nmels - 1);
        for (int i = 0; i < nmels; i++) {
            arr[i]=(fMin + (step * i));
        }
        return arr;
    }

    private static double[][] get_filterbanks(int M, int nfft, double[] flow, double[] fhigh, int samplerate) {

        //compute points evenly spaced in mels
        double[] lowmel = freqToMelS(flow);
        double[] highmel = freqToMelS(fhigh);
        double[] melpoints = linspace(lowmel[0], highmel[0], (M+2));
        melpoints= melToFreqS(melpoints);

        //our points are in Hz, but we use fft bins, so we have to convert from Hz to fft bin number
        //bin = numpy.floor((nfft+1)*mel2hz(melpoints)/samplerate)
        double[] bin = new double[melpoints.length];
        for (int i=0; i<melpoints.length;i++){
            bin[i]=Math.floor((nfft+1)*(melpoints[i])/samplerate);
        }

        final double[][] fbank = new double[M][((nfft/2) +1)];
        for (int j=0;j<M;j++){
            int i = (int) bin[j];
            while(i<bin[j+1]){
                fbank[j][i] = (i - bin[j]) / (bin[j+1]-bin[j]);
                i++;
            }
            i= (int) bin[j+1];
            while(i<bin[j+2]){
                fbank[j][i] = (bin[j+2]-i) / (bin[j+2]-bin[j+1]);
                i++;
            }
        }
        return fbank;
    }

    // Function to multiply
    // two matrices A[][] and B[][]
    private static double[][] multiplyMatrix(int row1, int col1, double A[][], int row2, int col2, double B[][])
    {
        int i, j, k;

        // Check if multiplication is Possible
        if (row2 != col1) {
            System.out.println("\nMultiplication Not Possible");
        }

        // Matrix to store the result
        // The product matrix will
        // be of size row1 x col2
        double C[][] = new double[row1][col2];

        // Multiply the two marices
        for (i = 0; i < row1; i++) {
            for (j = 0; j < col2; j++) {
                for (k = 0; k < row2; k++)
                    C[i][j] += A[i][k] * B[k][j];
            }
        }
    return C;
    }

    // Function to multiply
    // two matrices A[][] and B[][]
    private static float[][] getlogFBE(double A[][])
    {
        // Matrix to store the result
        // The product matrix will
        // be of size row1 x col2
        float C[][] = new float[A.length][A[0].length];

        // Multiply the two marices
        for (int i = 0; i < A.length; i++) {
            for (int j = 0; j < A[0].length; j++) {
                    C[i][j] = (float) Math.log(A[i][j]);
            }
        }
        return C;
    }

    //dct, librosa
    private static double[][] dctFilter(int n_filters, int n_input) {
        //Discrete cosine transform (DCT type-III) basis.
        double[][] basis = new double[n_filters][n_input];
        double[] samples = new double[n_input];
        for (int i = 0; i < n_input; i++) {
            samples[i] = (1 + 2 * i) * Math.PI / (2.0 * (n_input));
        }
        for (int j = 0; j < n_input; j++) {
            basis[0][j] = 1.0 / Math.sqrt(n_input);
        }
        for (int i = 0; i < n_filters; i++) {
            for (int j = 0; j < n_input; j++) {
                basis[i][j] = Math.cos(i * samples[j]) * Math.sqrt(2.0 / (n_input));
            }
        }
        return basis;
    }

    private static double[] ceplifter(int N,int L){
    //ceplifter = @( N, L )( 1+0.5*L*sin(pi*[0:N-1]/L) );
        double[] ceplif=new double[N];
        for(int i=0;i<N;i++){
            ceplif[i]=1+0.5*L*Math.sin(Math.PI*i/L);
        }
        return ceplif;
    }

    private static double[][] diag(double[] array){
        double[][] diagarr=new double[array.length][array.length];
        for(int i=0;i<array.length;i++){
                diagarr[i][i] = array[i];
        }
        return diagarr;
    }
    public static float[][] transpose(float[][] array){
        int i=array.length; //number of rows
        int j=array[0].length; //number of columns
        float[][] b=new float[j][i];
        float[][] a;
        a=array;
        for(int r = 0;r<j;r++) {
            for (int c = 0; c < i; c++) {
                b[r][c] = a[c][r];
            }
        }
        return b;
    }
    private static double[][] concat2D(double[][] mat0, double[][] mat1, double[][] mat2) {
        int col = mat0[0].length;
        int row = mat0.length;
        double[][] outarr = new double[3*row][col];
        for (int x = 0; x < (row); x++) {
            for (int y = 0; y < col; y++) {
                outarr[x][y]= mat0[x][y];
            }
        }
        for (int x = row; x < (2*row); x++) {
            for (int y = 0; y < col; y++) {
                outarr[x][y]= mat1[x-row][y];
            }
        }
        for (int x = (2*row); x < (3*row); x++) {
            for (int y = 0; y < col; y++) {
                outarr[x][y]= mat2[x-(2*row)][y];
            }
        }
        return outarr;
    }

    private static double[] getenergy(double[][] array){
        double[] outarr=new double[array[0].length];
        for(int i=0;i<array[0].length;i++){
            for(int j=0;j<array.length;j++) {
                outarr[i] = outarr[i]+array[j][i];
            }
        }
        return outarr;
    }

    private static float findMean(float[] a)
    {
        float sum = 0;
        // total sum calculation of matrix
        for (int i = 0; i < a.length; i++){
                sum += a[i];
        }
        return (sum / a.length);
    }

    private static double[][] addingminimumvalue(double[][] a)
    {
        double[][] feat = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++){
            for (int j = 0; j < a[0].length; j++){
                feat[i][j]=a[i][j] + 1e-6;
            }
        }
        return feat;
    }

    // Function for
    // calculating stddev
    private static float stddev(float[] a, float mean)
    {
        float sum = 0;
        int n = a.length;
        float[] b = new float[n];

        for (int i = 0; i < n; i++)
        {
            // subtracting mean from elements
            sum += Math.pow((a[i] - mean), 2);
        }
        return (float) Math.sqrt(sum / n);
    }

    public static float[][] process(double[] audio1, int samplingFreq) throws IOException {
        //Log.d("inside mel","1");
        //ReadExample read = new ReadExample();
        double[] audio = audio1; //read.readaudio();

        int fs = (int) samplingFreq;



        L = audio.length;
        //System.out.println("audio length"+ String.valueOf(L));
        W = 512;
        nfft = 1024;
        SP = 256;
        N = Math.floor((L - W) / SP + 1); //number of frames
        //System.out.println("number of frames within mels"+ String.valueOf(N));

        //Framing and windowing (frames as columns)
        y = new double[(int) nfft][(int) N];
        //Log.d("inside mel","bebore hanning");
        y = segment_y(audio, W, SP, hanning(W));
        //Log.d("inside mel","after hanning");

        //Magnitude spectrum computation (as column vectors)
        MAG = new double[(int) (nfft/2)][(int) N];
        MAG = getMAG(y,(int) nfft); //magnitude

        pSpec = new double[(int) (nfft/2)][(int) N];
        pSpec = getPOW(MAG);

        //Log.d("inside mel","2");
        //Triangular filterbank with uniformly spaced filters on mel scale
        double[] fMin = new double[1];
        double[] fMax = new double[1];
        fMax[0]=fs/2;
        fMin[0]=64;
        int M = 80; // feature size
        double[][] H = get_filterbanks(M, (int) nfft, fMin, fMax, fs);


        //Filterbank application to unique part of the magnitude spectrum
        double[][] feat = new double[H.length][MAG[0].length];
        feat = multiplyMatrix(H.length, H[0].length, H, MAG.length, MAG[0].length, pSpec);
        feat = addingminimumvalue(feat);

        logfeat = getlogFBE(feat);
        //float logfeatT = [][logfeatT];
        //logfeatT = transpose(Logfeat);

        //normalize mel spectrogram
        float[] logfeat_axis = new float[logfeat[0].length];
        float[][] logfeat_t = transpose(logfeat);
        float[][] norm_mel = new float[logfeat[0].length][logfeat.length];

        for (int j=0;j<logfeat_t[0].length;j++) {
            for (int i = 0; i < logfeat_t.length; i++) {
                logfeat_axis[i]=logfeat_t[i][j];
            }
            //mean
            float mean_r= findMean(logfeat_axis);
            // for std deviation
            float stddev = stddev(logfeat_axis,mean_r);

            for (int i = 0; i < logfeat_t.length; i++) {
                norm_mel[i][j] = (float) ((logfeat_axis[i]-(mean_r))/stddev);
            }

        }
        return logfeat_t;
    }
}
