package fl.android_client;


import static fl.utils.copytoappdata.copyfile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.widget.TextView;
import android.widget.Toast;
import java.util.StringTokenizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import fl.PhoneDataCollection.DataCollectionService;
import fl.PhoneDataCollection.DataCollections;
import fl.android_client.databinding.FragmentSecondBinding;
import fl.sendreceiveweights.getweightsfromtflite;
import fl.sendreceiveweights.saveweightstockpt;
import fl.trainingmodule.Train;
import fl.trainingmodule.preprocessing.getandsavemelspect;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Random;


public class SecondFragment extends Fragment {

    public FragmentSecondBinding binding;
    public ManagedChannel channel;
    private static String TAG = "FL_DESE";
    public Activity secondFragmentactivity;
    public static String path = "/data/data/fl.android_client/files";
    public static String ckptname = "";
    public static String tflitename = "With_set_weights_signature_750_150.tflite";
    public static int trainingsamples = 30;
    public static double training_time=500;

//    public static int phone_parameters = new HashMap<>();


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSecondBinding.inflate(inflater, container, false);

        return binding.getRoot();

    }


    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        secondFragmentactivity = getActivity();

        binding.buttonSecond.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            }
        });

        binding.participateinfl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("Participate in federated learning clicked ");
                String clientid = binding.clientid.getText().toString();
