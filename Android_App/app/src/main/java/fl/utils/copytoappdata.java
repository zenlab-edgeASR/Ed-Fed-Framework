package fl.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class copytoappdata {

    public static String path = "/data/data/fl.android_client/files";

    @SuppressLint("LongLogTag")
    public static void copyfile(Activity activity, String filename) throws IOException {

        AssetManager assetManager = activity.getAssets();

        InputStream in;
        OutputStream out;
        try {
            in = assetManager.open(filename);
            String newFileName = path + "/" + filename;
            out = new FileOutputStream(newFileName);

            byte[] buffer = new byte[120983552];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
            Log.v("COPIED FILES TO INTERNAL STORAGE",filename);
        } catch (Exception e) {
            Log.e("COPY FILES TO INTERNAL STORAGE FAILED: ", e.getMessage());
        }
    }

//    public static void copygroundtruth(Activity activity, String filename) {
//        AssetManager assetManager = activity.getAssets();
//
//        InputStream in;
//        OutputStream out;
//        try {
//            in = assetManager.open(filename);
//            String newFileName = path + "/" + filename;
//            out = new FileOutputStream(newFileName);
//
//            byte[] buffer = new byte[7848];
//            int read;
//            while ((read = in.read(buffer)) != -1) {
//                out.write(buffer, 0, read);
//            }
//            in.close();
//            out.flush();
//            out.close();
//            Log.v("COPY GROUNDTRUTH ", "SUCCESS");
//        } catch (Exception e) {
//            Log.e("COPY GROUNDTRUTH FAILED", e.getMessage());
//        }
//    }

//    public static void copyondevicetraincsv(Activity activity, String filename) {
//        AssetManager assetManager = activity.getAssets();
//
//        InputStream in;
//        OutputStream out;
//        try {
//            in = assetManager.open(filename);
//            String newFileName = path + "/" + filename;
//            out = new FileOutputStream(newFileName);
//
//            byte[] buffer = new byte[8145];
//            int read;
//            while ((read = in.read(buffer)) != -1) {
//                out.write(buffer, 0, read);
//            }
//            in.close();
//            out.flush();
//            out.close();
//            Log.v("COPY ODT CSV", "SUCCESS");
//        } catch (Exception e) {
//            Log.e("COPY FILES FAILED: ", e.getMessage());
//        }
//    }


}
