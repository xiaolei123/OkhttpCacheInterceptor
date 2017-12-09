# OkhttpCacheInterceptor
The OkHttpCacheInterceptor 一个OkHttp的网络缓存拦截器，可自定义场景对请求的数据进行缓存
>前言：前段时间在开发APP的时候，经常出现由于用户设备环境的原因，拿不到从网络端获取的数据，所以在APP端展现的结果总是一个空白的框，这种情况对于用户体验来讲是极其糟糕的，所以，苦思冥想决定对OKHTTP下手(因为我在项目中使用的网络请求框架就是OKHTTP)，则 写了这么一个网络数据缓存拦截器。

OK，那么我们决定开始写了，我先说一下思路：
##思路篇
既然要写的是网络数据缓存拦截器，主要是利用了OKHTTP强大的拦截器功能，那么我们应该对哪些数据进行缓存呢，或者在哪些情况下启用数据进行缓存机制呢？

- **第一** ：支持POST请求，因为官方已经提供了一个缓存拦截器,但是有一个缺点，就是只能对GET请求的数据进行缓存，对POST则不支持。
- **第二** ：网络正常的时候，则是去网络端取数据，如果网络异常，比如TimeOutException UnKnowHostException 诸如此类的问题，那么我们就需要去缓存取出数据返回。
- **第三** ：如果从缓存中取出的数据是空的，那么我们还是需要让这次请求走剩下的正常的流程。
- **第四** ：调用者必须对缓存机制完全掌控，可以根据自己的业务需求选择性的对数据决定是否进行缓存。
- **第五** ：使用必须简单，这是最最最最重要的一点。

好，我们上面罗列了五点是我们的大概思路，现在来说一下代码部分：

##代码篇

