package org.wls.ddns.http;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by wls on 2019/8/8.
 */
public class TrafficStatistic implements Runnable{

    private ProxyConfig proxyConfig;
    private Queue<String> queue = new ConcurrentLinkedQueue<>();

    public void offer(){

    }

    @Override
    public void run() {

    }
}
