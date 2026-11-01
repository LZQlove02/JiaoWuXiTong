package com.lzqlove.jiaowu;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import okhttp3.HttpUrl;
import org.junit.Test;

public class HttpPolicyGithubTest {

    @Test
    public void allowsGithubApi() throws Exception {
        HttpPolicy.GITHUB.check(HttpUrl.parse("https://api.github.com/repos/a/b/releases/latest"));
    }

    @Test(expected = IOException.class)
    public void rejectsPlainHttp() throws Exception {
        HttpPolicy.GITHUB.check(HttpUrl.parse("http://api.github.com/repos/a/b/releases/latest"));
    }

    @Test(expected = IOException.class)
    public void rejectsNonStandardPort() throws Exception {
        HttpPolicy.GITHUB.check(HttpUrl.parse("https://api.github.com:8443/repos/a/b/releases/latest"));
    }

    @Test(expected = IOException.class)
    public void rejectsOtherHost() throws Exception {
        HttpPolicy.GITHUB.check(HttpUrl.parse("https://github.com/LZQlove02/lzpu-jwxt-releases"));
    }

    @Test(expected = IOException.class)
    public void rejectsCredentialsInUrl() throws Exception {
        HttpPolicy.GITHUB.check(HttpUrl.parse("https://user:pass@api.github.com/repos/a/b"));
    }

    @Test(expected = IOException.class)
    public void authPolicyRejectsGithub() throws Exception {
        HttpPolicy.AUTH.check(HttpUrl.parse("https://api.github.com/repos/a/b/releases/latest"));
    }

    @Test
    public void authPolicyAllowsZfjwAttachment() throws Exception {
        HttpPolicy.AUTH.check(HttpUrl.parse(
                "https://zfjw.lzzy.net/ftp/x.pdf?filename=%E9%99%84%E4%BB%B61.pdf"));
    }

    @Test(expected = IOException.class)
    public void authPolicyRejectsOtherSchoolHost() throws Exception {
        HttpPolicy.AUTH.check(HttpUrl.parse("https://ftp.lzzy.net/x.pdf"));
    }

    @Test(expected = IOException.class)
    public void authPolicyRejectsLookalikeHost() throws Exception {
        HttpPolicy.AUTH.check(HttpUrl.parse("https://zfjw.lzzy.net.evil.com/x.pdf"));
    }

    @Test(expected = IOException.class)
    public void authPolicyRejectsPlainHttpAttachment() throws Exception {
        HttpPolicy.AUTH.check(HttpUrl.parse("http://zfjw.lzzy.net/ftp/x.pdf"));
    }

    @Test
    public void policyInstancesAreDistinct() {
        assertNotNull(HttpPolicy.GITHUB);
        assertNotNull(HttpPolicy.AUTH);
    }
}
