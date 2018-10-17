package com.rocka.eventbusdemo;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class RefreshActivity extends AppCompatActivity {
    Context context;
    TextView normalEvent;
    TextView stickyEvent;
    int a = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refresh);
        context = this;
        normalEvent = findViewById(R.id.textView);

        stickyEvent = findViewById(R.id.textView2);
        stickyEvent.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                a = a + 1;
                Toast.makeText(context, "refresh sticky :" + a, Toast.LENGTH_LONG).show();
                EventStickyMessage eventMessage = new EventStickyMessage(a);
                EventBus.getDefault().postSticky(eventMessage);
            }
        });

        normalEvent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                a = a + 1;
                Toast.makeText(context, "refresh :" + a, Toast.LENGTH_LONG).show();
                EventMessage eventMessage = new EventMessage(a);
                EventBus.getDefault().post(eventMessage);
            }
        });
    }
}
