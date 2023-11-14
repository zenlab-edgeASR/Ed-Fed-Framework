package fl.trainingmodule.preprocessing;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvValidationException;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fl.trainingmodule.spectrogram.MelSpectrogram;
import fl.trainingmodule.spectrogram.ReadExample;


public class getandsavemelspect {

    static ArrayList<float[][][][]> batches; //holds the melspect features for training for batchsize=5 (i.e 5 samples in a batch)
    static ArrayList<int[][]> batcheslabels; //holds the stringlabels converted to int for batchsize=5 (i.e 5 samples in a batch)
    static ArrayList<String> train_labels_string;  //holds the train labels in Array List of String values

    static ArrayList<float[][][][]> mel_batches_for_inference; //holds the melspect features for inference with batchsize=1
    static ArrayList<String> test_labels_string;  //holds the test labels in Array List of String values

    private static int noofframes = 750; //the input number of frames to the tflite
    private static int melfeatures = 80; //the melfeatures extracted from a wav file per frame
    private static int charnum = 150;    //the max number of characters in the ground truth file
    private static int batchsize = 5; //the number of samples considered in a batch during training


    public static String path = "/data/data/fl.android_client/files"; //app data path in internal storage of phone to store all ckpt and csvs

    /** first compute melspectrogram array and then save them in a hashmap in the files directory of the app's internal storage
     trainingsamples - the number of training samples needed for this round. received from the server.
     testsamples - the number of test samples needed for this round. use a fixed number of test samples.
     offset - the number of training samples to be skipped as they were already used in the previous rounds.
     **/

