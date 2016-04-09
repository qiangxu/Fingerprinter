package com.mazein.fingerprinter;

import java.util.HashMap;

/**
 * Created by Seif3 on 4/5/2016.
 */
public class AccessPointMacs
{
    public static String AP1_MAC = "00:25:84:03:5b:44";
    public static String AP2_MAC = "00:25:84:03:67:48";
    public static String AP3_MAC = "00:1f:9d:20:40:48";

    public static HashMap<String, Integer> keys = new HashMap<>();
    static {
        keys.put(AP1_MAC,0);
        keys.put(AP2_MAC,1);
        keys.put(AP3_MAC,2);
    }

}
