package com.chat.login.service;

import android.content.Context;
import android.widget.Button;
import android.widget.EditText;

import com.chat.base.base.WKBasePresenter;
import com.chat.base.base.WKBaseView;
import com.chat.base.entity.UserInfoEntity;
import com.chat.login.entity.CountryCodeEntity;

import java.util.List;

/**
 * 登录页面契约。
 */
public final class LoginContract {

    private LoginContract() {
    }

    public interface LoginPresenter extends WKBasePresenter {
        void login(String name, String pwd);

        /**
         * 使用 Credential Manager 获取到的 Google ID Token 登录。
         * ID Token 和 nonce 必须交给业务后端验证，客户端不能自行认定登录成功。
         */
        void googleLogin(String idToken, String nonce);

        void sendLoginAuthVerificationCode(String uid);

        void getCountryCode();

        void registerCode(String zone, String phone);

        void forgetPwd(String zone, String phone);

        void registerApp(
                String code,
                String zone,
                String name,
                String phone,
                String password,
                String inviteCode
        );

        void checkLoginAuth(String uid, String code);

        void resetPwd(String zone, String phone, String code, String pwd);
    }

    public interface LoginView extends WKBaseView {
        void loginResult(UserInfoEntity userInfoEntity);

        void setCountryCode(List<CountryCodeEntity> list);

        void setRegisterCodeSuccess(int code, String msg, int exist);

        void setLoginFail(int code, String uid, String phone);

        void setSendCodeResult(int code, String msg);

        void setResetPwdResult(int code, String msg);

        Button getVerificationCodeBtn();

        EditText getNameEt();

        Context getContext();
    }
}
