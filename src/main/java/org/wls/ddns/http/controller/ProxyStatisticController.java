package org.wls.ddns.http.controller;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import org.wls.ddns.http.ProxyConfig;
import org.wls.ddns.http.lib.annotation.JsonParam;
import org.wls.ddns.http.lib.annotation.PathParam;
import org.wls.ddns.http.lib.annotation.RequestParam;
import org.wls.ddns.http.lib.annotation.RouterMapping;
import org.wls.ddns.model.ConnModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by shirukai on 2018/9/30
 * controller
 */
public class ProxyStatisticController extends BaseController {
//    ProxyConfig proxyConfig;

    public ProxyStatisticController(ProxyConfig proxyConfig,  ChannelHandlerContext channelHandlerContext){
        super(proxyConfig, channelHandlerContext);
    }
    /**
     * 测试GET请求
     *
     * @return list
     */
    @RouterMapping(api = "/statistic/list", method = "GET")
    public List<Map<String, String>> statisticList() {
        return this.proxyConfig.getAllConnectionInfo();
    }

//    /**
//     * 测试POST请求
//     *
//     * @param json json
//     * @return json
//     */
//    @RouterMapping(api = "/api/v1/test/post", method = "POST")
//    public JSONObject testPost(
//            @JsonParam("json") JSONObject json
//    ) {
//        return json;
//    }
}
