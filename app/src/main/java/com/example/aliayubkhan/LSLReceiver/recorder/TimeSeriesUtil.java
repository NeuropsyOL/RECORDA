package com.example.aliayubkhan.LSLReceiver.recorder;

import android.util.Log;

import java.util.Arrays;

class TimeSeriesUtil {

    static float[] appendZeros(float[] sample, double[] timestamps){
        float[] data1 = new float[1];

        int dif = timestamps.length - sample.length;

        data1 = Arrays.copyOf(sample, sample.length + dif);

        for (int i=sample.length; i<timestamps.length; i++){
            data1[i] = 0;
        }

        return data1;
    }

    // removing leading zeros
    static int[] removeZerosInt(int[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        int[] b = new int[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static float[] removeZerosFloat(float[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        float[] b = new float[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static double[] removeZerosDouble(double[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        double[] b = new double[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static short[] removeZerosShort(short[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        short[] b = new short[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static byte[] removeZerosByte(byte[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        byte[] b = new byte[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }
}
