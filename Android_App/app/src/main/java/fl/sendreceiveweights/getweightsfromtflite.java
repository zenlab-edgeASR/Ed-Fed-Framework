package fl.sendreceiveweights;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fl.android_client.InnerType;


public class getweightsfromtflite {

    public static MappedByteBuffer loadModelFile(Activity activity, String tflitename) throws IOException {
        System.out.println("LOADING TFLITE ");
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(tflitename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    public static ArrayList<InnerType> getweights(Activity activity, String ckptpath, String tflitename) throws IOException {

        System.out.println("get the node names and size to load weights from tflite");

        //load the tflite interpreter
        Interpreter interpreter = new Interpreter(loadModelFile(activity,tflitename));
        File saveoutputFile = new File(ckptpath);
        System.out.println("test 1");

        Map<String, Object> reinputs = new HashMap<>();
        reinputs.put("checkpoint_path", saveoutputFile.getAbsolutePath());
        Map<String, Object> reoutputs = new HashMap<>();
        interpreter.runSignature(reinputs, reoutputs, "restore");
        System.out.println("test 2");

        //get parameter shape of each layer of the tflite
        Map<String, Object> input = new HashMap<>();
        input.put("checkpoint_path", saveoutputFile.getAbsolutePath());
        Map<String, Object> output_to_getnames_sizes = new HashMap<>();
        String[] nodenames = new String[46];
        int[] parametersizes = new int[46];
        output_to_getnames_sizes.put("shape",parametersizes);
        output_to_getnames_sizes.put("name",nodenames);
        interpreter.runSignature(input,output_to_getnames_sizes, "get_nodenames_sizes");
        System.out.println("test 3");


        //get parameter shape of each layer of the tflite
        File saveoutputFile_getweights = new File(ckptpath);
        Map<String, Object> input_getweights = new HashMap<>();
        input_getweights.put("checkpoint_path", saveoutputFile_getweights.getAbsolutePath());
        Map<String, Object> output_to_get_weights = new HashMap<>();
        for (int i=0;i< nodenames.length;i++){
            String nodename = nodenames[i];
            float[] shape = new float[parametersizes[i]];
            output_to_get_weights.put(nodename,shape);
        }
        interpreter.runSignature(input_getweights, output_to_get_weights, "get_1D_weights");
        System.out.println("test 4");


        //add the weights to a array list modelParameters
        ArrayList<InnerType> modelParameters = new ArrayList<>(46);


        for(int k=0;k<46;k++) { //our model has 46 layers
            String layername = nodenames[k];
            //Step 1 : we need to arrange the node information in ascending order from 0 to 46
            for (Map.Entry<String, Object> entry : output_to_get_weights.entrySet()) {
                //Step 1.b : find the desired key (outputnodename) in entryset
                String currentkey = entry.getKey();
                if (currentkey.equals(layername)) {
                    float[] weightarray = ((float[]) ((HashMap.Entry) entry).getValue());
                    ArrayList<Float> layerweights = new ArrayList<>(weightarray.length);

                    for (int i = 0; i < weightarray.length; i++) {
                        layerweights.add(weightarray[i]);
                        //System.out.println(String.valueOf(layerweights.get(i)));
                    }
                    InnerType layer = InnerType.newBuilder().addAllArr(layerweights).build();
                    modelParameters.add(layer);
                }
            }
        }
        System.out.println("test 5");

        return modelParameters;
    }

}
