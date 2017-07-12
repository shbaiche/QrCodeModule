package com.shbaiche.testqrcode;

import android.os.Bundle;

import com.shbaiche.qrcode.activity.CaptureActivity;

public class QrCodeActivity extends CaptureActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_code);
    }
}
