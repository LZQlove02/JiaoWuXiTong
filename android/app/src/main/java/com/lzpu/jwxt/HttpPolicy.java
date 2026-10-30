package com.lzpu.jwxt;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import okhttp3.HttpUrl;

/**
 * 网络访问策略。公开官网内容与教务认证使用各自独立的允许主机集合，
 * 所有请求强制 HTTPS，重定向逐跳校验，避免策略混用。
 */
public final class HttpPolicy {

    /** 公开官网内容。 */
    static final HttpPolicy PUBLIC = new HttpPolicy(
            Collections.singleton("www.lzpu.edu.cn"));

    /**
     * 教务认证与个人数据。登录后的业务请求与课表变更后台检查都走这套策略。
     */
    public static final HttpPolicy AUTH = new HttpPolicy(
            Collections.singleton("zfjw.lzzy.net"));

    private final Set<String> allowedHosts;

    private HttpPolicy(Set<String> allowedHosts) {
        this.allowedHosts = new HashSet<>(allowedHosts);
    }

    public void check(HttpUrl url) throws IOException {
        if (!"https".equals(url.scheme())) {
            throw new IOException("仅允许 HTTPS 请求");
        }
        if (url.port() != 443) {
            throw new IOException("仅允许标准 HTTPS 端口");
        }
        if (!allowedHosts.contains(url.host())) {
            throw new IOException("目标主机不在允许范围内");
        }
        if (!url.username().isEmpty() || !url.password().isEmpty()) {
            throw new IOException("URL 不允许携带凭据");
        }
    }

    HttpUrl resolveRedirect(HttpUrl current, String location) throws IOException {
        if (location == null || location.isEmpty()) {
            throw new IOException("重定向缺少 Location");
        }
        HttpUrl next = current.resolve(location);
        if (next == null) {
            throw new IOException("无法解析重定向目标");
        }
        check(next);
        return next;
    }
}
