package com.chopracenter.chopraaccount.android.rest;

import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

public class HTTPRequest {

    private String mUrl;
    private HashMap<String, String> mRequestParams = new HashMap<>();

    public String requestMethod;
    private String contentType = APIManager.CONTENT_TYPE_FORM;
    private HashMap<String, String> headers;
    private JSONObject postJSON;

    public HTTPRequest(String url) {
        mUrl = url;
    }

    public void addParam(String key, String value) {
        if (value != null)
            mRequestParams.put(key, value);
    }

    public void addHeader(String key, String value) {
        if (headers == null)
            headers = new HashMap<>();
        headers.put(key, value);
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setPostJSON(JSONObject postJSON) {
        this.postJSON = postJSON;
    }

    public void setBasicAuthentication(final String username, final String password) {
        Authenticator.setDefault(new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password.toCharArray());

            }
        });
    }

    public void request(ResponseListener response) {
        Uri.Builder b = Uri.parse(mUrl).buildUpon();

        try {
            if (mRequestParams != null) {
                for (Entry<String, String> e : mRequestParams.entrySet()) {
                    b.appendQueryParameter(e.getKey(), e.getValue());
                }
            }
            URL u = new URL(b.toString());
            String postData = null;
            if (!requestMethod.equals(APIManager.GET) && !requestMethod.equals(APIManager.DELETE)) {
                if (postJSON != null)
                    postData = postJSON.toString();
                else
                    postData = u.getQuery();
                String url = u.getProtocol() + "://" + u.getHost() + u.getPath();
                u = new URL(url);
            }

            HttpURLConnection urlConnection = (HttpURLConnection) u.openConnection();
            urlConnection.setRequestMethod(requestMethod);
            urlConnection.setConnectTimeout(APIManager.HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(APIManager.HTTP_READ_TIMEOUT);

            if (headers != null && headers.size() > 0) {
                Set<String> keys = headers.keySet();
                for (String key : keys) {
                    urlConnection.setRequestProperty(key, headers.get(key));
                }
            }

            if (!requestMethod.equals(APIManager.GET) && !requestMethod.equals(APIManager.DELETE))
                urlConnection.setDoOutput(true);

            if (postData != null) {
                char[] bytes = postData.toCharArray();
                urlConnection.setRequestProperty("Content-Type", contentType);

                OutputStreamWriter post = null;
                try {
                    post = new OutputStreamWriter(urlConnection.getOutputStream());
                    post.write(bytes);
                    post.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                    response.onResponseFailed(e.getMessage());
                } finally {
                    if (post != null) {
                        try {
                            post.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            InputStream in;

            if (urlConnection.getResponseCode() >= 400) {
                in = new BufferedInputStream(urlConnection.getErrorStream());
            } else {
                in = new BufferedInputStream(urlConnection.getInputStream());
            }

            String result = readStream(in);
            int responseCode = urlConnection.getResponseCode();

            urlConnection.disconnect();
            response.onResponseReceived(responseCode, result);
        } catch (Exception e) {
            response.onResponseFailed(e.getMessage());
        }
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int i = is.read();
        while (i != -1) {
            bo.write(i);
            i = is.read();
        }
        return bo.toString();
    }

}