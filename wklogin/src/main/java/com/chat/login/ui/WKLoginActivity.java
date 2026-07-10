package com.chat.login.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.endpoint.entity.OtherLoginResultMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.login.R;
import com.chat.login.databinding.ActLoginLayoutBinding;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.oauth.GoogleLoginManager;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 手机号密码登录与 Google 登录页面。
 */
public class WKLoginActivity extends WKBaseActivity<ActLoginLayoutBinding>
        implements LoginContract.LoginView {

    private static final String DEFAULT_ZONE = "0086";
    private static final String OTHER_LOGIN_RESULT_ENDPOINT = "other_login_result";

    private WKAPPConfig wkappConfig;
    private String code = DEFAULT_ZONE;
    private LoginPresenter loginPresenter;
    private GoogleLoginManager googleLoginManager;
    private boolean googleLoginInProgress;

    @Override
    protected ActLoginLayoutBinding getViewBinding() {
        return ActLoginLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        loginPresenter = new LoginPresenter(this);
        googleLoginManager = new GoogleLoginManager(this);
    }

    @Override
    protected void initView() {
        initTheme();
        initAgreementCheckBox();
        showLoginReasonIfNeeded();
        restoreLastPhoneAccount();

        wkVBinding.loginTitleTv.setText(
                String.format(getString(R.string.login_title), getString(R.string.app_name))
        );
        wkVBinding.privacyPolicyTv.setOnClickListener(
                v -> showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html")
        );
        wkVBinding.userAgreementTv.setOnClickListener(
                v -> showWebView(WKApiConfig.baseWebUrl + "user_agreement.html")
        );

        // 原 GitHub/Gitee 插件容器继续保留，但 Google 登录直接使用当前页面按钮。
        // EndpointManager.getInstance().invoke(
        //         "other_login_view",
        //         new OtherLoginViewMenu(this, wkVBinding.otherView)
        // );
    }

    private void initTheme() {
        wkVBinding.loginBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.privacyPolicyTv.setTextColor(Theme.colorAccount);
        wkVBinding.userAgreementTv.setTextColor(Theme.colorAccount);
        wkVBinding.registerTv.setTextColor(Theme.colorAccount);
        wkVBinding.forgetPwdTv.setTextColor(Theme.colorAccount);
    }

    private void initAgreementCheckBox() {
        wkVBinding.checkbox.setResId(getContext(), R.mipmap.round_check2);
        wkVBinding.checkbox.setDrawBackground(true);
        wkVBinding.checkbox.setHasBorder(true);
        wkVBinding.checkbox.setStrokeWidth(AndroidUtilities.dp(1));
        wkVBinding.checkbox.setBorderColor(
                ContextCompat.getColor(getContext(), R.color.color999)
        );
        wkVBinding.checkbox.setSize(18);
        wkVBinding.checkbox.setColor(
                Theme.colorAccount,
                ContextCompat.getColor(getContext(), R.color.white)
        );
        wkVBinding.checkbox.setVisibility(View.VISIBLE);
        wkVBinding.checkbox.setEnabled(true);
        wkVBinding.checkbox.setChecked(false, true);
    }

    private void showLoginReasonIfNeeded() {
        int from = getIntent().getIntExtra("from", 0);
        if (from != 1 && from != 2) return;

        String content = from == 1
                ? getString(R.string.other_device_login)
                : getString(R.string.wk_ban);
        WKDialogUtils.getInstance().showSingleBtnDialog(
                this,
                "",
                content,
                getString(R.string.sure),
                index -> {
                }
        );
    }

    private void restoreLastPhoneAccount() {
        UserInfoEntity userInfo = WKConfig.getInstance().getUserInfo();
        if (userInfo == null || TextUtils.isEmpty(userInfo.phone)) return;

        wkVBinding.nameEt.setText(userInfo.phone);
        wkVBinding.nameEt.setSelection(userInfo.phone.length());

        if (!TextUtils.isEmpty(userInfo.zone)) {
            code = userInfo.zone;
            showZoneCode(code);
        }
    }

    @Override
    public boolean supportSlideBack() {
        return false;
    }

    @Override
    protected void initListener() {
        wkVBinding.myTv.setOnClickListener(v -> toggleAgreement());
        wkVBinding.checkbox.setOnClickListener(v -> toggleAgreement());
        wkVBinding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            wkVBinding.pwdEt.setTransformationMethod(
                    isChecked
                            ? HideReturnsTransformationMethod.getInstance()
                            : PasswordTransformationMethod.getInstance()
            );
            if (wkVBinding.pwdEt.getText() != null) {
                wkVBinding.pwdEt.setSelection(wkVBinding.pwdEt.getText().length());
            }
        });

        SingleClickUtil.onSingleClick(wkVBinding.loginBtn, v -> loginWithPhone());
        SingleClickUtil.onSingleClick(wkVBinding.googleLoginBtn, v -> startGoogleLogin());
        SingleClickUtil.onSingleClick(
                wkVBinding.registerTv,
                v -> startActivity(new Intent(this, WKRegisterActivity.class))
        );
        SingleClickUtil.onSingleClick(wkVBinding.chooseCodeTv, v -> {
            Intent intent = new Intent(this, ChooseAreaCodeActivity.class);
            intentActivityResultLauncher.launch(intent);
        });
        SingleClickUtil.onSingleClick(wkVBinding.forgetPwdTv, v -> {
            Intent intent = new Intent(this, WKResetLoginPwdActivity.class);
            intent.putExtra("canEditPhone", true);
            startActivity(intent);
        });

        registerOtherLoginResultEndpoint();
        initServerAddressActions();
        showBaseUrl();
    }

    private void toggleAgreement() {
        wkVBinding.checkbox.setChecked(!wkVBinding.checkbox.isChecked(), true);
    }

    private void loginWithPhone() {
        if (checkEditInputIsEmpty(wkVBinding.nameEt, R.string.name_not_null)) return;
        if (checkEditInputIsEmpty(wkVBinding.pwdEt, R.string.pwd_not_null)) return;

        String phone = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString().trim();
        String password = Objects.requireNonNull(wkVBinding.pwdEt.getText()).toString();

        if (DEFAULT_ZONE.equals(code) && phone.length() != 11) {
            showSingleBtnDialog(getString(R.string.phone_error));
            return;
        }
        if (!ensureAgreementAccepted()) return;
        if (password.length() < 6 || password.length() > 16) {
            showSingleBtnDialog(getString(R.string.pwd_length_error));
            return;
        }

        showLoginLoading(R.string.logging_in);
        loginPresenter.login(code + phone, password);
    }

    private void startGoogleLogin() {
        if (googleLoginInProgress || !ensureAgreementAccepted()) return;

        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.pwdEt);
        setGoogleLoginInProgress(true);

        googleLoginManager.signIn(resolveGoogleWebClientId(), new GoogleLoginManager.Callback() {
            @Override
            public void onSuccess(@NonNull String idToken, @NonNull String nonce) {
                if (!canHandleGoogleCallback()) return;
                showLoginLoading(R.string.google_login_loading);
                loginPresenter.googleLogin(idToken, nonce);
            }

            @Override
            public void onCancelled() {
                if (!canHandleGoogleCallback()) return;
                setGoogleLoginInProgress(false);
                showToast(getString(R.string.google_login_cancelled));
            }

            @Override
            public void onError(int messageRes, Throwable throwable) {
                if (!canHandleGoogleCallback()) return;
                setGoogleLoginInProgress(false);
                showSingleBtnDialog(getString(messageRes));
            }
        });
    }

    private boolean ensureAgreementAccepted() {
        if (wkVBinding.checkbox.isChecked()) return true;
        showSingleBtnDialog(getString(R.string.agree_auth_tips));
        return false;
    }

    private void showLoginLoading(int titleRes) {
        loadingPopup.show();
        loadingPopup.setTitle(getString(titleRes));
    }

    /**
     * 优先读取 google-services 插件生成的 default_web_client_id；
     * 没有生成时再读取 wklogin/values/strings.xml 中的 google_web_client_id。
     */
    private String resolveGoogleWebClientId() {
        int generatedClientIdRes = getResources().getIdentifier(
                "default_web_client_id",
                "string",
                getPackageName()
        );
        if (generatedClientIdRes != 0) {
            String generatedClientId = getString(generatedClientIdRes).trim();
            if (!TextUtils.isEmpty(generatedClientId)) return generatedClientId;
        }
        return getString(R.string.google_web_client_id).trim();
    }

    private void setGoogleLoginInProgress(boolean inProgress) {
        googleLoginInProgress = inProgress;
        wkVBinding.googleLoginBtn.setEnabled(!inProgress);
        wkVBinding.googleLoginBtn.setAlpha(inProgress ? 0.6f : 1f);
    }

    private boolean canHandleGoogleCallback() {
        return !isFinishing() && !isDestroyed();
    }

    private void registerOtherLoginResultEndpoint() {
        EndpointManager.getInstance().setMethod(OTHER_LOGIN_RESULT_ENDPOINT, object -> {
            if (!(object instanceof OtherLoginResultMenu)) return null;

            OtherLoginResultMenu menu = (OtherLoginResultMenu) object;
            UserInfoEntity userInfo = menu.getUserInfoEntity();
            if (menu.getCode() == 0 && userInfo != null) {
                loginResult(userInfo);
            } else if (userInfo != null) {
                setLoginFail(menu.getCode(), userInfo.uid, userInfo.phone);
            } else {
                hideLoading();
                showError(getString(R.string.google_login_failed));
            }
            return null;
        });
    }

    private void initServerAddressActions() {
        wkVBinding.baseUrlTv.setOnClickListener(v -> {
            if (wkappConfig == null || wkappConfig.can_modify_api_url == 0) return;

            String currentUrl = WKSharedPreferencesUtil.getInstance()
                    .getSP("api_base_url", "");
            WKDialogUtils.getInstance().showInputDialog(
                    this,
                    getString(R.string.update_api),
                    getString(R.string.update_api_content),
                    currentUrl,
                    getString(R.string.update_api_ip),
                    100,
                    text -> saveBaseUrl(text)
            );
        });
        wkVBinding.resetTv.setOnClickListener(v -> {
            WKSharedPreferencesUtil.getInstance().putSP("api_base_url", "");
            EndpointManager.getInstance().invoke("update_base_url", "");
            showBaseUrl();
        });
    }

    private void saveBaseUrl(String text) {
        if (TextUtils.isEmpty(text)) return;

        String url = text.trim();
        if (!url.toLowerCase(Locale.ROOT).startsWith("http")) {
            url = "http://" + url;
        }
        WKSharedPreferencesUtil.getInstance().putSP("api_base_url", url);
        EndpointManager.getInstance().invoke("update_base_url", url);
        showBaseUrl();
    }

    @Override
    protected void initData() {
        super.initData();
        WKCommonModel.getInstance().getAppConfig((resultCode, msg, config) -> {
            if (isFinishing() || isDestroyed()) return;
            wkappConfig = config;
            if (config != null && config.can_modify_api_url == 1) {
                wkVBinding.settingLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showBaseUrl() {
        String apiURL = WKSharedPreferencesUtil.getInstance().getSP("api_base_url");
        if (!TextUtils.isEmpty(apiURL)) {
            wkVBinding.baseUrlTv.setText(apiURL);
            wkVBinding.resetTv.setVisibility(View.VISIBLE);
        } else {
            wkVBinding.baseUrlTv.setText(R.string.update_api);
            wkVBinding.resetTv.setVisibility(View.GONE);
        }
    }

    private void showZoneCode(String zone) {
        if (TextUtils.isEmpty(zone)) return;
        String displayCode = zone.startsWith("00") && zone.length() > 2
                ? zone.substring(2)
                : zone;
        wkVBinding.codeTv.setText(String.format("+%s", displayCode));
    }

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {
        if (userInfoEntity == null) {
            hideLoading();
            showError(getString(R.string.google_login_failed));
            return;
        }

        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.pwdEt);
        hideLoading();

        if (TextUtils.isEmpty(userInfoEntity.name)) {
            startActivity(new Intent(this, PerfectUserInfoActivity.class));
            finish();
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            List<LoginMenu> menus = EndpointManager.getInstance()
                    .invokes(EndpointCategory.loginMenus, null);
            if (WKReader.isNotEmpty(menus)) {
                for (LoginMenu menu : menus) {
                    if (menu != null && menu.iMenuClick != null) {
                        menu.iMenuClick.onClick();
                    }
                }
            }
            finish();
        }, 200);
    }

    @Override
    public void setCountryCode(List<CountryCodeEntity> list) {
    }

    @Override
    public void setRegisterCodeSuccess(int code, String msg, int exist) {
    }

    @Override
    public void setLoginFail(int code, String uid, String phone) {
        hideLoading();
        if (TextUtils.isEmpty(uid)) {
            showError(getString(R.string.google_login_failed));
            return;
        }

        Intent intent = new Intent(this, LoginAuthActivity.class);
        intent.putExtra("phone", phone == null ? "" : phone);
        intent.putExtra("uid", uid);
        startActivity(intent);
    }

    @Override
    public void setSendCodeResult(int code, String msg) {
    }

    @Override
    public void setResetPwdResult(int code, String msg) {
    }

    @Override
    public Button getVerificationCodeBtn() {
        return null;
    }

    @Override
    public EditText getNameEt() {
        return null;
    }

    @Override
    public void showError(String msg) {
        showSingleBtnDialog(
                TextUtils.isEmpty(msg) ? getString(R.string.google_login_failed) : msg
        );
    }

    @Override
    public void hideLoading() {
        loadingPopup.dismiss();
        setGoogleLoginInProgress(false);
    }

    @Override
    public Context getContext() {
        return this;
    }

    private final ActivityResultLauncher<Intent> intentActivityResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null) {
                            return;
                        }
                        CountryCodeEntity entity = result.getData()
                                .getParcelableExtra("entity");
                        if (entity == null || TextUtils.isEmpty(entity.code)) return;
                        code = entity.code;
                        showZoneCode(code);
                    }
            );

    @Override
    public void finish() {
        cancelGoogleLogin();
        EndpointManager.getInstance().remove(OTHER_LOGIN_RESULT_ENDPOINT);
        super.finish();
    }

    @Override
    protected void onDestroy() {
        cancelGoogleLogin();
        super.onDestroy();
    }

    private void cancelGoogleLogin() {
        if (googleLoginManager != null) googleLoginManager.cancel();
    }
}
