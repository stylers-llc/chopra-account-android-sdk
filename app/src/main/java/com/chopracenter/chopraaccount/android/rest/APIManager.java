package com.chopracenter.chopraaccount.android.rest;

public class APIManager {

    // HTTP request methods
    public static String POST = "POST";
    public static String GET = "GET";
    public static String DELETE = "DELETE";
    public static String PUT = "PUT";

    // HTTP timeout values (in milliseconds)
    public static int HTTP_CONNECTION_TIMEOUT = 30000;
    public static int HTTP_READ_TIMEOUT = 30000;
    public static int HTTP_READ_TIMEOUT_FILEUPLOAD = 60000;

    // MIME types
    public static String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static String CONTENT_TYPE_MULTIPART = "multipart/form-data; charset=UTF-8;";
    public static String CONTENT_TYPE_JSON = "application/json; charset=UTF-8;";

    // URLs
    private String baseAuthUrl;
    private String baseAPiUrl;

    // Getters / setters
    public String getBaseAuthUrl() {
        return baseAuthUrl;
    }

    public void setBaseAuthUrl(String baseAuthUrl) {
        this.baseAuthUrl = baseAuthUrl;
    }

    public String getBaseAPiUrl() {
        return baseAPiUrl;
    }

    public void setBaseAPiUrl(String baseAPiUrl) {
        this.baseAPiUrl = baseAPiUrl;
    }
}