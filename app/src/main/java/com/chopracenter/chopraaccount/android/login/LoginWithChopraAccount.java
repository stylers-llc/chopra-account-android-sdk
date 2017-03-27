package com.chopracenter.chopraaccount.android.login;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
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
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.scottyab.aescrypt.AESCrypt;

import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class LoginWithChopraAccount implements GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "ChopraAccountSDKWebView";

    private static final int RC_GOOGLE_SIGN_IN = 0;
    private static final int RC_GOOGLE_AUTHORIZATION = 1;

    private static final String SOCIAL_TYPE_GOOGLE = "google";
    private static final String SOCIAL_TYPE_FACEBOOK = "facebook";

    private final static String PLATFORM = "mobile";

    private String apiKey;
    private String clientKey;
    private String clientSecret;
    private String namespace;
    private boolean autoclose;

    private ChopraLoginListener chopraLoginListener;
    private GetChopraAccountListener getChopraAccountListener;
    private APIManager apiManager;

    // Facebook
    private CallbackManager callbackManager;

    //Google
    private GoogleApiClient googleApiClient;

    public LoginWithChopraAccount(String baseUrl, String apiUrl, String apiKey, String namespace, String clientKey, String clientSecret, boolean autoclose) {
        apiManager = new APIManager();
        apiManager.setBaseAuthUrl(baseUrl);
        apiManager.setBaseAPiUrl(apiUrl);
        this.apiKey = apiKey;
        this.namespace = namespace;
        this.clientKey = clientKey;
        this.clientSecret = clientSecret;
        this.autoclose = autoclose;
    }

    public void showRegistrationView(Context context) {
        String urlString = apiManager.getBaseAuthUrl()
                + "/tokenauth/registration"
                + "?client_key=" + clientKey
                + "&platform_type=" + PLATFORM
                + "&namespace=" + namespace;

        showPopup(context, urlString);
    }

    public void showEmailLoginView(Context context, ChopraLoginListener chopraLoginListener) {
        String urlString = apiManager.getBaseAuthUrl()
                + "/tokenauth"
                + "?client_key=" + clientKey
                + "&platform_type=" + PLATFORM
                + "&namespace=" + namespace;

        this.chopraLoginListener = chopraLoginListener;
        showPopup(context, urlString);
    }

    /**
     * onActivityResult forwarded from a Fragment
     *
     * @param fragment    The caller Fragment - used for routing further calls through it
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param data        An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     */
    public void onActivityResult(Fragment fragment, int requestCode, int resultCode, Intent data) {
        if (callbackManager != null) {
            if (callbackManager.onActivityResult(requestCode, resultCode, data))
                return;
        }

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess() && result.getSignInAccount() != null) {
                new GoogleLoginTask(result.getSignInAccount(), fragment).execute();
            } else {
                chopraLoginListener.loginFailed(result.getStatus().getStatusMessage());
            }
        } else if (requestCode == RC_GOOGLE_AUTHORIZATION) {
            fragment.startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(googleApiClient), RC_GOOGLE_SIGN_IN);
        }
    }


    /**
     * onActivityResult forwarded from an Activity
     *
     * @param activity    The caller Activity - used for routing further calls through it
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param data        An Intent, which can return result data to the caller
     *                    (various data can be attached to Intent "extras").
     */
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (callbackManager != null) {
            if (callbackManager.onActivityResult(requestCode, resultCode, data))
                return;
        }

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess() && result.getSignInAccount() != null) {
                new GoogleLoginTask(result.getSignInAccount(), activity).execute();
            } else {
                chopraLoginListener.loginFailed(result.getStatus().getStatusMessage());
            }
        } else if (requestCode == RC_GOOGLE_AUTHORIZATION) {
            activity.startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(googleApiClient), RC_GOOGLE_SIGN_IN);
        }
    }

    private class GoogleLoginTask extends AsyncTask<Void, Void, String> {

        GoogleSignInAccount account;
        Activity activity;
        Fragment fragment;
        String error;

        GoogleLoginTask(GoogleSignInAccount account, Activity activity) {
            super();
            this.account = account;
            this.activity = activity;
        }

        GoogleLoginTask(GoogleSignInAccount account, Fragment fragment) {
            super();
            this.account = account;
            this.activity = fragment.getActivity();
            this.fragment = fragment;
        }

        @Override
        protected String doInBackground(Void... params) {
            String token = null;
            error = "Unknown error";
            try {
                token = GoogleAuthUtil.getToken(
                        activity,
                        new Account(account.getEmail(), GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE),
                        "oauth2:" + Scopes.PROFILE);
            } catch (IOException e) {
                error = e.getMessage();
            } catch (GooglePlayServicesAvailabilityException playEx) {
                GoogleApiAvailability.getInstance().showErrorDialogFragment(
                        activity,
                        playEx.getConnectionStatusCode(),
                        RC_GOOGLE_AUTHORIZATION);
            } catch (UserRecoverableAuthException e) {
                if (fragment == null) {
                    activity.startActivityForResult(e.getIntent(), RC_GOOGLE_AUTHORIZATION);
                } else {
                    fragment.startActivityForResult(e.getIntent(), RC_GOOGLE_AUTHORIZATION);
                }
            } catch (GoogleAuthException e) {
                error = e.getMessage();
            }
            return token;
        }

        @Override
        protected void onPostExecute(String token) {
            if (token == null) {
                chopraLoginListener.loginFailed(error);
            } else {
                showSocialLoginView(
                        fragment.getContext(),
                        account.getId(),        //google userId
                        token,                  //google accessToken
                        LoginWithChopraAccount.SOCIAL_TYPE_GOOGLE,
                        chopraLoginListener);
            }
        }
    }

    private void initializeFacebook(final Context context) {
        if (callbackManager != null)
            return;

        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                showSocialLoginView(
                        context,
                        loginResult.getAccessToken().getUserId(),   //facebook userId
                        loginResult.getAccessToken().getToken(),    //facebook accessToken
                        LoginWithChopraAccount.SOCIAL_TYPE_FACEBOOK,
                        chopraLoginListener);
            }

            @Override
            public void onCancel() {
                // login canceled, nothing to do
            }

            @Override
            public void onError(FacebookException error) {
                chopraLoginListener.loginFailed(error.getMessage());
            }
        });
    }

    public void showFacebookLoginView(final Activity activity, final ChopraLoginListener chopraLoginListener) {
        initializeFacebook(activity);
        this.chopraLoginListener = chopraLoginListener;
        LoginManager.getInstance().logInWithReadPermissions(activity, null);
    }

    public void showFacebookLoginView(final android.app.Fragment fragment, final ChopraLoginListener chopraLoginListener) {
        initializeFacebook(fragment.getActivity());
        this.chopraLoginListener = chopraLoginListener;
        LoginManager.getInstance().logInWithReadPermissions(fragment, null);
    }

    public void showFacebookLoginView(final Fragment fragment, final ChopraLoginListener chopraLoginListener) {
        initializeFacebook(fragment.getActivity());
        this.chopraLoginListener = chopraLoginListener;
        LoginManager.getInstance().logInWithReadPermissions(fragment, null);
    }

    private void initializeGoogle(final FragmentActivity activity, final String token) {
        if (googleApiClient != null)
            return;

        // Configure sign-in to request the user's ID, email address, and basic profile.
        // ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(token)
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the options specified by gso.
        googleApiClient = new GoogleApiClient.Builder(activity)
                .enableAutoManage(activity, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        chopraLoginListener.loginFailed(connectionResult.getErrorMessage());
    }

    public void showGoogleLoginView(String token, final FragmentActivity activity, ChopraLoginListener chopraLoginListener) {
        initializeGoogle(activity, token);
        this.chopraLoginListener = chopraLoginListener;
        activity.startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(googleApiClient), RC_GOOGLE_SIGN_IN);
    }

    public void showGoogleLoginView(String token, final Fragment fragment, ChopraLoginListener chopraLoginListener) {
        initializeGoogle(fragment.getActivity(), token);
        this.chopraLoginListener = chopraLoginListener;
        fragment.startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(googleApiClient), RC_GOOGLE_SIGN_IN);
    }

    private void showSocialLoginView(Context context, String socialId, String socialToken, String socialType, ChopraLoginListener chopraLoginListener) {
        String urlString = apiManager.getBaseAuthUrl()
                + "/social/tokenauth"
                + "?client_key=" + clientKey
                + "&platform_type=" + PLATFORM
                + "&namespace=" + namespace
                + "&social_id=" + socialId
                + "&social_token=" + encryptSocialToken(socialToken)
                + "&social_type=" + socialType;

        this.chopraLoginListener = chopraLoginListener;
        showPopup(context, urlString);
    }

    public void getChopraAccount(String ssoToken, GetChopraAccountListener getChopraAccountListener) {
        this.getChopraAccountListener = getChopraAccountListener;
        new GetAboutTaskCommand().start(ssoToken);
    }

    public void logout(String ssoToken) {
        new LogoutTaskCommand().start(ssoToken);
        LoginManager.getInstance().logOut();
        if (googleApiClient != null)
            Auth.GoogleSignInApi.signOut(googleApiClient);
    }

    //PopUpWebView
    private void showPopup(final Context context, String urlString) {
        View layout = LayoutInflater.from(context).inflate(R.layout.dialog_login, null);

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
                String ssoCode = Uri.parse(url).getQueryParameter("sso_code");

                if (ssoCode != null) {
                    String[] ssoResults = getSSOKeyFromUrl(ssoCode);

                    if (chopraLoginListener != null) {
                        chopraLoginListener.loginFinished(ssoResults[0], ssoResults[1]);
                        chopraLoginListener = null;
                    }

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

    private String[] getSSOKeyFromUrl(String ssoKey) {
        String[] resultArray = new String[2]; //0:u_id ; 1:api_token
        Log.d(TAG, "SSO_KEY: " + ssoKey);
        try {
            JSONObject jsonObject = new JSONObject(new String(Base64.decode(ssoKey, Base64.DEFAULT)));

            try {
                byte[] messageAfterDecrypt = AESCrypt.decrypt(
                        new SecretKeySpec(clientSecret.getBytes(), "AES"),
                        Base64.decode(jsonObject.get("iv").toString(), Base64.DEFAULT),
                        Base64.decode(jsonObject.get("value").toString(), Base64.DEFAULT));

                String tempResult = new SerializedPhpParser(new String(messageAfterDecrypt)).parse().toString();

                LinkedHashMap<String, Object> resultHashMap = (LinkedHashMap<String, Object>) new SerializedPhpParser(tempResult).parse();
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