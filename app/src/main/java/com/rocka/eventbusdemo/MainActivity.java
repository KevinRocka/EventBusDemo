package com.rocka.eventbusdemo;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {

    private TextView refresh;
    private TextView jump;
    private TextView register;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        refresh = findViewById(R.id.textView);
        register = findViewById(R.id.textView1);
        jump = findViewById(R.id.textView2);
        context = this;

        register.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Toast.makeText(context, "register success" , Toast.LENGTH_LONG).show();
                EventBus.getDefault().register(context);
            }
        });


        jump.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context , RefreshActivity.class);
                startActivity(intent);
            }
        });
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMessageRefresh(EventMessage eventMessage){
        refresh.setText("refresh:" + eventMessage.num);
    }

    @Subscribe(threadMode = ThreadMode.POSTING, sticky = true)
    public void onEventMessageStickyRefresh(EventStickyMessage eventMessage){
        refresh.setText("sticky refresh:" + eventMessage.num);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
