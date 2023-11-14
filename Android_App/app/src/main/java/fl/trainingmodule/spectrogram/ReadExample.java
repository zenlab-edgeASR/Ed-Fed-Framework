package fl.trainingmodule.spectrogram;
import android.os.Environment;

import java.io.File;

public class ReadExample
{
	double[] buffer;
	double[] signal;
	long samplingFreq;
	long bitsPerSample;

	public double[] readaudio(File file)
	{
		try
		{
			// Open the wav file specified as the first argument
			WavFile wavFile = WavFile.openWavFile(new File(String.valueOf(file)));
			samplingFreq=wavFile.getSampleRate();
			bitsPerSample=wavFile.getValidBits();

			// Display information about the wav file
			wavFile.display();

			// Get the number of audio channels in the wav file
			int numChannels = wavFile.getNumChannels();

			long totalframes = wavFile.getNumFrames();

			// Create a buffer of 100 frames
			buffer = new double[(int) totalframes * numChannels];

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
