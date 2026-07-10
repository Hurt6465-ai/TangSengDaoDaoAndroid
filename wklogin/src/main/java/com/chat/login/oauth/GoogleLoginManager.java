package com.chat.login.oauth;

import android.app.Activity;
import android.os.CancellationSignal;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.chat.login.R;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;

import java.security.SecureRandom;

/**
 * Google Credential Manager 登录封装。
 *
 * <p>这里只从 Google 获取 ID Token。ID Token 必须发送到业务后端进行签名、aud、iss、exp
 * 和 nonce 校验，客户端不能把 Google 用户 ID 当作登录凭证。</p>
 */
public final class GoogleLoginManager {

    public interface Callback {
        void onSuccess(@NonNull String idToken, @NonNull String nonce);

        void onCancelled();

        void onError(@StringRes int messageRes, Throwable throwable);
    }

    private final Activity activity;
    private final CredentialManager credentialManager;
    private CancellationSignal cancellationSignal;

    public GoogleLoginManager(@NonNull Activity activity) {
        this.activity = activity;
        this.credentialManager = CredentialManager.create(activity);
    }

    /**
     * 显式按钮登录：显示 Google 账号选择界面，不跳转普通浏览器。
     */
    public void signIn(@NonNull String serverClientId, @NonNull Callback callback) {
        String clientId = serverClientId.trim();
        if (!isValidClientId(clientId)) {
            callback.onError(R.string.google_login_not_configured, null);
            return;
        }

        cancel();
        cancellationSignal = new CancellationSignal();
        String nonce = generateSecureRandomNonce();

        GetSignInWithGoogleOption googleOption = new GetSignInWithGoogleOption.Builder(clientId)
                .setNonce(nonce)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                cancellationSignal,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        cancellationSignal = null;
                        handleCredential(result, nonce, callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        cancellationSignal = null;
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onCancelled();
                        } else if (e instanceof NoCredentialException) {
                            callback.onError(R.string.google_login_no_credential, e);
                        } else {
                            callback.onError(R.string.google_login_failed, e);
                        }
                    }
                }
        );
    }

    public void cancel() {
        if (cancellationSignal != null && !cancellationSignal.isCanceled()) {
            cancellationSignal.cancel();
        }
        cancellationSignal = null;
    }

    private void handleCredential(
            @NonNull GetCredentialResponse response,
            @NonNull String nonce,
            @NonNull Callback callback
    ) {
        Credential credential = response.getCredential();
        if (!(credential instanceof CustomCredential)) {
            callback.onError(R.string.google_login_invalid_response, null);
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            callback.onError(R.string.google_login_invalid_response, null);
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(customCredential.getData());
            String idToken = googleCredential.getIdToken();
            if (TextUtils.isEmpty(idToken)) {
                callback.onError(R.string.google_login_invalid_response, null);
                return;
            }
            callback.onSuccess(idToken, nonce);
        } catch (GoogleIdTokenParsingException | IllegalArgumentException e) {
            callback.onError(R.string.google_login_invalid_response, e);
        }
    }

    private boolean isValidClientId(String clientId) {
        return !TextUtils.isEmpty(clientId)
                && !clientId.startsWith("YOUR_")
                && clientId.endsWith(".apps.googleusercontent.com");
    }

    private String generateSecureRandomNonce() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.encodeToString(
                randomBytes,
                Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING
        );
    }
}
