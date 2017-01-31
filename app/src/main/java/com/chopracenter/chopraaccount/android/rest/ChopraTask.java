package com.chopracenter.chopraaccount.android.rest;

import android.os.AsyncTask;

public class ChopraTask extends AsyncTask<HTTPRequest, Void, Void> {

    private HTTPRequest request;
    private int responseCode;
    private String result;
    private String error = null;

    private ResponseListener responseListener;

    public ChopraTask(String url, String apiToken, String apiKey, String clientKey, String requestMethod, ResponseListener responseListener) {
        request = new HTTPRequest(url);
        request.addHeader("X-SSO-ApiKey", apiKey);
        request.addHeader("X-SSO-ClientKey", clientKey);
        request.addHeader("Authorization", "Bearer " + apiToken);
        request.setContentType(APIManager.CONTENT_TYPE_JSON);
        request.requestMethod = requestMethod;

        this.responseListener = responseListener;
    }

    @Override
    protected Void doInBackground(HTTPRequest... params) {
        try {
            params[0].request(new ResponseListener() {
                @Override
                public void onResponseReceived(int _responseCode, String _result) {
                    responseCode = _responseCode;
                    result = _result;
                }

                @Override
                public void onResponseFailed(String _error) {
                    error = _error;
                }

            });
        } catch (Exception e) {
            error = e.toString();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void param) {
        if (error == null)
            responseListener.onResponseReceived(responseCode, result);
        else
            responseListener.onResponseFailed(error);
    }

    public void execute() {
        if (request != null) {
            this.execute(request);
        }
    }

}