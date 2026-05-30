package com.chat.uikit.setting;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.utils.language.WKLanguageType;
import com.chat.base.utils.language.WKMultiLanguageUtil;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActLanguageLayoutBinding;

/**
 * 多语言
 */
public class WKLanguageActivity extends WKBaseActivity<ActLanguageLayoutBinding> {
    int selectedLanguage = 0;

    @Override
    protected ActLanguageLayoutBinding getViewBinding() {
        return ActLanguageLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.language);
    }

    @Override
    protected String getRightBtnText(Button titleRightBtn) {
        return getString(R.string.str_save);
    }

    @Override
    protected void rightButtonClick() {
        super.rightButtonClick();
        WKMultiLanguageUtil.getInstance().updateLanguage(selectedLanguage);
        EndpointManager.getInstance().invoke("main_show_home_view", 0);
        finish();
    }

    @Override
    protected void initView() {
        selectedLanguage = WKMultiLanguageUtil.getInstance().getLanguageType();
        setSelectedLanguage();
    }

    @Override
    protected void initListener() {
        wkVBinding.autoLayout.setOnClickListener(v -> {
            selectedLanguage = WKLanguageType.LANGUAGE_FOLLOW_SYSTEM;
            setSelectedLanguage();
        });
        wkVBinding.simplifiedChineseLayout.setOnClickListener(v -> {
            selectedLanguage = WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED;
            setSelectedLanguage();
        });
        wkVBinding.englishLayout.setOnClickListener(v -> {
            selectedLanguage = WKLanguageType.LANGUAGE_EN;
            setSelectedLanguage();
        });
        wkVBinding.burmeseLayout.setOnClickListener(v -> {
            selectedLanguage = WKLanguageType.LANGUAGE_MYANMAR;
            setSelectedLanguage();
        });
    }

    private void setSelectedLanguage() {
        wkVBinding.autoIv.setVisibility(selectedLanguage == WKLanguageType.LANGUAGE_FOLLOW_SYSTEM ? View.VISIBLE : View.INVISIBLE);
        wkVBinding.englishIv.setVisibility(selectedLanguage == WKLanguageType.LANGUAGE_EN ? View.VISIBLE : View.INVISIBLE);
        wkVBinding.simplifiedChineseIv.setVisibility(selectedLanguage == WKLanguageType.LANGUAGE_CHINESE_SIMPLIFIED ? View.VISIBLE : View.INVISIBLE);
        wkVBinding.burmeseIv.setVisibility(selectedLanguage == WKLanguageType.LANGUAGE_MYANMAR ? View.VISIBLE : View.INVISIBLE);
    }
}
