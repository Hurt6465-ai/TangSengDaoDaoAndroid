package com.chat.uikit.setting;

import android.os.CountDownTimer;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.utils.WKDialogUtils;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.databinding.ActAccountDestroyLayoutBinding;
import com.chat.uikit.user.service.UserModel;

/**
 * 注销账号。
 * 后端接口保持即时注销：发送验证码 -> 校验验证码 -> is_destroy=1 并退出全部设备。
 */
public class AccountDestroyActivity extends WKBaseActivity<ActAccountDestroyLayoutBinding> {
    private CountDownTimer countDownTimer;

    @Override
    protected ActAccountDestroyLayoutBinding getViewBinding() {
        return ActAccountDestroyLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.destroy_account);
    }

    @Override
    protected void initListener() {
        wkVBinding.sendCodeTv.setOnClickListener(v -> sendDestroyCode());
        wkVBinding.destroyAccountBtn.setOnClickListener(v -> confirmDestroy());
    }

    private void sendDestroyCode() {
        wkVBinding.sendCodeTv.setEnabled(false);
        UserModel.getInstance().sendDestroyCode((code, msg) -> {
            if (code == HttpResponseCode.success || code == 200 || code == 0) {
                showToast(getString(R.string.destroy_code_sent));
                startCountDown();
            } else {
                wkVBinding.sendCodeTv.setEnabled(true);
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.operation_failed) : msg);
            }
        });
    }

    private void startCountDown() {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                wkVBinding.sendCodeTv.setText(getString(R.string.resend_after_seconds, millisUntilFinished / 1000));
                wkVBinding.sendCodeTv.setTextColor(ContextCompat.getColor(AccountDestroyActivity.this, R.color.color999));
            }

            @Override
            public void onFinish() {
                wkVBinding.sendCodeTv.setEnabled(true);
                wkVBinding.sendCodeTv.setText(R.string.send_destroy_code);
                wkVBinding.sendCodeTv.setTextColor(ContextCompat.getColor(AccountDestroyActivity.this, R.color.colorAccent));
            }
        };
        countDownTimer.start();
    }

    private void confirmDestroy() {
        String code = wkVBinding.codeEt.getText() == null ? "" : wkVBinding.codeEt.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            showToast(getString(R.string.input_destroy_code));
            return;
        }
        WKDialogUtils.getInstance().showDialog(this, getString(R.string.destroy_account), getString(R.string.destroy_account_confirm_tips), true, "", getString(R.string.confirm_destroy_account), 0, ContextCompat.getColor(this, R.color.red), index -> {
            if (index == 1) destroyAccount(code);
        });
    }

    private void destroyAccount(String code) {
        UserModel.getInstance().destroyAccount(code, (resultCode, msg) -> {
            if (resultCode == HttpResponseCode.success || resultCode == 200 || resultCode == 0) {
                showToast(getString(R.string.destroy_account_success));
                WKUIKitApplication.getInstance().exitLogin(0);
            } else {
                showToast(TextUtils.isEmpty(msg) ? getString(R.string.operation_failed) : msg);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) countDownTimer.cancel();
        super.onDestroy();
    }
}
