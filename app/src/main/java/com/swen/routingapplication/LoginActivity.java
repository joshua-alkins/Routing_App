package com.swen.routingapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    String token;
    String factoryID;

    EditText emailField;
    EditText passwordField;
    TextView errorText;
    Button loginButton;

    RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        emailField = findViewById(R.id.emailEditText);
        passwordField = findViewById(R.id.passwordEditText);
        errorText = findViewById(R.id.errorText);
        loginButton = findViewById(R.id.loginButton);

        requestQueue = Volley.newRequestQueue(this);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email;
                String password;

                email = emailField.getText().toString();
                password = passwordField.getText().toString();

                Log.d("TAG", "onClick: "+ email +" "+password);

                login(email,password);

            }
        });
    }

    private void login(String email, String password){
        String url = "https://pdq-routing-subsystem.herokuapp.com/security/driver-login";
        Map<String, String> params = new HashMap<String, String>();
        params.put("email",email);
        params.put("password",password);
        JSONObject request_params = new JSONObject(params);
        JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.POST, url, request_params,
                        new com.android.volley.Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                String valid;
                                try {
                                    valid = response.getString("valid");
                                    Log.d("TAG", "onResponse: "+valid);
                                    if("valid".compareTo(valid)>=0) {
                                        token = response.getString("token");
                                        factoryID = response.getString("factory_id");
                                        openMapActivity();
                                    }else{
                                        //report invalid credentials
                                        errorText.setText("Invalid credentials");
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        },
                        new com.android.volley.Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.e("TAG", "onErrorResponse:"+error);
                                Toast.makeText(getApplicationContext(), "Error occurred.",Toast.LENGTH_SHORT).show();
                            }
                        });
        requestQueue.add(request);
    }

    private void openMapActivity(){
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("token", token);
        intent.putExtra("factory_id",factoryID);
        startActivity(intent);
    }
}