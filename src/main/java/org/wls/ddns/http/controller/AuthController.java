package org.wls.ddns.http.controller;

import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wls.ddns.http.ProxyConfig;
import org.wls.ddns.http.lib.annotation.PathParam;
import org.wls.ddns.http.lib.annotation.RequestParam;
import org.wls.ddns.http.lib.annotation.RouterMapping;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by shirukai on 2018/9/30
 * controller
 */
public class AuthController extends BaseController{
    private static final Logger LOG = LogManager.getLogger(AuthController.class);

    private static Map<String, String> authKeyMap = new ConcurrentHashMap<>();

    public AuthController(ProxyConfig proxyConfig, ChannelHandlerContext channelHandlerContext){
        super(proxyConfig, channelHandlerContext);

    }


    @RouterMapping(api = "/auth/gen/{name}", method = "GET")
    public String genNameAuthKeys(@PathParam("name") String name)
    {
//        InetSocketAddress insocket = (InetSocketAddress) channelHandlerContext.channel()
//                .remoteAddress();

        UUID uuid = UUID.randomUUID();
        authKeyMap.put(uuid.toString(), name);
        return "<ip:port>:" + "/auth/validate/" + uuid.toString();
    }

    /**
     * 测试POST请求
     *
     */
    @RouterMapping(api = "/auth/validate/{uuid}", method = "GET")
    public String validate(@PathParam("uuid") String uuid) {
        if(authKeyMap.get(uuid) != null){
            String name = authKeyMap.get(uuid);
            authKeyMap.remove(uuid);


            InetSocketAddress insocket = (InetSocketAddress) channelHandlerContext.channel()
                    .remoteAddress();
            LOG.error(insocket.getAddress());
            proxyConfig.addTrustIps(name, insocket.getAddress().toString().substring(1));

            return "OK";
        }

        return "Not Valid";
    }
}
