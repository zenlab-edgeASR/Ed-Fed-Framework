package fl.sendreceiveweights;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Environment;
import android.util.Log;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import org.apache.commons.lang3.ArrayUtils;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fl.android_client.InnerType;
import fl.android_client.Weight;


public class saveweightstockpt {


    public static MappedByteBuffer loadModelFile(Activity activity, String tflitename) throws IOException {
        System.out.println("LOADING TFLITE "+ tflitename);
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(tflitename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    public static int saveweights(Activity activity, String ckptpath, String tflitename, String round, Weight fedavgweights) throws IOException {

        //load the tflite interpreter
        Interpreter interpreter = new Interpreter(loadModelFile(activity, tflitename));
        //get parameter shape of each layer of the tflite
        //read the ckpt in the internal directory of the app and you have to give old ckptname
        File saveoutputFile = new File(ckptpath);
        Map<String, Object> input = new HashMap<>();
        input.put("checkpoint_path", saveoutputFile.getAbsolutePath());
        Map<String, Object> output_to_getnames_sizes = new HashMap<>();
        String[] nodenames = new String[46];
        int[] parametersizes = new int[46];
        output_to_getnames_sizes.put("shape",parametersizes);
        output_to_getnames_sizes.put("name",nodenames);
        interpreter.runSignature(input,output_to_getnames_sizes, "get_nodenames_sizes");

        //get parameter shape of each layer of the tflite
        Map<String, Object> input_saveweights = new HashMap<>();
        List<InnerType> fedavgweights_layerweights = fedavgweights.getArrOfArrsList();

        for (int i=0;i< 46;i++){
            System.out.println(i);
            System.gc();
            String key = "weights_1D_" + i;
            List<Float> layerweights_list = fedavgweights_layerweights.get(i).getArrList();
            float[] layerweights_float = new float[parametersizes[i]];
            int k = 0;
            for (Float f : layerweights_list) {
                layerweights_float[k++] = (f != null ? f : Float.NaN); // Or whatever default you want.
            }
            input_saveweights.put(key,layerweights_float);
        }

        File saveoutputFile_saveweights = new File(ckptpath);
        input_saveweights.put("checkpoint_path", saveoutputFile_saveweights.getAbsolutePath());
        Map<String, Object> output_to_get_weights = new HashMap<>();
        interpreter.runSignature(input_saveweights, output_to_get_weights, "set_weights");

        return 0;
    }

}
