package fl.trainingmodule.bert.featureextraction;
/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import androidx.annotation.WorkerThread;
import android.util.Log;

import com.google.common.base.Joiner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.InterpreterApi;

import timber.log.Timber;


/** Interface to load TfLite model and provide predictions. */
public class calltokenizer {
    private static final String TAG = "OnDevice";
    private static final String MODEL_PATH = "mobilebert_working.tflite";

    private static final int MAX_ANS_LEN = 32;
    private static final int MAX_QUERY_LEN = 64;
    private static final int MAX_SEQ_LEN = 128;
    private static final boolean DO_LOWER_CASE = true;
    private static final int PREDICT_ANS_NUM = 5;
    private static final int NUM_LITE_THREADS = 4;

    // Need to shift 1 for outputs ([CLS]).
    private static final int OUTPUT_OFFSET = 1;

    private static Interpreter mobilebert_tflite;
    private static Map<String, Integer> dic;

    private static final Joiner SPACE_JOINER = Joiner.on(" ");


    @WorkerThread
    public synchronized void unload() {
        mobilebert_tflite.close();
        //dic.clear();
    }

    /**
     * Input: Original content and query for the QA task. Later converted to Feature by
     * FeatureConverter. Output: A String[] array of answers and a float[] array of corresponding
     * logits.
     */
    @WorkerThread
    public static synchronized float predict(Interpreter tflite, Map<String, Integer> dictionary, String truth, String hypothesis) {

        mobilebert_tflite = tflite;
        dic = dictionary;
        //Log.v(TAG, "TFLite model: " + MODEL_PATH + " running...");
        //Log.v(TAG, "Convert Feature...");
//        Log.v("Loaded dictionary is : ", String.valueOf(dic));
        //Log.v("Hypothesis is : " , hypothesis);
        //Log.v("Ground truth is : " , truth);
        FeatureConverter featureConverter = new FeatureConverter();
        Feature feature = featureConverter.convert(dic, DO_LOWER_CASE, truth, hypothesis);

        //Log.v(TAG, "Set inputs...");
        int[][] inputIds = new int[1][MAX_SEQ_LEN];
        int[][] inputMask = new int[1][MAX_SEQ_LEN];
        int[][] segmentIds = new int[1][MAX_SEQ_LEN];
        float[][] outputlogits = new float[1][2];

        for (int j = 0; j < MAX_SEQ_LEN; j++) {
            inputIds[0][j] = feature.inputIds[j];
            inputMask[0][j] = feature.inputMask[j];
            segmentIds[0][j] = feature.segmentIds[j];
        }

//    Object[] inputs = {inputIds, inputMask, segmentIds};
        Map<Integer, Object> output = new HashMap<>();
        output.put(0, outputlogits);

        int imageTensorIndex = 0;

//        Log.v("InputTensor", String.valueOf(mobilebert_tflite.getInputTensor(0).name()));
//        int[] inputshape0= mobilebert_tflite.getInputTensor(0).shape();
//        Timber.d("InputTensor %s x %s ", inputshape0[0],inputshape0[1]);
//
//        Log.v("InputTensor", String.valueOf(mobilebert_tflite.getInputTensor(1).name()));
//        int[] inputshape1= mobilebert_tflite.getInputTensor(1).shape();
//        Timber.d("InputTensor %s x %s ", inputshape1[0],inputshape1[1]);
//
//        Log.v("InputTensor", String.valueOf(mobilebert_tflite.getInputTensor(2).name()));
//        int[] inputshape2= mobilebert_tflite.getInputTensor(2).shape();
//        Timber.d("InputTensor %s x %s ", inputshape2[0],inputshape2[1]);
//
//        int[] outputshape = mobilebert_tflite.getOutputTensor(0).shape();
//        Timber.d("InputTensor %s x %s ", outputshape[0],outputshape[1]);

        //Log.v(TAG, "Run inference...");
        mobilebert_tflite.runForMultipleInputsOutputs(new Object[] { inputMask,inputIds,segmentIds}, output);
        float prob1 = outputlogits[0][1];
        //Log.v("BERTvalue", String.valueOf(prob1));
        //Log.v(TAG, "Finish.");

        return prob1;
    }

    /** Find the Best N answers & logits from the logits array and input feature. */
    private static synchronized List<QaAnswer> getBestAnswers(
            float[] startLogits, float[] endLogits, Feature feature) {
        // Model uses the closed interval [start, end] for indices.
        int[] startIndexes = getBestIndex(startLogits, feature.tokenToOrigMap);
        int[] endIndexes = getBestIndex(endLogits, feature.tokenToOrigMap);

        List<QaAnswer.Pos> origResults = new ArrayList<>();
        for (int start : startIndexes) {
            for (int end : endIndexes) {
                if (end < start) {
                    continue;
                }
                int length = end - start + 1;
                if (length > MAX_ANS_LEN) {
                    continue;
                }
                origResults.add(new QaAnswer.Pos(start, end, startLogits[start] + endLogits[end]));
            }
        }

        Collections.sort(origResults);

        List<QaAnswer> answers = new ArrayList<>();
        for (int i = 0; i < origResults.size(); i++) {
            if (i >= PREDICT_ANS_NUM) {
                break;
            }

            String convertedText;
            if (origResults.get(i).start > 0) {
                convertedText = convertBack(feature, origResults.get(i).start, origResults.get(i).end);
            } else {
                convertedText = "";
            }
            QaAnswer ans = new QaAnswer(convertedText, origResults.get(i));
            answers.add(ans);
        }
        return answers;
    }

    /** Get the n-best logits from a list of all the logits. */
    @WorkerThread
    private static synchronized int[] getBestIndex(float[] logits, Map<Integer, Integer> tokenToOrigMap) {
        List<QaAnswer.Pos> tmpList = new ArrayList<>();
        for (int i = 0; i < MAX_SEQ_LEN; i++) {
            if (tokenToOrigMap.containsKey(i + OUTPUT_OFFSET)) {
                tmpList.add(new QaAnswer.Pos(i, i, logits[i]));
            }
        }

        Collections.sort(tmpList);

        int[] indexes = new int[PREDICT_ANS_NUM];
        for (int i = 0; i < PREDICT_ANS_NUM; i++) {
            indexes[i] = tmpList.get(i).start;
        }

        return indexes;
    }

    /** Convert the answer back to original text form. */
    @WorkerThread
    private static String convertBack(Feature feature, int start, int end) {
        // Shifted index is: index of logits + offset.
        int shiftedStart = start + OUTPUT_OFFSET;
        int shiftedEnd = end + OUTPUT_OFFSET;
        int startIndex = feature.tokenToOrigMap.get(shiftedStart);
        int endIndex = feature.tokenToOrigMap.get(shiftedEnd);
        // end + 1 for the closed interval.
        String ans = SPACE_JOINER.join(feature.origTokens.subList(startIndex, endIndex + 1));
        return ans;
    }
}
