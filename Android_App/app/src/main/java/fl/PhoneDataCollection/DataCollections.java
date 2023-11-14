package fl.PhoneDataCollection;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.BATTERY_SERVICE;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataCollections {
    public static boolean inProgress;
    public static HashMap<String,String> phone_params = new HashMap<>();

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static HashMap processReading(Activity activity) {

        ActivityManager activityManager = (ActivityManager) activity.getSystemService(ACTIVITY_SERVICE);
        BatteryManager bm = (BatteryManager) activity.getSystemService(BATTERY_SERVICE);
        int batLevel;

        List<ActivityManager.RunningAppProcessInfo> pids;
        try {
            System.out.println("Start data collection " + "inside try");
            pids = activityManager.getRunningAppProcesses();
            ArrayList<Integer> processList = new ArrayList<Integer>();
            int i = 0;
            for (; i < pids.size(); i++) {
                ActivityManager.RunningAppProcessInfo info = pids.get(i);
                if (info.processName.equalsIgnoreCase(activity.getPackageName())) {
                    processList.add(info.pid);
                }
            }
            int[] pidArray = new int[processList.size()];

            i = 0;

            for (Integer pid : processList) {
                pidArray[i++] = pid;
            }

            Debug.MemoryInfo[] memInfo = activityManager.getProcessMemoryInfo(pidArray);
            for (Debug.MemoryInfo memoryInfo : memInfo) {
                Map<String, String> memoryStats = memoryInfo.getMemoryStats();
                for (String key : memoryStats.keySet()) {
                    //Log.d("MemoryStats", key + " = " + memoryStats.get(key));
                    phone_params.put(key,memoryStats.get(key));
                }
            }

            batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            //Log.d("MemoryStats", "Current Battery Level : " + batLevel);
            phone_params.put("batterylevel", String.valueOf(batLevel));
            //Log.d("MemoryStats", "Current Battery Charging : " + bm.isCharging());
            phone_params.put("batterycharging", String.valueOf(bm.isCharging()));

            try {
                String[] DATA = {"/system/bin/cat", "/proc/cpuinfo"};
                ProcessBuilder processBuilder = new ProcessBuilder(DATA);
                Process process = processBuilder.start();
                InputStream inputStream = process.getInputStream();
                byte[] byteArray = new byte[2048];
                StringBuilder output = new StringBuilder();
                int readBytes;
                do {
                    readBytes = inputStream.read(byteArray);
                    if (readBytes == -1) {
                        break;
                    }
                    output.append(new String(byteArray).substring(0, readBytes));
                } while (true);
                inputStream.close();
                phone_params.put("cpuinfo", String.valueOf(output));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

//        phone_params.put("total_memory",Math.ceil((memoryInfo.totalMem * 1.0) / (1024 * 1024 * 1024))).append(" ,");

        return phone_params;
    }
}
