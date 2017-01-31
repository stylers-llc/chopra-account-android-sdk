package com.chopracenter.chopraaccount.android.rest;

public interface ResponseListener {
    void onResponseReceived(int responseCode, String result);
    void onResponseFailed(String error);
}