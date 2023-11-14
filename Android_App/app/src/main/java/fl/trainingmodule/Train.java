package fl.trainingmodule;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.os.Environment;
import android.util.Log;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import fl.PhoneDataCollection.DataCollectionService;
import fl.android_client.SecondFragment;
import fl.trainingmodule.preprocessing.getandsavemelspect;
import fl.trainingmodule.wer.WER_LER_RemovedSpaces;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Train {
    //hols the cer,wer and ss values for each inference drawn
    static ArrayList<Float> cerlist = new ArrayList<Float>();
    static ArrayList<Float> werlist = new ArrayList<Float>();
//    static ArrayList<Float> bertlist = new ArrayList<Float>();

    static ArrayList<float[][][][]> batches; //holds the melspect features for training for batchsize=5 (i.e 5 samples in a batch)
    static ArrayList<int[][]> batcheslabels; //holds the stringlabels converted to int for batchsize=5 (i.e 5 samples in a batch)
    static ArrayList<String> train_labels_string;  //holds the train labels in Array List of String values

    static ArrayList<float[][][][]> mel_batches_for_inference; //hols the melspect features for inference with batchsize=1
    static ArrayList<String> test_labels_string;  //holds the test labels in Array List of String values

    //if want to change the input no of frames to ds2 model's tflite, then change the below variables
    private static int noofframes = 750; //the input number of frames to the tflite
    private static int ypredframeno = ((noofframes/2)-9); //outputframes got from the model. Equivalent to (noofframes/2)-9
    //

    private static int melfeatures = 80; //the melfeatures extracted from a wav file per frame
    private static int charnum = 150;    //the number of characters in the ground truth file
    private static int batchsize = 5; //the number of samples considered in a batch during training

    //names of the files
    public static String path = "/data/data/fl.android_client/files"; //app data path in internal storage of phone to store all ckpt and csvs
    public static String ckptname = "";
    public static String ondevicetrainingcsvname = "onDeviceTraining_dataset.csv";

    /** Load ds2 model tflite from assests. */
    public static MappedByteBuffer loadModelFile(Activity activity, String modeltflitename) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modeltflitename);
        Log.v("Tflite model is loaded for training",modeltflitename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /** Load bert model tflite from assests. */
//    public static MappedByteBuffer loadBERTModelFile(Activity activity) throws IOException {
//        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd("mobilebert_working.tflite");
//        Log.v("Tflite model is loaded for training","mobilebert_working.tflite");
//        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
//        FileChannel fileChannel = inputStream.getChannel();
//        long startOffset = fileDescriptor.getStartOffset();
//        long declaredLength = fileDescriptor.getDeclaredLength();
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
//    }

    /** Load dictionary from assets. */
//    public static Map<String, Integer> loadDictionaryFile(Activity activity) throws IOException {
//        Map<String, Integer> dic = new HashMap<>();
//        try (InputStream ins = activity.getAssets().open("vocab.txt");
//             BufferedReader reader = new BufferedReader(new InputStreamReader(ins))) {
//            int index = 0;
//            while (reader.ready()) {
//                String key = reader.readLine();
//                dic.put(key, index++);
//            }
//        }
//        return dic;
//    }

    /** check if the number of lines in the ondevicetraining_dataset.csv is equal or more than number of training+test samples specified. */
   public static Boolean isCSVFull() throws IOException {
        String filename = "/onDeviceTraining_dataset.csv";
        int count = 0;
        try {
            File filleddataset = new File(Environment.getExternalStorageDirectory() + filename);
            CSVReader reader = new CSVReader(new FileReader(filleddataset));
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                // nextLine[] is an array of values from the line
                System.out.println(nextLine[0] + nextLine[1] + "etc...");
                count += 1;
            }
        } catch (IOException | CsvValidationException e) {
            System.out.println("IO ISSUE!!!!!");
        }
        System.out.println("number of lines" + count);
        if (count >= 100) {
            return (true);
        } else {
            return (false);
        }
    }

    /** Log the results into the TrainingLog.txt in the app's internal storage. */
    public static void generateNote(String sBody) {
        try {
            File root = new File(path);
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, "TrainingLog.txt");
            FileWriter writer = new FileWriter(gpxfile, true);
            writer.write(sBody);

            writer.flush();
            writer.close();
            // Toast.makeText(context, "Writting to TXT File Named TrainingLog", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** generate the ASR hypothesis in string from a particular ckpt. As well as generate wer,cer and ss. The inputs are mel spectrogram and its labels
     * @return*/
    public static ArrayList<Float> generateInference(String ckptname, Activity activity, String tflitename) throws IOException {

        char[] characters = {' ', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        float totalcer = 0f;
        float totalwer = 0f;
//        float totalbert = 0f;

        //test related variables
        mel_batches_for_inference = new ArrayList<>(); //holds the melspectrogram values of test set
        test_labels_string = new ArrayList<>(); //holds the labels of the test set in string

        try {
            // Retrieve the test melspectrogram and their labels list from internal storage
            HashMap<String,ArrayList> cachedEntries = (HashMap<String,ArrayList>) getandsavemelspect.InternalStorage.readObject(activity.getApplicationContext(), "Test_MelSpectrogram");

            // Retrieve the values in the hashmap using their respective variable names as keys from the melspectrogram file in cache. (see line 157 to 162 in getandsavemelspect.java)
            mel_batches_for_inference = cachedEntries.get("mel_batches_for_inference");
            test_labels_string = cachedEntries.get("test_labels_string");

        } catch (IOException e) {
            Log.e("Reading melspect from IS", e.getMessage());
        } catch (ClassNotFoundException e) {
            Log.e("Reading melspect from IS", e.getMessage());
        }

        //initialising for bert/SS values
//        Interpreter bert_interpreter = new Interpreter(Train.loadBERTModelFile(activity));
//        Map<String, Integer> dic = loadDictionaryFile(activity);
//        calltokenizer ct = new calltokenizer();

        for (int sample = 0; sample < mel_batches_for_inference.size(); sample++) {
            try (Interpreter another_interpreter = new Interpreter(loadModelFile(activity,tflitename))) {
                // Load the trained weights from the checkpoint file.
                File outputFile = new File(path, ckptname);
                System.out.println("filedir" + outputFile.getAbsolutePath());

                //Restore the tflite with the ckpt weights using the "restore" signature
                //input to the signature is the ckpt path of the checkpoint weights that you would like to infer on
                Map<String, Object> inputs = new HashMap<>();
                inputs.put("checkpoint_path", outputFile.getAbsolutePath());
                Map<String, Object> outputs = new HashMap<>();
                another_interpreter.runSignature(inputs, outputs, "restore");
                System.out.println("DS2 WEIGHTS RESTORED");

                //initialise the y_pred_output to store the output of the inference
                float[][][] y_pred_output = new float[1][ypredframeno][28];

                //Run the inference using the "infer" signature
                //input to the inference is the melspectrogram of each sample stored inside mel_batches_for_inference
                //output is a prediction of probability matric given as y_pred_output
                Map<String, Object> infer_inputs =  new HashMap<>();
                Map<String, Object> infer_outputs = new HashMap<>();
                infer_inputs = new HashMap<>();
                infer_inputs.put("x", mel_batches_for_inference.get(sample));
                infer_outputs = new HashMap<>();
                infer_outputs.put("output", y_pred_output);
                another_interpreter.runSignature(infer_inputs, infer_outputs, "infer");

                //get bestpath results
                int prev = -1;
                // Process the result to get the final category values.
                String result = "";
                float maxim = -1000000000;
                int index = -1;

                for (int x = 0; x < ypredframeno; ++x) {
                    maxim = -1000000000;
                    index = -1;
                    for (int y = 0; y < 28; ++y) {
                        if (y_pred_output[0][x][y] > maxim) {
                            maxim = y_pred_output[0][x][y];
                            index = y;
                        }
                    }
                    if (index != 27 && index != prev) {
                        result += characters[index];
                    }
                    prev = index;
                }

                //with the bestpath resultant string (stored as "result") as your hypothesis
                //and string labels in test_labels_string as your groundtruth
                //calculate the wer,cer and the ss values using their respective
                System.out.println("this is ur output:  -> " + result);
                generateNote('\n'+result+',');
                float wer = WER_LER_RemovedSpaces.wer(test_labels_string.get(sample), result, Boolean.FALSE, " ");
                generateNote(String.valueOf(wer)+',');
                float cer = WER_LER_RemovedSpaces.cer(test_labels_string.get(sample), result, Boolean.FALSE, Boolean.FALSE);
                generateNote(String.valueOf(cer));
//                float mobilebertval = ct.predict(bert_interpreter,dic, test_labels_string.get(sample), result);
//                generateNote(String.valueOf(mobilebertval)+'\n');

                totalcer += cer;
                totalwer += wer;
//                totalbert += mobilebertval;
                System.out.println("\nWER  = " + wer);
                System.out.println("\nLER  = " + cer);

                result = "";
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        ArrayList<Float> avg_criteria_values = new ArrayList<>(3);
        avg_criteria_values.add(totalwer / mel_batches_for_inference.size());
        avg_criteria_values.add(totalcer / mel_batches_for_inference.size());
//        avg_criteria_values.add(totalbert / mel_batches_for_inference.size());

//        bert_interpreter.close();
//        dic.clear();

        return avg_criteria_values;
    }


    @SuppressLint("LongLogTag")
    public static Float trainthemodel(Activity activity, String round, String tflitename, Double training_time, String trainingmode, int no_of_epochs) throws IOException {
        Log.v("Step 6: start training", String.valueOf(1));
        //get the ckptname for starting training
        ckptname = "FL_weights_" + round + ".ckpt";
        String logvalue;
        //train related variables
        batches = new ArrayList<float[][][][]>(); //holds the melspectrogram values of each batch in the training list
        batcheslabels = new ArrayList<>(); //holds the labels in int format of each batch in the training list

        //test related variables
        mel_batches_for_inference = new ArrayList<>(); //holds the melspectrogram values of test set
        test_labels_string = new ArrayList<>(); //holds the labels of the test set in string

        //list of wer,cer and ss values for multiple epochs
        werlist = new ArrayList<>();
        cerlist = new ArrayList<>();
        Float sec;
//        bertlist = new ArrayList<>();

        //generate inference for the global ckpt or the global weights
//        long inference_start = System.currentTimeMillis();
//        ArrayList<Float> avg_criteria_for_each_epoch = generateInference(ckptname,activity,tflitename);
//        long inference_end = System.currentTimeMillis();
//        //finding the time difference and converting it into seconds
//        float sec = (inference_start - inference_end) / 1000F;
//        System.out.println(sec + " seconds");
//        String logvalue = "Inference before training time:" + "\n" + sec + " seconds";
        //SpeechToTextMainActivity.setMessage(logvalue, "");


//        generateNote(logvalue+'\n');
//        werlist.add(avg_criteria_for_each_epoch.get(0));
//        cerlist.add(avg_criteria_for_each_epoch.get(1));
//        bertlist.add(avg_criteria_for_each_epoch.get(2));

        werlist.add(1F);
        DataCollectionService dinstance = new DataCollectionService();


        try {
            // Retrieve the melspectrogram and their labels list from internal storage
            HashMap<String,ArrayList> cachedEntries = (HashMap<String,ArrayList>) getandsavemelspect.InternalStorage.readObject(activity.getApplicationContext(), "Train_MelSpectrogram");

            // Retrieve the train items in the hashmap using their respective variable names as keys from the melspectrogram file in cache. (see line 157 to 162 in getandsavemelspect.java)
            batches = cachedEntries.get("batches");
            batcheslabels = cachedEntries.get("batcheslabels");

            // Retrieve the melspectrogram and their labels list from internal storage
            HashMap<String,ArrayList> cachedEntries_test = (HashMap<String,ArrayList>) getandsavemelspect.InternalStorage.readObject(activity.getApplicationContext(), "Test_MelSpectrogram");

            // Retrieve the test items in the hashmap using their respective variable names as keys from the melspectrogram file in cache. (see line 157 to 162 in getandsavemelspect.java)
            mel_batches_for_inference = cachedEntries_test.get("mel_batches_for_inference");
            test_labels_string = cachedEntries_test.get("test_labels_string");

        } catch (IOException e) {
            Log.e("Retrieving melspect from Int.Sto.", e.getMessage());
        } catch (ClassNotFoundException e) {
            Log.e("Retrieving melspect from Int.Sto.", e.getMessage());
        }

        //Toast.makeText(SpeechToTextMainActivity.speechToTextMainActivity.getApplicationContext(), "TRAINING STARTED HOLD ON", Toast.LENGTH_LONG).show();
        try (Interpreter interpreter = new Interpreter(Train.loadModelFile(activity, tflitename))) {

            //train over the ckpt with ckptname gotten using round info
            File outputFile = new File(path, ckptname);
            System.out.println("filedir" + outputFile.getAbsolutePath());
            Map<String, Object> rinputs = new HashMap<>();
            rinputs.put("checkpoint_path", outputFile.getAbsolutePath());
            Map<String, Object> routputs = new HashMap<>();
            interpreter.runSignature(rinputs, routputs, "restore");


            //initialize min_wer with baseline wer
            float min_wer = werlist.get(0) ; //
            int epoch = 1;
            int continuetraining =1 ;
            int checkextraepoch = 0 ;
            long training_start=System.currentTimeMillis();
            double alpha=0;
            //start training for n epochs or with stopping criteria in place
//            String trainingmode="null";
            String phone_parameters = dinstance.startSystemParamCollectionInstance(activity,-1,-1);
            String[] phone_parameters_list = phone_parameters.split(", ", 0);

            if (trainingmode.equals("1")){
                trainingmode="train_c1_d2";
            }
//            if (trainingmode.equals("0.5")){
//                trainingmode="train_b2_d2";
//            }
//            if (trainingmode.equals("0.25")){
//                trainingmode="train_b4_d2";
//            }
//            if((Float.parseFloat(phone_parameters_list[3])/Float.parseFloat(phone_parameters_list[2])>=0.5) && (Float.parseFloat(phone_parameters_list[4])>=60 || phone_parameters_list[5].equals("true"))) {
//                trainingmode="train_full";
//            }
//            else if((Float.parseFloat(phone_parameters_list[3])/Float.parseFloat(phone_parameters_list[2])>=0.35) && (Float.parseFloat(phone_parameters_list[4])>=60 || phone_parameters_list[5].equals("true"))){
//                trainingmode = "train_blstm";
//            }
//            else if((Float.parseFloat(phone_parameters_list[3])/Float.parseFloat(phone_parameters_list[2])>=0.15) && (Float.parseFloat(phone_parameters_list[4])>=60 || phone_parameters_list[5].equals("true"))){
//                trainingmode="train_dense";
//            }
//            else{
//                trainingmode="none";
//            }
//            System.out.println("trainingmode: "+trainingmode);
//
//            trainingmode = "train_dense";


            System.out.println("dinstance.phone_parametrs: "+phone_parameters);
            System.out.println("Individual parameters: "+Arrays.toString(phone_parameters_list));

//            no_of_epochs=1;
            while(epoch <=no_of_epochs){                            // ((continuetraining==1 )&& (epoch <20)  ){           // && ((System.currentTimeMillis() + alpha)<=training_time)) {

                logvalue = "\n================================= EPOCH NUMBER " + epoch + "\n";
                Log.d("",logvalue);
                generateNote(logvalue+'\n');
                System.out.println(batches.size());

                //START TRAINING
                long start = System.currentTimeMillis();
                for (int i = 0; i < batches.size(); ++i) {
                    long epoch_batch_start = System.currentTimeMillis();
                    phone_parameters = dinstance.startSystemParamCollectionInstance(activity,epoch,i);
                    System.out.println("TRAINING:\n" + "EPOCH: " + (epoch) + "\n Batch: " + (i));
                    //restore the ckpt
                    File reoutputFile = new File(path, ckptname);
                    System.out.println("filedir" + reoutputFile.getAbsolutePath());
                    Map<String, Object> reinputs = new HashMap<>();
                    reinputs.put("checkpoint_path", reoutputFile.getAbsolutePath());
                    Map<String, Object> reoutputs = new HashMap<>();
                    interpreter.runSignature(reinputs, reoutputs, "restore");
                    System.out.println("DS2 WEIGHTS RESTORED");

                    //check if this part is needed//
                    Map<String, Object> node2inputs = new HashMap<>();
                    node2inputs.put("checkpoint_path", reoutputFile.getAbsolutePath());
                    Map<String, Object> node2outputs = new HashMap<>();
                    interpreter.runSignature(node2inputs, node2outputs, "get_nodenames_sizes");
                    System.out.println("got nodenames and sizes");
                    //

                    //train the model..
                    //first inputs is one batch of melspectrogram values with batchsize=5 within batches Arraylist
                    //second input are the respective labels of the melspectrogram values stored in batches)
                    Map<String, Object> inputs = new HashMap<>();
                    inputs.put("x", batches.get(i));
                    inputs.put("y", batcheslabels.get(i));
                    Map<String, Object> outputs = new HashMap<>();
                    FloatBuffer loss = FloatBuffer.allocate(batchsize);
                    outputs.put("loss", loss);
//                    System.out.println("BATCH: " + i);
//                    trainingmode=
                    interpreter.runSignature(inputs, outputs,"train"); //, trainingmode);
                    System.out.println("ctc loss for batch -> " + loss);

                    //save the trained batch into the checkpoint
                    File saveoutputFile = new File(path, ckptname);
                    Map<String, Object> saveinputs = new HashMap<>();
                    saveinputs.put("checkpoint_path", saveoutputFile.getAbsolutePath());
                    Map<String, Object> saveoutputs = new HashMap<>();
                    interpreter.runSignature(saveinputs, saveoutputs, "save");
                    long epoch_batch_end = System.currentTimeMillis();
                    long epoch_batch_sec = (long) ((epoch_batch_end - epoch_batch_start) / 1000F);
                    logvalue = "TRAINING FINISHED for epoch " +epoch+ " batch "+ i + ":" + epoch_batch_sec + " seconds \n";
                    generateNote(logvalue+'\n');
                }
                long end = System.currentTimeMillis();
//                if (epoch==1){
//                    alpha=end-start;
//                }
                //finding the time difference and converting it into seconds
                sec = (end - start) / 1000F;
                System.out.println(sec + " seconds");
                logvalue = "TRAINING FINISHED for epoch " +epoch+ ":" + sec + " seconds \n";
                //SpeechToTextMainActivity.setMessage(logvalue, "");
                generateNote(logvalue+'\n');

                //generate inference at end of each epoch
//                avg_criteria_for_each_epoch = generateInference(ckptname,activity,tflitename);
//                werlist.add(avg_criteria_for_each_epoch.get(0));
//                cerlist.add(avg_criteria_for_each_epoch.get(1));
//                bertlist.add(avg_criteria_for_each_epoch.get(2));

//                String tempdisplay = "\n" + "AVG WER" + Arrays.toString(werlist.toArray()) + "\n" + "AVG CER" + Arrays.toString(cerlist.toArray()) + "\n";// + "AVG BERT" + Arrays.toString(bertlist.toArray());
//                String tempdisplay = "AVG WER" + Arrays.toString(werlist.toArray()) + "\n" + "AVG CER" + Arrays.toString(cerlist.toArray()) + "\n";

//                generateNote(tempdisplay+'\n');
//                SecondFragment.setMessage(activity,tempdisplay);

                //stopping criteria
//                 if checkextraepoch is set to true then stop training
//                if (checkextraepoch==1) {
//                    continuetraining = 0; //if it set to 0, then training will stop/halt
//                }

                //if wer increases even once, set checkextraepoch flag as true. The training will continue for one more epoch and stop
//                if (epoch>1){
//                    if ((werlist.get(epoch) >= werlist.get(epoch-1)) && (checkextraepoch==0)) {
//                        checkextraepoch = 1;
//                    }
//                }

//                if (werlist.get(epoch)<min_wer){
//                    //save checkpoint under the respective Epoch number
//                    String epochname = "min_checkpoint.ckpt";
//                    File saveoutputFile = new File(path, epochname);
//                    Map<String, Object> saveinputs = new HashMap<>();
//                    saveinputs.put("checkpoint_path", saveoutputFile.getAbsolutePath());
//                    Map<String, Object> saveoutputs = new HashMap<>();
//                    interpreter.runSignature(saveinputs, saveoutputs, "save");
//                    min_wer = werlist.get(epoch);
//                }

//                String epochname = "Epoch_"+epoch+".ckpt";
//                File saveoutputFile = new File(path, epochname);
//                Map<String, Object> saveinputs = new HashMap<>();
//                saveinputs.put("checkpoint_path", saveoutputFile.getAbsolutePath());
//                Map<String, Object> saveoutputs = new HashMap<>();
//                interpreter.runSignature(saveinputs, saveoutputs, "save");

                epoch+=1;
            }

            long training_end = System.currentTimeMillis();
            //finding the time difference and converting it into seconds
            sec = (training_start - training_end) / 1000F;
            System.out.println(sec + " seconds");
            logvalue = "TOTAL TRAINING TIME for round: " + round + "\n" + sec + " seconds";
            //SpeechToTextMainActivity.setMessage(logvalue, "");

            generateNote(logvalue+'\n');
            for(Object obj : batches){
                obj = null;
            }
            for(Object obj : batcheslabels){
                obj = null;
            }
//            for(Object obj : train_labels_string){
//                obj = null;
//            }
            for(Object obj : mel_batches_for_inference){
                obj = null;
            }
            for(Object obj : test_labels_string){
                obj = null;
            }
            batches=null;
            batcheslabels=null;
            train_labels_string=null;
            mel_batches_for_inference=null;
            test_labels_string=null;
            System.gc();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(werlist);
        return 0.5F;
    }
}