- **缓存框架** ：我这里使用的缓存框架是**DiskLruCache** **[https://github.com/JakeWharton/DiskLruCache](https://github.com/JakeWharton/DiskLruCache "缓存框架")** 这个缓存框架可以存储到本地，也经过谷歌认可，这也是选择这个框架的主要原因。我这里也对缓存框架进行封装了一个CacheManager类:

```java
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.xiaolei.OkhttpCacheInterceptor.Log.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Created by xiaolei on 2017/5/17.
 */

public class CacheManager
{
    public static final String TAG = "CacheManager";

    //max cache size 10mb
    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 10;

    private static final int DISK_CACHE_INDEX = 0;

    private static final String CACHE_DIR = "responses";

    private DiskLruCache mDiskLruCache;

    private volatile static CacheManager mCacheManager;

    public static CacheManager getInstance(Context context)
    {
        if (mCacheManager == null)
        {
            synchronized (CacheManager.class)
            {
                if (mCacheManager == null)
                {
                    mCacheManager = new CacheManager(context);
                }
            }
        }
        return mCacheManager;
    }

    private CacheManager(Context context)
    {
        File diskCacheDir = getDiskCacheDir(context, CACHE_DIR);
        if (!diskCacheDir.exists())
        {
            boolean b = diskCacheDir.mkdirs();
            Log.d(TAG, "!diskCacheDir.exists() --- diskCacheDir.mkdirs()=" + b);
        }
        if (diskCacheDir.getUsableSpace() > DISK_CACHE_SIZE)
        {
            try
            {
                mDiskLruCache = DiskLruCache.open(diskCacheDir,
                        getAppVersion(context), 1/*一个key对应多少个文件*/, DISK_CACHE_SIZE);
                Log.d(TAG, "mDiskLruCache created");
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * 同步设置缓存
     */
    public void putCache(String key, String value)
    {
        if (mDiskLruCache == null) return;
        OutputStream os = null;
        try
        {
            DiskLruCache.Editor editor = mDiskLruCache.edit(encryptMD5(key));
            os = editor.newOutputStream(DISK_CACHE_INDEX);
            os.write(value.getBytes());
            os.flush();
            editor.commit();
            mDiskLruCache.flush();
        } catch (IOException e)
        {
            e.printStackTrace();
        } finally
        {
            if (os != null)
            {
                try
                {
                    os.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 异步设置缓存
     */
    public void setCache(final String key, final String value)
    {
        new Thread()
        {
            @Override
            public void run()
            {
                putCache(key, value);
            }
        }.start();
    }

    /**
     * 同步获取缓存
     */
    public String getCache(String key)
    {
        if (mDiskLruCache == null)
        {
            return null;
        }
        FileInputStream fis = null;
        ByteArrayOutputStream bos = null;
        try
        {
            DiskLruCache.Snapshot snapshot = mDiskLruCache.get(encryptMD5(key));
            if (snapshot != null)
            {
                fis = (FileInputStream) snapshot.getInputStream(DISK_CACHE_INDEX);
                bos = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int len;
                while ((len = fis.read(buf)) != -1)
                {
                    bos.write(buf, 0, len);
                }
                byte[] data = bos.toByteArray();
                return new String(data);
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        } finally
        {
            if (fis != null)
            {
                try
                {
                    fis.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            if (bos != null)
            {
                try
                {
                    bos.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * 异步获取缓存
     */
    public void getCache(final String key, final CacheCallback callback)
    {
        new Thread()
        {
            @Override
            public void run()
            {
                String cache = getCache(key);
                callback.onGetCache(cache);
            }
        }.start();
    }

    /**
     * 移除缓存
     */
    public boolean removeCache(String key)
    {
        if (mDiskLruCache != null)
        {
            try
            {
                return mDiskLruCache.remove(encryptMD5(key));
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 获取缓存目录
     */
    private File getDiskCacheDir(Context context, String uniqueName)
    {
        String cachePath = context.getCacheDir().getPath();
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 对字符串进行MD5编码
     */
    public static String encryptMD5(String string)
    {
        try
        {
            byte[] hash = MessageDigest.getInstance("MD5").digest(
                    string.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                if ((b & 0xFF) < 0x10)
                {
                    hex.append("0");
                }
                hex.append(Integer.toHexString(b & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return string;
    }

    /**
     * 获取APP版本号
     */
    private int getAppVersion(Context context)
    {
        PackageManager pm = context.getPackageManager();
        try
        {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi == null ? 0 : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }
        return 0;
    }
}
```
- **缓存CacheInterceptor拦截器** ：利用OkHttp的Interceptor拦截器机制，智能判断缓存场景，以及网络情况，对不同的场景进行处理。

```java
import android.content.Context;

import com.xiaolei.OkhttpCacheInterceptor.Catch.CacheManager;
import com.xiaolei.OkhttpCacheInterceptor.Log.Log;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 字符串的缓存类
 * Created by xiaolei on 2017/12/9.
 */
public class CacheInterceptor implements Interceptor
{
    private Context context;

    public void setContext(Context context)
    {
        this.context = context;
    }

    public CacheInterceptor(Context context)
    {
        this.context = context;
    }

    @Override
    public Response intercept(Chain chain) throws IOException
    {
        Request request = chain.request();
        String cacheHead = request.header("cache");
        String cache_control = request.header("Cache-Control");

        if ("true".equals(cacheHead) ||                              // 意思是要缓存
                (cache_control != null && !cache_control.isEmpty())) // 这里还支持WEB端协议的缓存头
        {
            long oldnow = System.currentTimeMillis();
            String url = request.url().url().toString();
            String responStr = null;
            String reqBodyStr = getPostParams(request);
            try
            {
                Response response = chain.proceed(request);
                if (response.isSuccessful()) // 只有在网络请求返回成功之后，才进行缓存处理，否则，404存进缓存，岂不笑话
                {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null)
                    {
                        responStr = responseBody.string();
                        if (responStr == null)
                        {
                            responStr = "";
                        }
                        CacheManager.getInstance(context).setCache(CacheManager.encryptMD5(url + reqBodyStr), responStr);//存缓存，以链接+参数进行MD5编码为KEY存
                        Log.i("HttpRetrofit", "--> Push Cache:" + url + " :Success");
                    }
                    return getOnlineResponse(response, responStr);
                } else
                {
                    return chain.proceed(request);
                }
            } catch (Exception e)
            {
                Response response = getCacheResponse(request, oldnow); // 发生异常了，我这里就开始去缓存，但是有可能没有缓存，那么久需要丢给下一轮处理了
                if (response == null)
                {
                    return chain.proceed(request);//丢给下一轮处理
                } else
                {
                    return response;
                }
            }
        } else
        {
            return chain.proceed(request);
        }
    }

    private Response getCacheResponse(Request request, long oldNow)
    {
        Log.i("HttpRetrofit", "--> Try to Get Cache   --------");
        String url = request.url().url().toString();
        String params = getPostParams(request);
        String cacheStr = CacheManager.getInstance(context).getCache(CacheManager.encryptMD5(url + params));//取缓存，以链接+参数进行MD5编码为KEY取
        if (cacheStr == null)
        {
            Log.i("HttpRetrofit", "<-- Get Cache Failure ---------");
            return null;
        }
        Response response = new Response.Builder()
                .code(200)
                .body(ResponseBody.create(null, cacheStr))
                .request(request)
                .message("OK")
                .protocol(Protocol.HTTP_1_0)
                .build();
        long useTime = System.currentTimeMillis() - oldNow;
        Log.i("HttpRetrofit", "<-- Get Cache: " + response.code() + " " + response.message() + " " + url + " (" + useTime + "ms)");
        Log.i("HttpRetrofit", cacheStr + "");
        return response;
    }

    private Response getOnlineResponse(Response response, String body)
    {
        ResponseBody responseBody = response.body();
        return new Response.Builder()
                .code(response.code())
                .body(ResponseBody.create(responseBody == null ? null : responseBody.contentType(), body))
                .request(response.request())
                .message(response.message())
                .protocol(response.protocol())
                .build();
    }

    /**
     * 获取在Post方式下。向服务器发送的参数
     *
     * @param request
     * @return
     */
    private String getPostParams(Request request)
    {
        String reqBodyStr = "";
        String method = request.method();
        if ("POST".equals(method)) // 如果是Post，则尽可能解析每个参数
        {
            StringBuilder sb = new StringBuilder();
            if (request.body() instanceof FormBody)
            {
                FormBody body = (FormBody) request.body();
                if (body != null)
                {
                    for (int i = 0; i < body.size(); i++)
                    {
                        sb.append(body.encodedName(i)).append("=").append(body.encodedValue(i)).append(",");
                    }
                    sb.delete(sb.length() - 1, sb.length());
                }
                reqBodyStr = sb.toString();
                sb.delete(0, sb.length());
            }
        }
        return reqBodyStr;
    }

}
```

以上是主体思路，以及主要实现代码，现在来说一下使用方式
##使用方式：

gradle使用：
```grandle
compile 'com.xiaolei:OkhttpCacheInterceptor:1.0.0'
```
由于是刚刚提交到Jcenter，可能会出现拉不下来的情况(暂时还未过审核)，着急的读者可以再在你的**Project:build.gradle**里的**repositories**里新增我maven的链接：

```grandle
allprojects {
    repositories {
        maven{url 'https://dl.bintray.com/kavipyouxiang/maven'}
    }
}
```
我们新建一个项目，项目截图是这样的：
![项目截图](http://upload-images.jianshu.io/upload_images/1014308-c11944bff11291d5.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

demo很简单，一个主页面，一个Bean，一个Retrofit，一个网络请求接口

注意，因为是网络，缓存，有关，所以，毫无疑问我们要在manifest里面添加网络请求权限，文件读写权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

使用的时候，你只需要为你的OKHttpClient添加一个Interceptor：

```java
client = new OkHttpClient.Builder()
                .addInterceptor(new CacheInterceptor(context))//添加缓存拦截器，添加缓存的支持
                .retryOnConnectionFailure(true)//失败重连
                .connectTimeout(30, TimeUnit.SECONDS)//网络请求超时时间单位为秒
                .build();
```

如果你想哪个接口的数据缓存，那么久为你的网络接口，添加一个请求头**`CacheHeaders.java`**这个类里包含了所有的情况，一般情况下只需要`CacheHeaders.NORMAL`就可以了
```java
public interface Net
{
    @Headers(CacheHeaders.NORMAL) // 这里是关键
    @FormUrlEncoded
    @POST("geocoding")
    public Call<DataBean> getIndex(@Field("a") String a);
}

```

业务代码：
```java
Net net = retrofitBase.getRetrofit().create(Net.class);
        Call<DataBean> call = net.getIndex("苏州市");
        call.enqueue(new Callback<DataBean>()
        {
            @Override
            public void onResponse(Call<DataBean> call, Response<DataBean> response)
            {
                DataBean data = response.body();
                Date date = new Date();
                textview.setText(date.getMinutes() + " " + date.getSeconds() + ":\n" + data + "");
            }

            @Override
            public void onFailure(Call<DataBean> call, Throwable t)
            {
                textview.setText("请求失败！");
            }
        });
```

我们这里对网络请求，成功了，则在界面上输出文字,加上当前时间，网络失败，则输出一个请求失败。

大概代码就是这样子的，详细代码，文章末尾将贴出demo地址

##看效果:演示图

![演示图](http://upload-images.jianshu.io/upload_images/1014308-f4a982a2d9e1ef75.gif?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

这里演示了，从网络正常，到网络不正常，再恢复到正常的情况。

##结尾
以上篇章就是整个从思路，到代码，再到效果图的流程，这里贴一下DEMO的地址，喜欢的可以点个Start

[Demo地址:https://github.com/xiaolei123/OkhttpCacheInterceptor](https://github.com/xiaolei123/OkhttpCacheInterceptor)