    @SuppressLint("LongLogTag")
    public static void trainsamples_melspect(Activity activity, int trainingsamples, int offset) {
        char[] characters = {' ', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

        try {
            Log.v("Pre-processing", "Starting");
            //train related variables
            batches = new ArrayList<float[][][][]>(); //holds the melspect features for training for batchsize=5 (i.e 5 samples in a batch)
            batcheslabels = new ArrayList<>(); //holds the stringlabels converted to int for batchsize=5 (i.e 5 samples in a batch)
            train_labels_string = new ArrayList<>();   //holds the training labels in Array List of String values

            int samplenumber = 0;

            float[][][][] tempmel = new float[batchsize][noofframes][melfeatures][1]; //tempmel stores the melspectrogram features of a batch of wav files.. tensorflow requires a 4D array as an input
            int[][] tempytrue = new int[batchsize][charnum]; //tempytrue stores the labels in int values

            // start computing melspectrogram for the train set
            CSVReader reader = new CSVReader(new FileReader(new File(path, "onDeviceTraining_dataset.csv")));
            List<String[]> odt_csv_reader = reader.readAll();
            int total_num_of_lines = odt_csv_reader.size();
            String[] nextLine;
            //because the csv lines are stored in an array list, we start with offset (for second round, we have to leave out the first few lines) and end at (trainingsamples)
            int start_linenum = offset;
            int end_linenum = (offset+trainingsamples);

            while (start_linenum < (end_linenum)) {
                // nextLine[] is an array of values from that line
                nextLine = odt_csv_reader.get(start_linenum);

                //get the labels from the csv and remove special characters and make all lower case
                //nextline[1] is the second column which has the labels
                String labels = nextLine[1].toLowerCase().replaceAll("[^\\w\\s]", "");
                Log.d("labels : ", labels);

                //Read the wav file next
                //instantiate the readExample code to read wav file
                //nextline[0] is the first column which has the wav file names
                //call melspectrogram instance and call the "process" class with wav values and sampling frequency as 16k
                ReadExample readExample = new ReadExample();
                File file = new File(Environment.getExternalStorageDirectory(), "Music/dataset"); //read from the external storage, inside Music/dataset directory
                double[] read_audio = readExample.readaudio(new File(file, nextLine[0]));
                MelSpectrogram melSpectrogram = new MelSpectrogram();
                float[][] melInput = melSpectrogram.process(read_audio, 16000);

                Log.d("Frame size of wav files : ", String.valueOf(melInput.length));

                //copy the melspectrogram values into an array with specific batch number. Pad it with least value if frames are lower than noofframes AND truncate it, if melspectrogram frames increase noofframes
                for (int x = 0; x < noofframes; x++) {
                    for (int y = 0; y < melfeatures; y++) {
                        if (x >= melInput.length) {
                            tempmel[samplenumber][x][y][0] = (float) -13.815511;
                        } else {
                            tempmel[samplenumber][x][y][0] = melInput[x][y];
                        }
                    }
                }

                //convert the string labels to its respective int values as defined in "characters" for the specific batch number
                train_labels_string.add(labels);
                String tempstring = labels;
                int tempvar = 0;
                for (char c : tempstring.toCharArray()) {
                    tempytrue[samplenumber][tempvar] = ArrayUtils.indexOf(characters, c);
                    tempvar++;
                    if (tempvar == charnum) {
                        break;
                    }
                }
                //pad the training ends to ctc blank
                while (tempvar < charnum) {
                    tempytrue[samplenumber][tempvar] = 0;
                    tempvar++;
                }

                //increment the sample number
                samplenumber++;

                //when the samplenumber equals the batchsize (5).. add the melspectrogram and labels of that batch into batches and bactheslabels respectively
                if (samplenumber == batchsize) {
                    batches.add(tempmel);
                    batcheslabels.add(tempytrue);
                    tempmel = new float[samplenumber][noofframes][melfeatures][1];
                    tempytrue = new int[samplenumber][charnum];
                    samplenumber = 0;
                }
                //increase line number to read next line of the csv
                start_linenum += 1;
            }

            // NOW WE ARE SAVING THE MELSPPECTROGRAM TO CACHE
            // The hashmap of data that should be saved to internal storage.

            HashMap<String, ArrayList> entries = new HashMap();
            entries.put("batches", batches);
            entries.put("batcheslabels", batcheslabels);

            try {
                // Save the hashmap entries to internal storage
                getandsavemelspect.InternalStorage.writeObject(activity.getApplicationContext(), "Train_MelSpectrogram", entries);
                Log.v("Saved melspectrogram into cache", "DONE");

            } catch (IOException e) {
                Log.e("Saving melspect to internal storage", e.getMessage());
            }

        } catch (IndexOutOfBoundsException | FileNotFoundException e) {
            System.out.println("Error while preprocessing " + e);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("LongLogTag")
    public static void testsamples_melspect(Activity activity, int testsamples) {
        char[] characters = {' ', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

        try {
            Log.v("Pre-processing", "Starting");

            //test related variables
            mel_batches_for_inference = new ArrayList<>(); //holds the melspect features for inference with batchsize=1
            test_labels_string = new ArrayList<>(); //holds the test labels in Array List of String values


            // start computing melspectrogram for the train set
            CSVReader reader = new CSVReader(new FileReader(new File(path, "onDeviceTraining_dataset.csv")));
            List<String[]> odt_csv_reader = reader.readAll();
            int total_num_of_lines = odt_csv_reader.size();
            String[] nextLine;

            // start computing melspectrogram for the test set
            // read the last n samples in ondevicetraining.csv for test set
            int start_linenum = (total_num_of_lines - testsamples);
            int end_linenum = total_num_of_lines;


            while (start_linenum < (end_linenum)) {
                // nextLine[] is an array of values from that line
                nextLine = odt_csv_reader.get(start_linenum);

                //get the labels from the csv and remove special characters and make all lower case
                //nextline[1] is the second column which has the labels
                String labels = nextLine[1].toLowerCase().replaceAll("[^\\w\\s]", "");
                Log.d("labels : ", labels);

                //Read the wav file next
                //instantiate the readExample code to read wav file
                //nextline[0] is the first column which has the wav file names
                //call melspectrogram instance and call the "process" function with the audio values and sampling frequency as 16k
                ReadExample readExample = new ReadExample();
                File file = new File(Environment.getExternalStorageDirectory(), "Music/dataset"); //read from the external storage, inside Music/dataset directory
                double[] read_audio = readExample.readaudio(new File(file, nextLine[0]));
                MelSpectrogram melSpectrogram = new MelSpectrogram();
                float[][] melInput = melSpectrogram.process(read_audio, 16000);


                Log.d("Frame size of wav files : ", String.valueOf(melInput.length));

                //create a temp 4D array with size 1 as inference happens only on BS=1 and copy the melfeatures to it
                float temp1bs[][][][] = new float[1][noofframes][melfeatures][1];
                for (int x = 0; x < noofframes; x++) {
                    for (int y = 0; y < melfeatures; y++) {
                        if (x >= melInput.length) {
                            temp1bs[0][x][y][0] = (float) -13.815511;
                        } else {
                            temp1bs[0][x][y][0] = melInput[x][y];
                        }
                    }
                }
                //for inference we add the just one melspectrogram array at a time. Therefore, BS=1 for inference.
                mel_batches_for_inference.add(temp1bs);
                Log.d("test labels", labels);
                test_labels_string.add(labels);
                //increase line number to read next line of the csv
                start_linenum += 1;
            }


            // NOW WE ARE SAVING THE MELSPPECTROGRAM TO CACHE
            // The hashmap of data that should be saved to internal storage.

            HashMap<String, ArrayList> entries = new HashMap();
            entries.put("mel_batches_for_inference", mel_batches_for_inference);
            entries.put("test_labels_string", test_labels_string);

            try {
                // Save the hashmap entries to internal storage
                getandsavemelspect.InternalStorage.writeObject(activity.getApplicationContext(), "Test_MelSpectrogram", entries);
                Log.v("Saved Test melspectrogram into cache", "DONE");

            } catch (IOException e) {
                Log.e("Saving Test melspect to internal storage", e.getMessage());
            }

        } catch (IndexOutOfBoundsException | FileNotFoundException e) {
            System.out.println("Error while preprocessing " + e);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvException e) {
            e.printStackTrace();
        }
    }


    public static class InternalStorage{

        private InternalStorage() {}

        public static void writeObject(Context context, String key, Object object) throws IOException {
            FileOutputStream fos = context.openFileOutput(key, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(object);
            oos.close();
            fos.close();
        }

        public static Object readObject(Context context, String key) throws IOException,
                ClassNotFoundException {
            FileInputStream fis = context.openFileInput(key);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object object = ois.readObject();
            return object;
        }
    }

}