//                clientid= String.valueOf(new Random().nextInt(1000));
//                clientid= String.valueOf(1);
                System.out.println("client id :" + clientid);
                String host = binding.ipstr.getText().toString();
                System.out.println("host IP address :" + host);
                String portStr = binding.serverport.getText().toString();
                System.out.println("host port address :" + portStr);

                new SecondFragment.GrpcTask(getActivity())
                        .execute(clientid, "10.114.54.78","50057");
            }
        });
    }

    public static void generateNote(String sBody) {
        try {
            File root = new File(path);
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, "CommunicationLog.txt");
            FileWriter writer = new FileWriter(gpxfile, true);
            writer.write(sBody);

            writer.flush();
            writer.close();
            // Toast.makeText(context, "Writting to TXT File Named TrainingLog", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public static void setMessage(String text) {
//        TextView display = (TextView) getView().findViewById(R.id.responsefromserver);
//        FragmentActivity activity = getActivity();
//        activity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                display.setText("HELLO");
//            }
//
//        });
//    }

    public static void setMessage(Activity activity, String text) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
            }
        });
    }




    private static class GrpcTask extends AsyncTask<String, Void, String> {
        private final WeakReference<Activity> activityReference;
        private ManagedChannel channel;

        private GrpcTask(Activity activity) {
            this.activityReference = new WeakReference<Activity>(activity);
        }

        @SuppressLint("LongLogTag")
        @Override
        protected String doInBackground(String... params) {
            String client_ID = params[0];
            String host = params[1]; //host is the ip address of the server
            String portStr = params[2]; //the port string of the host server
            Activity activity = activityReference.get();
            int step = 1;
            long total_fl_time_start=System.currentTimeMillis();
            int port = TextUtils.isEmpty(portStr) ? 0 : Integer.valueOf(portStr);
            try {
                Context fl_context = activity.getApplicationContext();

                //run the datacollection code to get the system parameters
                DataCollectionService dinstance = new DataCollectionService();
                String phone_parameters = dinstance.startSystemParamCollectionInstance(activity,-3,-3);

                setMessage(activity,"Step " + step + "-> get phone related data ");
                Log.v("Step " + step ,  "Get phone related data ");
                step++;


                //get the current round information also with number of training samples to be skipped from the RoundInfo.txt in the app specific storage directory
                String roundinfo = getroundinfo(fl_context);
                String round = roundinfo.split(",")[0];
                int trainingsamples_offset = (Integer.parseInt(client_ID)%6)*20;                      //Integer.parseInt(roundinfo.split(",")[1]);
                trainingsamples_offset=0;
                ckptname = "FL_weights_" + round + ".ckpt";
                setMessage(activity,"Step " + step + "-> read RoundInfo.txt -> Round number is : "+String.valueOf(round));
                Log.v("Step " + step , "read RoundInfo.txt -> Round number is : "+String.valueOf(round));
                step++;

                //build a grpc channel and connect to the server
                channel = ManagedChannelBuilder.forAddress(host, port).maxInboundMessageSize(512*1024*1024).usePlaintext().build();
                FLGrpc.FLBlockingStub stub = FLGrpc.newBlockingStub(channel);
                setMessage(activity,"Step " + step + " -> build grpc channel with -> server ip : "+ host + " server port : " + port);
                Log.v("Step " + step, "build grpc channel with -> server ip : "+ host + " server port : " + port);
                step++;

                //create the melspectrogram for testing samples
                int testsamples = 15;
                getandsavemelspect.testsamples_melspect(activity, testsamples);
                setMessage(activity,"Step " + step + " -> pre-processing of test samples : " + testsamples);
                Log.v("Step " + step , "pre-processing test samples : " + testsamples);
                step++;


                //message the server with client id, criteria and flag
//                ArrayList<Float> avg_criteria_values = Train.generateInference(ckptname,activity,tflitename);
//                float wer = avg_criteria_values.get(0); //get the latest wer,cer, ss values of the latest ckpt
//                -1, -1, 8.0, 3.7524032592773438, 79, false, 0.029095994800759497
                String[] temp_phone_parametrs=phone_parameters.split(", ");
                String temp_var="";
                System.out.println(temp_phone_parametrs[5]);
                if (temp_phone_parametrs[5].equals("false")){
                    temp_var="0";
                }
                else if(temp_phone_parametrs[5].equals("true")){
                    temp_var="1";
                }
                System.out.println(temp_phone_parametrs[6]);
                phone_parameters=String.valueOf(trainingsamples)+","+temp_phone_parametrs[3]+","+temp_phone_parametrs[4]+","+temp_var+","+temp_phone_parametrs[6];
                System.out.println(phone_parameters);
                String msgtoserver = "ID:" + client_ID + "FLAG:" + 0 + "CRITERIA:" +  phone_parameters; //wer  ;//phoneparams;
                //first message from client using flag id stub - sending the client id, round info and criteria to the server
//                Log.v("Step!! " + step , msgtoserver);
                flag firsthandshake = flag.newBuilder().setFlagId(msgtoserver).build();
//                Log.v("Step!! " + step , msgtoserver);

                long start = System.currentTimeMillis();
                flag replyfromserver = stub.communicatedText(firsthandshake);
                long end = System.currentTimeMillis();
                //finding the time difference and converting it into seconds
                float sec = (end - start) / 1000F;
                System.out.println(sec + " seconds");
                String logvalue = "First" + "\n" + sec + " seconds";
                generateNote(logvalue+'\n');
//                Log.v("Step!! " + step , msgtoserver);
                setMessage(activity,"Step " + step + "-> client msgs server (1) : "+ msgtoserver);
                Log.v("Step " + step , "client msgs server (1) : "+ msgtoserver);
                step++;

                //get the reply from the server through the same flag id
                char confirmation = replyfromserver.getFlagId().charAt(0);
//                char confirmation = '1';

                //depending on the reply from the server. Either begin the training or break from loop.
                if (confirmation=='0') {
                    setMessage(activity,"Step"+ step +" -> server msgs client (1) : "+ confirmation + "DO NOT TRAIN");
                    Log.v("Step" + step , "server msgs client (1) : "+ confirmation + "DO NOT TRAIN");
                    step++;
                    return "NOT SELECTED";

                } else if (confirmation=='1'){
//                    Getting Time Information from the server

//                    x='1:$no_of_epochs'+str(e)+'$mode'+str(mode)+'$Selected for training!!'
//                    nof_epochs=x[x.index('no_of_epochs')+len('no_of_epochs'):x.index('$',x.index('no_of_epochs'))]
//                    mode=x[x.index('mode')+len('mode'):x.index('$',x.index('mode'))]

                    String strText = replyfromserver.getFlagId();
                    String delims = ":";
                    String[] stringarray = strText.split(delims);
                    System.out.println(stringarray[1]);
                    int no_of_epochs=Integer.parseInt(strText.substring(strText.indexOf("no_of_epochs")+"no_of_epochs".length(),strText.indexOf("$",strText.indexOf("no_of_epochs"))));
                    String mode=strText.substring(strText.indexOf("mode")+"mode".length(),strText.indexOf("$",strText.indexOf("mode")));
//                    StringTokenizer stObj = new StringTokenizer(strText, delims);
//                    String temp = null;
//                    while (stObj.hasMoreElements()) {
//                        temp = (String) stObj.nextElement();
//                    }

//                    System.out.println("training_time before: "+System.currentTimeMillis());
//                    training_time=System.currentTimeMillis()+Float.valueOf(temp)*60*1000;
//                    System.out.println("training_time after: "+training_time+": "+(System.currentTimeMillis()-training_time)/(60*1000));

                    setMessage(activity,"Step "+ step +" -> server msgs client (1) : "+ confirmation + "START TRAIN");
                    Log.v("Step 5", "server msgs client (1) : "+ confirmation + "START TRAIN");
                    step++;
                    try {

                        //before starting training send a message to server that confirmation has been received
//                        msgtoserver = "ID:" + client_ID + "FLAG:" + 4;
//                        setMessage(activity,"Step "+ step +" -> client msgs server just before training (2) : "+ msgtoserver);
//                        Log.v("Step "+ step, "client msgs server just before training (2) : "+ msgtoserver);
//                        step++;

                        //get the globalweights from the server and put into the checkpoint with "ckptname" in internal storage of app
                        start=System.currentTimeMillis();
                        msgtoserver = "ID:" + client_ID + "Requesting Current Global Weights";
                        Weight globalweights = stub.getGlobalWeights(flag.newBuilder().setFlagId(msgtoserver).build());
                        end = System.currentTimeMillis();
                        //finding the time difference and converting it into seconds
                        sec = (end - start) / 1000F;
                        System.out.println(sec + " seconds");
                        logvalue = "global weights" + "\n" + sec + " seconds";
                        //SpeechToTextMainActivity.setMessage(logvalue, "");
                        generateNote(logvalue+'\n');
                        ckptname = "FL_weights_" + (Integer.parseInt(round)) + ".ckpt";
                        fl.sendreceiveweights.saveweightstockpt.saveweights(activity,(path + "/" + ckptname) ,tflitename, round, globalweights);
                        fl.sendreceiveweights.saveweightstockpt.saveweights(activity,(path + "/" + "min_checkpoint.ckpt") ,tflitename, round, globalweights);
//                        avg_criteria_values = Train.generateInference(ckptname,activity,tflitename);


                        globalweights=null;
                        System.gc();

                        //begin training
                        //inputs are activity and round number(to load the correct ckpt)
                        setMessage(activity,"Step " + step + " -> pre-processing of train samples : " + trainingsamples);
                        Log.v("Step " + step , "pre-processing train samples : " + trainingsamples);
                        step++;
                        getandsavemelspect.trainsamples_melspect(activity, trainingsamples,trainingsamples_offset);


                        setMessage(activity,"Step "+step+" -> training started for round : "+ round);
                        Log.v("Step "+step, "training started for round : "+ round);
                        step++;
                        phone_parameters = dinstance.startSystemParamCollectionInstance(activity,-2,-2);


                        msgtoserver = "ID:" + client_ID + "FLAG:" + 1;
                        flag before_training = flag.newBuilder().setFlagId(msgtoserver).build();
                        flag msgfromserver_beforetraining = stub.communicatedText(before_training);

                        String phone_parameters_before_training = dinstance.startSystemParamCollectionInstance(activity,-1,-1);
                        start=System.currentTimeMillis();
                        int dataSize = 1024 * 1024;
                        System.out.println("Before training");
                        System.out.println("Used Memory   : " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/dataSize + " MB");
                        System.out.println("Free Memory   : " + Runtime.getRuntime().freeMemory()/dataSize + " MB");
                        System.out.println("Total Memory  : " + Runtime.getRuntime().totalMemory()/dataSize + " MB");
                        System.out.println("Max Memory    : " + Runtime.getRuntime().maxMemory()/dataSize + " MB");
                        Float wer_value= Train.trainthemodel(activityReference.get(),round, tflitename,training_time,mode,no_of_epochs);
                        System.out.println("After training");
                        System.out.println("Used Memory   : " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/dataSize + " MB");
                        System.out.println("Free Memory   : " + Runtime.getRuntime().freeMemory()/dataSize + " MB");
                        System.out.println("Total Memory  : " + Runtime.getRuntime().totalMemory()/dataSize + " MB");
                        System.out.println("Max Memory    : " + Runtime.getRuntime().maxMemory()/dataSize + " MB");
                        end = System.currentTimeMillis();
                        String phone_parameters_after_training = dinstance.startSystemParamCollectionInstance(activity,1000,1000);
                        Float training_time_per_batch= Float.valueOf(((end-start)/1000F)/(no_of_epochs*5));
                        //                -1, -1, 8.0, 3.7524032592773438, 79, false, 0.029095994800759497
                        Float drop_per_batch=((Float.valueOf(phone_parameters_before_training.split(", ")[4])-Float.valueOf(phone_parameters_after_training.split(", ")[4]))/(no_of_epochs*5));

//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_c2_d2");
//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_c3_d2");
//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_b1_d2");0.99
//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_b2_d2");0.66
//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_b3_d2");0.45
//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_b4_d2");0.24
//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_d1_d2");
//                        Train.trainthemodel(activityReference.get(),round, tflitename,training_time,"train_d2");


                        //After training successfully, send another msg to server
                        msgtoserver = "ID:" + client_ID + "FLAG:" + "2";
                        flag msgaftertraining = flag.newBuilder().setFlagId(msgtoserver).build();
                        start=System.currentTimeMillis();
                        flag msgfromserver_aftertraining = stub.communicatedText(msgaftertraining);
                        end = System.currentTimeMillis();
                        //finding the time difference and converting it into seconds
                        sec = (end - start) / 1000F;
                        System.out.println(sec + " seconds");
                        logvalue = "after training message" + "\n" + sec + " seconds";
                        generateNote(logvalue+'\n');
                        setMessage(activity,"Step"+step+" -> server msgs client after training (3): "+ msgfromserver_aftertraining.getFlagId());
                        Log.v("Step"+step, "server msgs client after training (3): "+ msgfromserver_aftertraining.getFlagId());
                        step++;


                        //Get the weights from the tflite
//                        ckptname = "FL_weights_" + round + ".ckpt";
                        ckptname = "FL_weights_" + round + ".ckpt";   //"min_checkpoint.ckpt";
                        ArrayList<InnerType> modelParameters = getweightsfromtflite.getweights(activity,(path + "/" + ckptname),tflitename);
                        System.out.println(" Getting weights from tflite done 1");
                        //do inference here on min_checkpoint
//                        'ID:'+ str(ID) +'$CRITERIA:'+str(wer_2)  +'$neural_ucb_criteria:'+neural_ucb_criteria
                        Weight sendweightsfromphone = Weight.newBuilder().addAllArrOfArrs(modelParameters).setFlagId(("ID:"+client_ID+"$CRITERIA:"+wer_value+"$")).build();
//                        neural_ucb_criteria:"+training_time_per_batch+","+drop_per_batch
                        setMessage(activity,"Step"+step+" -> client msgs server after training (4): "+ msgtoserver);
                        Log.v("Step"+step, "client msgs server weights after training (4): "+ msgtoserver);
                        step++;
                        System.out.println(" Getting weights from tflite done 2");

                        start=System.currentTimeMillis();
                        System.out.println("Before sending weights to server");
                        System.out.println("Used Memory   : " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/dataSize + " MB");
                        System.out.println("Free Memory   : " + Runtime.getRuntime().freeMemory()/dataSize + " MB");
                        System.out.println("Total Memory  : " + Runtime.getRuntime().totalMemory()/dataSize + " MB");
                        System.out.println("Max Memory    : " + Runtime.getRuntime().maxMemory()/dataSize + " MB");
                        Weight fedavgweight = stub.getFlWeights(sendweightsfromphone);
                        sendweightsfromphone=null;
                        for(Object obj : modelParameters){
                            obj = null;
                        }
                        modelParameters=null;
                        System.gc();
                        System.out.println("After sending weights to server");
                        System.out.println("Used Memory   : " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/dataSize + " MB");
                        System.out.println("Free Memory   : " + Runtime.getRuntime().freeMemory()/dataSize + " MB");
                        System.out.println("Total Memory  : " + Runtime.getRuntime().totalMemory()/dataSize + " MB");
                        System.out.println("Max Memory    : " + Runtime.getRuntime().maxMemory()/dataSize + " MB");
                        end = System.currentTimeMillis();
                        //finding the time difference and converting it into seconds
                        sec = (end - start) / 1000F;
                        System.out.println(sec + " seconds time for getflweights");
                        logvalue = "sending and recieving fl weights" + "\n" + sec + " seconds";
                        //SpeechToTextMainActivity.setMessage(logvalue, "");
                        generateNote(logvalue+'\n');
                        setMessage(activity,"Step "+step+" -> server msgs client with fed avg weights (5)");
                        Log.v("Step "+step, "server msgs client with fed avg weights (5)");
                        step++;



                        //save the fed averaged weights received from the server to the ckpt
                        ckptname = "FL_weights_" + (Integer.parseInt(round)+1) + ".ckpt";
                        saveweightstockpt.saveweights(activity,(path + "/" + ckptname) ,tflitename, round, fedavgweight);
                        saveweightstockpt.saveweights(activity,(path + "/" + "min_checkpoint.ckpt") ,tflitename, round, fedavgweight);
//                        Train.generateInference(ckptname,activity,tflitename);
                        setMessage(activity,"Step "+step+" -> save the fed avg weights to the ckpt in app specific stprage : " + ckptname);
                        Log.v("Step "+step, "save the fed avg weights to the ckpt in app specific stprage :" + ckptname);
                        step++;
//
//                        //update the round information and the training samples offset for next round
                        updateroundinfo(((Integer.parseInt(round)+1)+","+(trainingsamples_offset+trainingsamples)));
                        setMessage(activity,"Step"+step+" -> update the round and offset in RoundInfo.txt : " + (Integer.parseInt(round)+1));
                        Log.v("Step "+step, "update the round and offset in RoundInfo.txt : " + (Integer.parseInt(round)+1));
                        step++;
//
//
//                        'ID:'+ str(ID) +'$CRITERIA:'+str(wer_2)  +'$neural_ucb_criteria:'+neural_ucb_criteria
                        //                        neural_ucb_criteria:"+
                        msgtoserver = "ID:" + client_ID + "FLAG:" + 3 + "CRITERIA:" + String.valueOf(training_time_per_batch)+","+ String.valueOf(drop_per_batch);
                        setMessage(activity,"Step"+step+" -> client msgs server after recieving weights (3): "+ msgtoserver);
                        Log.v("Step"+step, "client msgs after recieving weights (3): "+ msgtoserver);
                        step++;

                        flag msgafterrecieingweights = flag.newBuilder().setFlagId(msgtoserver).build();
                        start=System.currentTimeMillis();
                        flag msgfromserver_afterreceivingweights = stub.communicatedText(msgafterrecieingweights);
                        end = System.currentTimeMillis();
                        //finding the time difference and converting it into seconds
                        sec = (end - start) / 1000F;
                        System.out.println(sec + " seconds");
                        logvalue = "after receciving weights message" + "\n" + sec + " seconds";
                        //SpeechToTextMainActivity.setMessage(logvalue, "");
                        generateNote(logvalue+'\n');

                        setMessage(activity,"Step"+step+" -> server msgs client recieving weights  (3): "+ msgfromserver_afterreceivingweights.getFlagId());
                        Log.v("Step"+step, "client msgs server recieving weights  (3): "+ msgfromserver_afterreceivingweights.getFlagId());
                        step++;
                        setMessage(activity,"Step"+step+" -> completion of one round of FL");
                        Log.v("Step"+step, "completion of one round of FL");


                        long total_fl_time_end = System.currentTimeMillis();
                        //finding the time difference and converting it into seconds
                        sec = -(total_fl_time_start - total_fl_time_end) / 1000F;
                        System.out.println(sec + " seconds");
                        logvalue = "total_fl_time" + "\n" + sec + " seconds";
                        generateNote(logvalue+'\n');
                        return "DONE";
                    } catch ( Exception error){
                        Log.e("Error", String.valueOf(error));
                        setMessage(activity,"Error between Step 4 to 7");
                        return String.valueOf(error);

                    }
                } else {
                    System.out.println("ERROR!");
                    setMessage(activity,"Step"+ step +" -> server msgs client (1) : "+ confirmation + "error");
                    Log.v("Step" + step , "server msgs client (1) : "+ confirmation + "error");
                    step++;
                    return "ERROR";
                }

//                return "ERROR2";
            } catch (Exception e) {
                System.out.println(e);
                return String.format("Failed... : %n%s", e);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            TextView messagefromserver = (TextView) activity.findViewById(R.id.responsefromserver);
            messagefromserver.setText(result);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static String getroundinfo (Context context) throws IOException {
        FileInputStream fis = context.openFileInput("RoundInfo.txt");
        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader bufferedReader = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();
        String line;
        //char round;
        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line);
        }
        String round= String.valueOf(sb);
        bufferedReader.close();
        return round;
    }

    public static void updateroundinfo(String sBody) {
        try {
            File gpxfile = new File(path, "RoundInfo.txt");
            FileWriter writer = new FileWriter(gpxfile, false);
            writer.write(sBody);

            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}