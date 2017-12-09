package com.xiaolei.Example;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.xiaolei.Example.Net.DataBean;
import com.xiaolei.Example.Net.Net;
import com.xiaolei.Example.Net.RetrofitBase;

import java.util.Date;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends Activity
{
    TextView textview;
    Button button;
    RetrofitBase retrofitBase;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textview = findViewById(R.id.textview);
        button = findViewById(R.id.button);

        retrofitBase = new RetrofitBase(this);

        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                doSomething();
            }
        });
    }

    private void doSomething()
    {
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
    }
}
