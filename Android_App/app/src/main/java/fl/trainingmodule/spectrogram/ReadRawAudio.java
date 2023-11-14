package fl.trainingmodule.spectrogram;
import android.os.Environment;

import java.io.File;

public class ReadRawAudio {
    int[] buffer;
    double[] signal;

    public int[] readdouble()
    {
        try
        {
            // Open the wav file specified as the first argument
            WavFile wavFile = WavFile.openWavFile(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/mu/LDC93S1.wav"));

            // Display information about the wav file
            wavFile.display();

            // Get the number of audio channels in the wav file
            int numChannels = wavFile.getNumChannels();
            System.out.println("Number of frames is : ");
            System.out.println(wavFile.getNumFrames());
            long totalframes = wavFile.getNumFrames();

            // Create a buffer of 100 frames
            buffer = new int[(int) totalframes * numChannels];
            //signal = new double[100000];

            int framesRead;

            do
            {
                // Read frames into buffer
                framesRead = wavFile.readFrames(buffer, (int) totalframes);

            }
            while (framesRead != 0);

            // Close the wavFile
            wavFile.close();

            // Output the minimum and maximum value
            //System.out.printf("Min: %f, Max: %f\n", min, max);

        }
        catch (Exception e)
        {
            System.err.println(e);
        }
        return buffer;
    }

}
