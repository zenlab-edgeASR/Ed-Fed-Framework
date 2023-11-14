package fl.PhoneDataCollection;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.Context.BATTERY_SERVICE;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
//import java.util.Map;
import java.util.Random;


/**
 * @Author: vinayrao
 * @Date: 2022-09-24 21:36
 * @Version 1.0
 */
public class DataCollectionService {

    public static String path = "/data/data/fl.android_client/files";
    private final Random mGenerator = new Random();
    private boolean inProgress;
    private Thread thread;
    private ActivityManager activityManager;
    private BatteryManager batteryManager;
    public int[] pidArray ;
    private ActivityManager.MemoryInfo memoryInfo;
    private FileOutputStream fileOutputStream = null;



    /** method for clients */
//    @RequiresApi(api = Build.VERSION_CODES.M)
//    public int startSystemParamCollection(Activity activity) {
////        fileOutputStream = savePublicly();
////        if (fileOutputStream != null) {
//            processReadingSystemParams(activity);
////            return mGenerator.nextInt(100);
////        }
//        return -1;
//
//    }

    /** method for clients
     * @return*/
    @RequiresApi(api = Build.VERSION_CODES.M)
    public String startSystemParamCollectionInstance(Activity activity, int epoch, int batch) {
        FileOutputStream fOutputStream = savePublicly(activity);
        String info = "Error";
        if (fOutputStream != null) {
            info = processReadingSystemParamsInstance(fOutputStream, activity, epoch, batch);
//            return mGenerator.nextInt(100);
        }
        return info;
    }

