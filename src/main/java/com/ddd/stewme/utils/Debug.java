// Debug.java
package com.ddd.stewme.utils;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class Debug {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void log(String message) {
        LOGGER.info("[StewMe] {}", message);
    }

    public static void logCauldronData(String operation, String data) {
        LOGGER.info("[StewMe-Cauldron] {}: {}", operation, data);
    }

    public static void logBowlData(String operation, String data) {
        LOGGER.info("[StewMe-Bowl] {}: {}", operation, data);
    }
}