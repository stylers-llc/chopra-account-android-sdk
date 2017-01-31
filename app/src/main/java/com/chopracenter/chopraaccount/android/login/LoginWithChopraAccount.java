package com.chopracenter.chopraaccount.android.login;

import android.app.Dialog;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.chopracenter.chopraaccount.android.R;
import com.chopracenter.chopraaccount.android.rest.APIManager;
import com.chopracenter.chopraaccount.android.rest.ChopraTask;
import com.chopracenter.chopraaccount.android.rest.ResponseListener;
import com.chopracenter.chopraaccount.android.utils.PhpSerializer;
import com.chopracenter.chopraaccount.android.utils.SerializedPhpParser;
import com.scottyab.aescrypt.AESCrypt;

import org.json.JSONObject;

import java.net.URLDecoder;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class LoginWithChopraAccount {

    private Context context;
    private String apiKey;
    private String clientKey;
    private String clientSecret;
    private String platform;
    private String namespace;
    private boolean autoclose;

    private ChopraLoginListener chopraLoginListener;
    private GetChopraAccountListener getChopraAccountListener;
    private APIManager apiManager;

    public static final String TAG = "ChopraAccountSDKWebView";

    public LoginWithChopraAccount(Context context, String baseUrl, String apiUrl, String apiKey, String platform, String namespace, String clientKey, String clientSecret, boolean autoclose) {
        this.context = context;
        apiManager = new APIManager();
        apiManager.setBaseAuthUrl(baseUrl);
        apiManager.setBaseAPiUrl(apiUrl);
        this.apiKey = apiKey;
        this.platform = platform;
        this.namespace = namespace;
        this.clientKey = clientKey;
        this.clientSecret = clientSecret;
        this.autoclose = autoclose;
    }

    public void showEmailLoginView(ChopraLoginListener _Chopra_loginListener) {
        String urlString = apiManager.getBaseAuthUrl()
                + "/tokenauth"
                + "?client_key=" + clientKey
                + "&platform_type=" + platform
                + "&namespace=" + namespace;

        chopraLoginListener = _Chopra_loginListener;
        showPopup(context, urlString);
    }

    //socialType: 0 - google+, 1 - facebook
    public void showSocialLoginView(String _socialId, String _socialToken, int _socialType, ChopraLoginListener _ChopraLoginListener) {
        String urlString = apiManager.getBaseAuthUrl()
                + "/social/tokenauth"
                + "?client_key=" + clientKey
                + "&platform_type=" + platform
                + "&namespace=" + namespace
                + "&social_id=" + _socialId
                + "&social_token=" + encryptSocialToken(_socialToken)
                + "&social_type=" + ((_socialType == 1) ? "facebook" : "google");

        chopraLoginListener = _ChopraLoginListener;
        showPopup(context, urlString);
    }

    public void getChopraAccount(String ssoToken, GetChopraAccountListener _getChopraAccountListener) {
        getChopraAccountListener = _getChopraAccountListener;
        new GetAboutTaskCommand().start(ssoToken);
    }

    public void logout(String ssoToken) {
        new LogoutTaskCommand().start(ssoToken);
    }

    //PopUpWebView
    //
    private void showPopup(final Context context, String urlString) {
        View layout = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.dialog_login, null);

        final Dialog rootView = new Dialog(context, android.R.style.Theme_NoTitleBar);
        rootView.setContentView(layout);
        rootView.show();

        final View progressBar = layout.findViewById(R.id.progressBar);
        final View closeButton = layout.findViewById(R.id.closeButton);

        final WebView webView = (WebView) layout.findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.requestFocus(View.FOCUS_DOWN);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {

            public void onPageFinished(WebView view, String url) {
                if (url.contains("sso_code")) {

                    String[] ssoResults = getSSOKeyFromUrl(url);
                    chopraLoginListener.loginFinished(ssoResults[0], ssoResults[1], null);

                    if (autoclose && rootView.isShowing()) {
                        rootView.dismiss();
                    }
                } else {
                    progressBar.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    closeButton.setVisibility(View.VISIBLE);
                }
            }
        });
        webView.loadUrl(urlString);

        closeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                rootView.dismiss();
            }
        });

    }

    private String[] getSSOKeyFromUrl(String url) {
        String[] resultArray = new String[2]; //0:u_id ; 1:api_token
        String ssoKey = url.substring(url.indexOf("sso_code") + 9);
        Log.d(TAG, "SSO_KEY: " + ssoKey);
        try {
            ssoKey = URLDecoder.decode(ssoKey, "UTF-8");
            byte[] data = Base64.decode(ssoKey, Base64.DEFAULT);

            JSONObject jsonObject = new JSONObject(new String(data));
            String SecretKey = clientSecret;
            SecretKeySpec keyspec = new SecretKeySpec(SecretKey.getBytes(), "AES");

            try {
                byte[] messageAfterDecrypt = AESCrypt.decrypt(
                        keyspec,
                        Base64.decode(jsonObject.get("iv").toString(), Base64.DEFAULT),
                        Base64.decode(jsonObject.get("value").toString(), Base64.DEFAULT));

                String str = new String(messageAfterDecrypt);
                SerializedPhpParser tempParser = new SerializedPhpParser(str);
                Object tempResult = tempParser.parse();

                SerializedPhpParser serializedPhpParser = new SerializedPhpParser(tempResult.toString());
                LinkedHashMap<String, Object> resultHashMap = (LinkedHashMap<String, Object>) serializedPhpParser.parse();
                resultArray[0] = String.valueOf(resultHashMap.get("api_token"));
                resultArray[1] = String.valueOf(resultHashMap.get("u_id"));

            } catch (GeneralSecurityException e) {
                Log.d(TAG, "Security error: ", e);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultArray;
    }

    private String encryptSocialToken(String token) {

        try {
            String serializedString = PhpSerializer.serialize(token);

            SecretKey secret = new SecretKeySpec(clientSecret.getBytes(), "AES");
            Cipher encryptionCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
//            Log.i("app_debug", encryptionCipher.getParameters() + " ");
            byte[] iv = "0000000000000000".getBytes();
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            encryptionCipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
            String encryptedText = Base64.encodeToString(encryptionCipher.doFinal(serializedString.getBytes()), Base64.DEFAULT);
//            Log.i("app_debug", "Encrypted: " + encryptedText);

            String input = Base64.encodeToString(iv, Base64.DEFAULT) + encryptedText;
//            Log.d("app_debug", input);
//            Log.d("app_debug", hashHmac("HMACSHA256", input, clientSecret));

            JSONObject resultObject = new JSONObject();
            resultObject.put("iv", Base64.encodeToString(iv, Base64.DEFAULT));
            resultObject.put("value", encryptedText);
            resultObject.put("mac", hashHmac("HMACSHA256", input, clientSecret));

            byte[] resultData = resultObject.toString().getBytes("UTF-8");

            return Base64.encodeToString(resultData, Base64.DEFAULT);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String hashHmac(String type, String value, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(type);
            javax.crypto.spec.SecretKeySpec secret = new javax.crypto.spec.SecretKeySpec(key.getBytes(), type);
            mac.init(secret);
            byte[] digest = mac.doFinal(value.getBytes());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            String s;
            for (byte b : digest) {
                s = Integer.toHexString(b & 0xFF);
                if (s.length() == 1) sb.append('0');
                sb.append(s);
            }
            return sb.toString();
        } catch (Exception e) {
//            Log.d("app_debug", "Exception [" + e.getMessage() + "]", e);
        }
        return "";
    }

    private class GetAboutTaskCommand implements ResponseListener {
        public void start(String ssoToken) {
            try {
                new ChopraTask(apiManager.getBaseAPiUrl() + "/auth",
                        ssoToken, apiKey, clientKey, APIManager.GET, this).execute();
            } catch (Exception e) {
                e.printStackTrace();
                onResponseFailed(e.toString());
            }
        }

        @Override
        public void onResponseReceived(int responseCode, String response) {
            try {
                JSONObject obj = new JSONObject(response);
                if (responseCode == 200) {
                    getChopraAccountListener.finish(new ChopraAccount(obj), null);
                } else {
                    getChopraAccountListener.finish(null, "Error");
                }
            } catch (Exception e) {
                e.printStackTrace();
                getChopraAccountListener.finish(null, e.toString());
            }
        }

        @Override
        public void onResponseFailed(String error) {
            Log.w(TAG, "Error: " + error);
        }
    }

    private class LogoutTaskCommand implements ResponseListener {
        public void start(String ssoToken) {
            try {
                new ChopraTask(apiManager.getBaseAPiUrl() + "/auth",
                        ssoToken, apiKey, clientKey, APIManager.DELETE, this).execute();
            } catch (Exception e) {
                e.printStackTrace();
                onResponseFailed(e.toString());
            }
        }

        @Override
        public void onResponseReceived(int responseCode, String response) {
            Log.d(TAG, "Logout result: " + response);
        }

        @Override
        public void onResponseFailed(String error) {
            Log.w(TAG, "Error: " + error);
        }
    }

}