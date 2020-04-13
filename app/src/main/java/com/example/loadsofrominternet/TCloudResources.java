package com.example.loadsofrominternet;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TCloudResources {

    //存储桶的根地址
    public static final String bucketAddress="https://solibrarytest-1257936688.cos.ap-beijing.myqcloud.com/";

    //Android系统Download文件夹的永久地址
    public static final String downloadAddress="/storage/emulated/0/Download/";

    /*
    通过HTTP请求获取一个文件，以字符串的形式返回
     */
    public static String run(OkHttpClient client, String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            assert response.body() != null;
            return response.body().string();
        } else {
            throw new IOException("Unexpected code " + response);
        }
    }



}
