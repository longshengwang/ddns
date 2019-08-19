package org.wls.ddns.http.controller;

import io.netty.channel.ChannelHandlerContext;
import org.wls.ddns.http.ProxyConfig;

/**
 * Created by wls on 2019/8/13.
 */
public class BaseController {
    protected ProxyConfig proxyConfig;
    protected  ChannelHandlerContext channelHandlerContext;
    public BaseController(ProxyConfig proxyConfig, ChannelHandlerContext channelHandlerContext){
        this.proxyConfig = proxyConfig;
        this.channelHandlerContext = channelHandlerContext;
    }

}
