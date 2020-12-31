package com.example.aliayubkhan.LSLReceiver.util;

import java.util.List;

public class ListToPrimitiveArray {

    public static float[] toFloatArray(List<Float> floats) {
        int len = floats.size();
        float[] primitiveArray = new float[len];
        for (int i = 0; i < len; i++) {
            primitiveArray[i] = floats.get(i);
        }
        return primitiveArray;
    }

    public static double[] toDoubleArray(List<Double> doubles) {
        int len = doubles.size();
        double[] primitiveArray = new double[len];
        for (int i = 0; i < len; i++) {
            primitiveArray[i] = doubles.get(i);
        }
        return primitiveArray;
    }

    public static int[] toIntArray(List<Integer> integers) {
        int len = integers.size();
        int[] primitiveArray = new int[len];
        for (int i = 0; i < len; i++) {
            primitiveArray[i] = integers.get(i);
        }
        return primitiveArray;
    }

    public static short[] toShortArray(List<Short> shorts) {
        int len = shorts.size();
        short[] primitiveArray = new short[len];
        for (int i = 0; i < len; i++) {
            primitiveArray[i] = shorts.get(i);
        }
        return primitiveArray;
    }

    public static byte[] toByteArray(List<Byte> bytes) {
        int len = bytes.size();
        byte[] primitiveArray = new byte[len];
        for (int i = 0; i < len; i++) {
            primitiveArray[i] = bytes.get(i);
        }
        return primitiveArray;
    }
}
