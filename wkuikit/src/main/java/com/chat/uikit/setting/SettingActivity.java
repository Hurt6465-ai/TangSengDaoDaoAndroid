package com.chat.uikit.setting;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.chat.base.act.WKWebViewActivity;
import com.chat.base.base.WKBaseActivity;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.entity.ChatBgItemMenu;
import com.chat.base.ui.Theme;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.DataCleanManager;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKLogUtils;
import com.chat.base.utils.singleclick.SingleClickUtil;
import com.chat.uikit.R;
import com.chat.uikit.WKUIKitApplication;
import com.chat.uikit.databinding.ActSettingLayoutBinding;
import com.chat.uikit.user.service.UserModel;
import com.xinbida.wukongim.entity.WKChannelType;

/**
 * 设置页面。
 * 二开版本只保留通用系统设置入口。
 */
public class SettingActivity extends WKBaseActivity<ActSettingLayoutBinding> {
    private String str;

    @Override
    protected ActSettingLayoutBinding getViewBinding() {
        return ActSettingLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.setting);
    }

    @Override
    protected void initPresenter() {
        wkVBinding.refreshLayout.setEnableOverScrollDrag(true);
        wkVBinding.refreshLayout.setEnableLoadMore(false);
        wkVBinding.refreshLayout.setEnableRefresh(false);
    }

    @Override
    protected void initView() {
        getCacheSize();
        EndpointManager.getInstance().invoke("set_chat_bg_view", new ChatBgItemMenu(this, wkVBinding.chatBgLayout, "", WKChannelType.PERSONAL));
    }

    @Override
    protected void initListener() {
        String wkThemePref = Theme.getTheme();
        if (wkThemePref.equals(Theme.DARK_MODE)) {
            wkVBinding.darkStatusTv.setText(R.string.enabled);
        } else {
            wkVBinding.darkStatusTv.setText(R.string.disabled);
        }

        SingleClickUtil.onSingleClick(wkVBinding.msgNoticesLayout, view -> startActivity(new Intent(this, MsgNoticesSettingActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.languageLayout, view -> startActivity(new Intent(this, WKLanguageActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.darkLayout, view -> startActivity(new Intent(this, WKThemeSettingActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.fontSizeLayout, view -> startActivity(new Intent(this, WKSetFontSizeActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.safetyLayout, view -> startActivity(new Intent(this, SecurityPrivacyActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.blacklistLayout, view -> startActivity(new Intent(this, BlacklistActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.aboutLayout, view -> startActivity(new Intent(this, WKAboutActivity.class)));
        SingleClickUtil.onSingleClick(wkVBinding.errorLogsLayout, view -> startActivity(new Intent(this, ErrorLogsActivity.class)));

        wkVBinding.clearImgCacheLayout.setOnClickListener(v -> showDialog(getString(R.string.clear_img_cache_tips), index -> {
            if (index == 1) {
                DataCleanManager.clearAllCache(SettingActivity.this);
                str = "0.00M";
                wkVBinding.imageCacheTv.setText(str);
            }
        }));

        wkVBinding.destroyAccountTv.setOnClickListener(v -> startActivity(new Intent(this, AccountDestroyActivity.class)));

        wkVBinding.loginOutTv.setOnClickListener(v -> WKDialogUtils.getInstance().showDialog(this, getString(R.string.login_out), getString(R.string.login_out_dialog), true, "", getString(R.string.login_out), 0, 0, index -> {
            if (index == 1) {
                UserModel.getInstance().quit(null);
                WKUIKitApplication.getInstance().exitLogin(0);
            }
        }));

        SingleClickUtil.onSingleClick(wkVBinding.thirdShareLayout, view -> {
            Intent intent = new Intent(this, WKWebViewActivity.class);
            intent.putExtra("url", WKApiConfig.baseWebUrl + "sdkinfo.html");
            startActivity(intent);
        });

        WKCommonModel.getInstance().getAppNewVersion(false, version -> {
            if (version != null && !TextUtils.isEmpty(version.download_url)) {
                wkVBinding.newVersionIv.setVisibility(View.VISIBLE);
            } else {
                wkVBinding.newVersionIv.setVisibility(View.GONE);
            }
        });
    }

    // 获取缓存大小
    private void getCacheSize() {
        new Thread(() -> {
            try {
                str = DataCleanManager.getTotalCacheSize(SettingActivity.this);
                if (str.equalsIgnoreCase("0.0Byte")) {
                    str = "0.00M";
                }
                AndroidUtilities.runOnUIThread(() -> wkVBinding.imageCacheTv.setText(str));
            } catch (Exception e) {
                WKLogUtils.e("获取图片缓存大小错误");
            }
        }).start();
    }
}
