package com.lzqlove.jiaowu;

import android.app.Application;

/** 进程入口：安装本地崩溃日志，不初始化任何第三方统计或上报。 */
public class JiaowuApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        NativeCrashLog.captureAsync(this);
    }
}
