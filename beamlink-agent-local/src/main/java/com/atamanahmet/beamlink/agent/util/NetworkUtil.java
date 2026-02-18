package com.atamanahmet.beamlink.agent.util;

import java.net.InetAddress;

public class NetworkUtil {

    /**
     * Get local IP address
     */
    public static String getLocalIp() {
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            return inetAddress.getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}