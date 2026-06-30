package com.chat.uikit.setting;

import android.content.Intent;
import android.widget.TextView;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActSecurityPrivacyLayoutBinding;

/**
 * 安全与隐藏。
 * 目前承载隐私政策、用户协议等安全合规入口。
 */
public class SecurityPrivacyActivity extends WKBaseActivity<ActSecurityPrivacyLayoutBinding> {
    @Override
    protected ActSecurityPrivacyLayoutBinding getViewBinding() {
        return ActSecurityPrivacyLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.security_and_privacy);
    }

    @Override
    protected void initListener() {
        SingleClickUtil.onSingleClick(wkVBinding.privacyPolicyLayout, view -> openWeb(WKApiConfig.baseWebUrl + "privacy_policy.html"));
        SingleClickUtil.onSingleClick(wkVBinding.userAgreementLayout, view -> openWeb(WKApiConfig.baseWebUrl + "user_agreement.html"));
    }

    private void openWeb(String url) {
        Intent intent = new Intent(this, WKWebViewActivity.class);
        intent.putExtra("url", url);
        startActivity(intent);
    }
}
