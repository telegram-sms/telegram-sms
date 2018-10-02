package com.qwe7002.telegram_sms;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;


public class MainActivity extends AppCompatActivity {
    SharedPreferences sharedPreferences;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        final EditText chat_id = (EditText) findViewById(R.id.chat_id);
        final EditText bot_token = (EditText) findViewById(R.id.bot_token);
        Button save_button = (Button) findViewById(R.id.save);
        bot_token.setText(sharedPreferences.getString("bot_token",""));
        chat_id.setText(sharedPreferences.getString("chat_id",""));
        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("bot_token",bot_token.getText().toString() );
                editor.putString("chat_id", chat_id.getText().toString());
                editor.apply();

            }
        });
    }

}

