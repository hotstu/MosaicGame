package com.example.slab.mosaicgame;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    PushBoxLayout mBoxLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBoxLayout = (PushBoxLayout) findViewById(R.id.box);
        if (savedInstanceState == null)
            mBoxLayout.startGame();// dont call it when activity recreated by android, PushBoxLayout handle it automaticlly
    }

    public void btn1(View view) {
        mBoxLayout.setLevel(3).setResId(R.drawable.pokemongo_logo).startGame();
    }

    public void btn2(View view) {
        mBoxLayout.setLevel(2).setResId(R.mipmap.ic_launcher).startGame();
    }

    public void btn3(View view) {
        mBoxLayout.setLevel(4).setResId(R.drawable.doraemon).startGame();
    }
}
