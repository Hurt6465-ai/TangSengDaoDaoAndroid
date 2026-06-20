package com.chat.login.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointCategory;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.LoginMenu;
import com.chat.base.entity.UserInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.SoftKeyboardUtils;
import com.chat.base.utils.WKReader;
import com.chat.login.R;
import com.chat.login.databinding.ActRegisterLayoutBinding;
import com.chat.login.entity.CountryCodeEntity;
import com.chat.login.service.LoginContract;
import com.chat.login.service.LoginPresenter;

import java.util.List;
import java.util.Objects;

/**
 * 2020-06-19 15:42
 * 注册
 */
public class WKRegisterActivity extends WKBaseActivity<ActRegisterLayoutBinding> implements LoginContract.LoginView {
    private String code = "0086";
    private LoginPresenter presenter;
    private WKAPPConfig appConfig;

    @Override
    protected ActRegisterLayoutBinding getViewBinding() {
        return ActRegisterLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        presenter = new LoginPresenter(this);
    }

    @Override
    protected void initView() {
        wkVBinding.getVCodeBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.registerBtn.getBackground().setTint(Theme.colorAccount);
        wkVBinding.privacyPolicyTv.setTextColor(Theme.colorAccount);
        wkVBinding.userAgreementTv.setTextColor(Theme.colorAccount);
        wkVBinding.loginTv.setTextColor(Theme.colorAccount);
        wkVBinding.authCheckBox.setResId(getContext(), R.mipmap.round_check2);
        wkVBinding.authCheckBox.setDrawBackground(true);
        wkVBinding.authCheckBox.setHasBorder(true);
        wkVBinding.authCheckBox.setStrokeWidth(AndroidUtilities.dp(1));
        wkVBinding.authCheckBox.setBorderColor(ContextCompat.getColor(getContext(), R.color.color999));
        wkVBinding.authCheckBox.setSize(18);
        wkVBinding.authCheckBox.setColor(Theme.colorAccount, ContextCompat.getColor(getContext(), R.color.white));
        wkVBinding.authCheckBox.setVisibility(View.VISIBLE);
        wkVBinding.authCheckBox.setEnabled(true);
        wkVBinding.authCheckBox.setChecked(false, true);
        hideSmsCodeViews();

        wkVBinding.privacyPolicyTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html"));
        wkVBinding.userAgreementTv.setOnClickListener(v -> showWebView(WKApiConfig.baseWebUrl + "user_agreement.html"));
        wkVBinding.registerAppTv.setText(String.format(getString(R.string.register_app), getString(R.string.app_name)));
        wkVBinding.nameEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkStatus();
            }
        });
        wkVBinding.pwdEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                checkStatus();
            }
        });
        wkVBinding.loginTv.setOnClickListener(v -> startActivity(new Intent(this, WKLoginActivity.class)));
        wkVBinding.chooseCodeTv.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChooseAreaCodeActivity.class);
            intentActivityResultLauncher.launch(intent);
        });
        wkVBinding.registerBtn.setOnClickListener(v -> {
            if (!wkVBinding.authCheckBox.isChecked()) {
                showToast(R.string.agree_auth_tips);
                return;
            }

            String phone = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString();
            String pwd = Objects.requireNonNull(wkVBinding.pwdEt.getText()).toString();
            String inviteCode = Objects.requireNonNull(wkVBinding.inviteCodeTv.getText()).toString();
            if (!TextUtils.isEmpty(phone) && !TextUtils.isEmpty(pwd)) {
                if (pwd.length() < 6 || pwd.length() > 16) {
                    showSingleBtnDialog(getString(R.string.pwd_length_error));
                } else {
                    if (appConfig != null && appConfig.register_invite_on == 1 && TextUtils.isEmpty(inviteCode)) {
                        showSingleBtnDialog(getString(R.string.invite_code_not_null));
                        return;
                    }
                    loadingPopup.show();
                    // 手机号无验证码注册：验证码 code 固定传空字符串。后端 /user/register 必须同步放开 code 校验。
                    presenter.registerApp("", code, "", phone, pwd, inviteCode);
                }
            }
        });
        wkVBinding.getVCodeBtn.setOnClickListener(null);

        wkVBinding.myTv.setOnClickListener(view1 -> wkVBinding.authCheckBox.setChecked(!wkVBinding.authCheckBox.isChecked(), true));
        wkVBinding.authCheckBox.setOnClickListener(view1 -> wkVBinding.authCheckBox.setChecked(!wkVBinding.authCheckBox.isChecked(), true));
    }

    @Override
    protected void initListener() {
        wkVBinding.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                wkVBinding.pwdEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            } else {
                wkVBinding.pwdEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            wkVBinding.pwdEt.setSelection(Objects.requireNonNull(wkVBinding.pwdEt.getText()).length());
        });
    }

    @Override
    protected void initData() {
        WKCommonModel.getInstance().getAppConfig((code, msg, wkappConfig) -> {
            if (code == HttpResponseCode.success) {
                appConfig = wkappConfig;
                if (appConfig != null && appConfig.register_invite_on == 1) {
                    wkVBinding.inviteCodeTv.setHint(R.string.input_invite_code_must);
                    wkVBinding.inviteLayout.setVisibility(View.VISIBLE);
                    wkVBinding.inviteLineView.setVisibility(View.VISIBLE);
                } else {
                    wkVBinding.inviteCodeTv.setHint(R.string.input_invite_code_not_must);
                    wkVBinding.inviteLayout.setVisibility(View.GONE);
                    wkVBinding.inviteLineView.setVisibility(View.GONE);
                }
            } else {
                showToast(msg);
            }
        });
    }

    private void checkStatus() {
        String phone = Objects.requireNonNull(wkVBinding.nameEt.getText()).toString();
        String pwd = Objects.requireNonNull(wkVBinding.pwdEt.getText()).toString();
        if (!TextUtils.isEmpty(phone) && !TextUtils.isEmpty(pwd)) {
            wkVBinding.registerBtn.setAlpha(1f);
            wkVBinding.registerBtn.setEnabled(true);
        } else {
            wkVBinding.registerBtn.setAlpha(0.2f);
            wkVBinding.registerBtn.setEnabled(false);
        }
    }

    /**
     * 注册不再需要短信验证码。
     * 这里不依赖新增 XML id，直接用现有 verfiEt/getVCodeBtn 找到验证码所在行并隐藏，
     * 避免只替换 Java 文件时因为 ViewBinding 字段不存在导致编译失败。
     */
    private void hideSmsCodeViews() {
        wkVBinding.verfiEt.setText("");
        wkVBinding.verfiEt.setVisibility(View.GONE);
        wkVBinding.getVCodeBtn.setEnabled(false);
        wkVBinding.getVCodeBtn.setAlpha(0f);
        wkVBinding.getVCodeBtn.setVisibility(View.GONE);
        wkVBinding.getVCodeBtn.setOnClickListener(null);

        View verifyLayout = (View) wkVBinding.verfiEt.getParent();
        if (verifyLayout != null) {
            ViewGroup parent = (ViewGroup) verifyLayout.getParent();
            if (parent != null) {
                int index = parent.indexOfChild(verifyLayout);
                verifyLayout.setVisibility(View.GONE);
                if (index >= 0 && index + 1 < parent.getChildCount()) {
                    parent.getChildAt(index + 1).setVisibility(View.GONE);
                }
            } else {
                verifyLayout.setVisibility(View.GONE);
            }
        }
    }


    ActivityResultLauncher<Intent> intentActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        //此处是跳转的result回调方法
        if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
            CountryCodeEntity entity = result.getData().getParcelableExtra("entity");
            assert entity != null;
            code = entity.code;
            String codeName = code.substring(2);
            wkVBinding.codeTv.setText(String.format("+%s", codeName));
        }
    });

    @Override
    public void loginResult(UserInfoEntity userInfoEntity) {
        loadingPopup.dismiss();
        SoftKeyboardUtils.getInstance().hideInput(this, wkVBinding.pwdEt);
        hideLoading();

        if (TextUtils.isEmpty(userInfoEntity.name)) {
            Intent intent = new Intent(this, PerfectUserInfoActivity.class);
            startActivity(intent);
            finish();
        } else {
            new Handler(Objects.requireNonNull(Looper.myLooper())).postDelayed(() -> {
                List<LoginMenu> list = EndpointManager.getInstance().invokes(EndpointCategory.loginMenus, null);
                if (WKReader.isNotEmpty(list)) {
                    for (LoginMenu menu : list) {
                        if (menu.iMenuClick != null) menu.iMenuClick.onClick();
                    }
                }
                finish();
            }, 500);
        }
    }

    @Override
    public void setCountryCode(List<CountryCodeEntity> list) {

    }

    @Override
    public void setRegisterCodeSuccess(int code, String msg, int exist) {
        // 手机号无验证码注册不再发送短信验证码，此回调保留为空实现以满足 LoginView 接口。
    }

    @Override
    public void setLoginFail(int code, String uid, String phone) {

    }

    @Override
    public void setSendCodeResult(int code, String msg) {

    }

    @Override
    public void setResetPwdResult(int code, String msg) {
    }

    @Override
    public Button getVerificationCodeBtn() {
        return wkVBinding.getVCodeBtn;
    }

    @Override
    public EditText getNameEt() {
        return wkVBinding.nameEt;
    }

    @Override
    public void showError(String msg) {
        showSingleBtnDialog(msg);
    }

    @Override
    public void hideLoading() {
        loadingPopup.dismiss();
    }


    @Override
    public Context getContext() {
        return this;
    }

}
