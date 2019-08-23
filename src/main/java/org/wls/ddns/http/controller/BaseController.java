package org.wls.ddns.http.controller;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.wls.ddns.http.ProxyConfig;

/**
 * Created by wls on 2019/8/13.
 */
public class BaseController {
    protected ProxyConfig proxyConfig;
    protected  ChannelHandlerContext channelHandlerContext;
    protected  FullHttpRequest request;
    public BaseController(ProxyConfig proxyConfig, ChannelHandlerContext channelHandlerContext, FullHttpRequest request){
        this.proxyConfig = proxyConfig;
        this.channelHandlerContext = channelHandlerContext;
        this.request = request;
    }

}