    public void stopSystemParamCollection() {
        thread.interrupt();
        try {
            if (fileOutputStream != null) {
                fileOutputStream.close();
                fileOutputStream = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @RequiresApi(api = Build.VERSION_CODES.M)
//    private void processReadingSystemParams(Activity activity) {
//        StringBuffer buffer = new StringBuffer();
//
//            activityManager = (ActivityManager) activity.getSystemService(ACTIVITY_SERVICE);
//            batteryManager = (BatteryManager) activity.getSystemService(BATTERY_SERVICE);
//
//        if (pidArray == null || pidArray.length == 0 ) {
//            getProcessList(activity);
//        }
//
//        memoryInfo = new ActivityManager.MemoryInfo();
//        activityManager.getMemoryInfo(memoryInfo);
//
//        Log.d("SysParams", "Total Device Memory");
//        buffer.append(Math.ceil((memoryInfo.totalMem * 1.0) / (1024 * 1024 * 1024))).append(", ");
//
//        while (inProgress && thread.isAlive()) {
//            processReadingMemory(buffer);
//            processBatteryInfo(buffer);
//            getCPUUsage(buffer);
//            writeToFile(fileOutputStream, buffer);
//        }
//        try {
//            writeToFile("completedata.txt",buffer);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        System.out.println(buffer);
//    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private String processReadingSystemParamsInstance(FileOutputStream fOutputStream,Activity activity,int epoch, int batch) {
        activityManager = (ActivityManager) activity.getSystemService(ACTIVITY_SERVICE);
        batteryManager = (BatteryManager) activity.getSystemService(BATTERY_SERVICE);


//            getProcessList(activity);
            try {
                List<ActivityManager.RunningAppProcessInfo> pids = activityManager.getRunningAppProcesses();
                ArrayList<Integer> processList = new ArrayList<>();
                int i = 0;
                for (; i < pids.size(); i++) {
                    ActivityManager.RunningAppProcessInfo info = pids.get(i);
                    if (info.processName.equalsIgnoreCase(activity.getPackageName())) {
                        processList.add(info.pid);
                    }
                }

                pidArray = new int[processList.size()];
                i = 0;

                for (Integer pid : processList) {
                    pidArray[i++] = pid;
                }
            }catch (Exception e) {
                System.out.println("Error1");
                e.printStackTrace();
            }


        memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        StringBuffer buffer = new StringBuffer();
        String epoch_batch= String.valueOf(epoch) + ", " + String.valueOf(batch) + ", ";
        buffer.append(epoch_batch);
//        buffer.append(String.valueOf(epoch));
//        buffer.append(String.valueOf(batch));
        Log.d("SysParams", "Total Device Memory");


//        getCPUInfo(buffer);
//        try {
//            String[] DATA = {"/system/bin/cat", "/proc/cpuinfo"};
//            ProcessBuilder processBuilder = new ProcessBuilder(DATA);
//            Process process = processBuilder.start();
//            InputStream inputStream = process.getInputStream();
//            byte[] byteArray = new byte[2048];
//            StringBuilder output = new StringBuilder();
//            int readBytes;
//            do {
//                readBytes = inputStream.read(byteArray);
//                if (readBytes == -1) {
//                    break;
//                }
//                output.append(new String(byteArray).substring(0, readBytes));
//            } while (true);
//            inputStream.close();
//            buffer.append(output);
//        } catch (Exception e) {
//            System.out.println("Error2");
//            e.printStackTrace();
//        }



        //processReadingMemory(buffer);
        try {
//            Debug.MemoryInfo[] memInfoArray = activityManager.getProcessMemoryInfo(pidArray);
//            for (Debug.MemoryInfo memoryInfo : memInfoArray) {
//                Map<String, String> memoryStats = memoryInfo.getMemoryStats();
//                for (String key : memoryStats.keySet()) {
//                    buffer.append(key).append(" = ").append(memoryStats.get(key)).append(" kB\n");
//                }
//            }

            activityManager.getMemoryInfo(memoryInfo);
            Log.d("SysParams", "Free Device Memory");
            buffer.append(Math.ceil((memoryInfo.totalMem * 1.0) / (1024 * 1024 * 1024))).append(", ");
            buffer.append((memoryInfo.availMem * 1.0) / (1024 * 1024 * 1024)).append(", ");
        } catch (Exception e) {
            System.out.println("Error1=3");
            e.printStackTrace();
        }

//        processBatteryInfo(buffer);
        int batLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Log.d("SysParams", "Battery level, Battery Charging");
        buffer.append(batLevel).append(", ").append(batteryManager.isCharging()).append(", ");

//        getCPUUsage(buffer);`
        long clockSpeedHz = Os.sysconf(OsConstants._SC_CLK_TCK);
        double uptimeSec = SystemClock.elapsedRealtime() / 1000.0;
        long numCores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF);

        try {
            for (int value : pidArray) {
                String[] DATA = {"/system/bin/cat", "/proc/" + value + "/stat"};
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

                String[] cpuInfoArray = output.toString().split(" ");

                long uTime = Long.parseLong(cpuInfoArray[13]);
                long sTime = Long.parseLong(cpuInfoArray[14]);
                long cuTime = Long.parseLong(cpuInfoArray[15]);
                long csTime = Long.parseLong(cpuInfoArray[16]);
                long startTime = Long.parseLong(cpuInfoArray[21]);

                double cpuTimeSec = (uTime + sTime + cuTime + csTime) / (clockSpeedHz * 1.0);
                double processTimeSec = uptimeSec - ((startTime * 1.0) / clockSpeedHz);

                double avgUsagePercentAllCores = 100 * (cpuTimeSec / processTimeSec);

                double avgUsagePercent = avgUsagePercentAllCores / numCores;
                Log.d("SysParams", "Average CPU Usage");
                buffer.append(avgUsagePercent).append("\n");
            }

        } catch (Exception e) {
            System.out.println("Error4");
            e.printStackTrace();
        }

        System.out.println(buffer);
        generateNote(String.valueOf(buffer));
//        writeToFile(fOutputStream, buffer);

        try {
            if (fOutputStream != null) {
                fOutputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.valueOf(buffer);
    }


    private void writeToFile(FileOutputStream fOutputStream, StringBuffer s) {
        try {
            if (fOutputStream != null) {
                fOutputStream.write(s.toString().getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void processReadingMemory(StringBuffer buffer) {
        try {
            /*Debug.MemoryInfo[] memInfoArray = activityManager.getProcessMemoryInfo(pidArray);
            for (Debug.MemoryInfo memoryInfo : memInfoArray) {
                Map<String, String> memoryStats = memoryInfo.getMemoryStats();
                for (String key : memoryStats.keySet()) {
                    buffer.append(key).append(" = ").append(memoryStats.get(key)).append(" kB\n");
                }
            }*/

            activityManager.getMemoryInfo(memoryInfo);
            Log.d("SysParams", "Free Device Memory");
            buffer.append((memoryInfo.availMem * 1.0) / (1024 * 1024 * 1024)).append(", ");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void processBatteryInfo(StringBuffer buffer) {
        int batLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        Log.d("SysParams", "Battery level, Battery Charging");
        buffer.append(batLevel).append(", ").append(batteryManager.isCharging()).append(", ");

    }

    private void getProcessList(Activity activity) {
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
        }catch (Exception e) {
            e.printStackTrace();
        }
//        List<ActivityManager.RunningAppProcessInfo> pids = activityManager.getRunningAppProcesses();
//        ArrayList<Integer> processList = new ArrayList<>();
//        int i = 0;
//        for (; i < pids.size(); i++) {
//            ActivityManager.RunningAppProcessInfo info = pids.get(i);
//            if (info.processName.equalsIgnoreCase(getPackageName())) {
//                processList.add(info.pid);
//            }
//        }
//
//        pidArray = new int[processList.size()];
//        i = 0;
//
//        for (Integer pid : processList) {
//            pidArray[i++] = pid;
//        }
    }

    private void getCPUUsage(StringBuffer buffer) {
        long clockSpeedHz = Os.sysconf(OsConstants._SC_CLK_TCK);
        double uptimeSec = SystemClock.elapsedRealtime() / 1000.0;
        long numCores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF);

        try {
            for (int value : pidArray) {
                String[] DATA = {"/system/bin/cat", "/proc/" + value + "/stat"};
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

                String[] cpuInfoArray = output.toString().split(" ");

                long uTime = Long.parseLong(cpuInfoArray[13]);
                long sTime = Long.parseLong(cpuInfoArray[14]);
                long cuTime = Long.parseLong(cpuInfoArray[15]);
                long csTime = Long.parseLong(cpuInfoArray[16]);
                long startTime = Long.parseLong(cpuInfoArray[21]);

                double cpuTimeSec = (uTime + sTime + cuTime + csTime) / (clockSpeedHz * 1.0);
                double processTimeSec = uptimeSec - ((startTime * 1.0) / clockSpeedHz);

                double avgUsagePercentAllCores = 100 * (cpuTimeSec / processTimeSec);

                double avgUsagePercent = avgUsagePercentAllCores / numCores;
                Log.d("SysParams", "Average CPU Usage");
                buffer.append(avgUsagePercent).append("\n");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getCPUInfo(StringBuffer buffer) {
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
            buffer.append(output);
        } catch (Exception e) {
            e.printStackTrace();
        }
;

    }

    public FileOutputStream savePublicly(Activity activity) {
//        String fileName = DateFormat.format("yyyy-MM-dd_hh-mm-ss", new Date()).toString();
        File file = new File(activity.getExternalFilesDir("SystemParams"), "Complete_Data"+".txt");
        try {
            if (file.createNewFile()) {
                return new FileOutputStream(file);
            } else if (file.exists()) {
                return new FileOutputStream(file,true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
    public static void generateNote(String sBody) {

        try {
            File root = new File(path);
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, "PhoneData.txt");
            FileWriter writer = new FileWriter(gpxfile, true);
            writer.write(sBody);

            writer.flush();
            writer.close();
//             Toast.makeText(context, "Writting to TXT File Named TrainingLog", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
