package com.chopracenter.chopraaccount.android.login;

public interface ChopraLoginListener {
    void loginFinished(String ssoToken, String userId, String errorMsg);
}