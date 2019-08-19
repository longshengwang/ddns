package org.wls.ddns.http;

import org.wls.ddns.http.ProxyConfig;
import org.wls.ddns.http.controller.AuthController;
import org.wls.ddns.http.controller.ProxyStatisticController;
import org.wls.ddns.http.lib.HttpServer;

/**
 * Created by wls on 2019/8/8.
 */
public class HttpServerManager implements Runnable {
    Integer port;
    HttpServer server;
    ProxyConfig proxyConfig;
    public HttpServerManager(Integer port, ProxyConfig p){
        this.port = port;
        this.proxyConfig = p;
    }

    @Override
    public void run() {
        this.server = new HttpServer(this.proxyConfig);
        server.builder()
                .setPort(port)
                .setControllers(ProxyStatisticController.class, AuthController.class)
                .create().start();
    }

    public void close(){
        this.server.stopServer();
    }



//    public static void main(String[] args) throws NoSuchMethodException, InterruptedException {
//        HttpServerManager manager = new HttpServerManager(9090);
//        new Thread(manager).start();
//        System.out.println("===== start over");
//        TimeUnit.SECONDS.sleep(10);
//        System.out.println("===== sleep over");
//        manager.server.stopServer();
//    }
}
