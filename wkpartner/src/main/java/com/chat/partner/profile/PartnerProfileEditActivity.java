package com.chat.partner.profile;

import android.content.Intent;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileEditBinding;
import com.chat.uikit.user.MyInfoActivity;

/**
 * 独立语伴资料编辑入口。
 * 第一版先承接到唐僧叨叨原资料页；背景墙、照片墙、WebP 压缩下一包接入这里。
 */
public class PartnerProfileEditActivity extends WKBaseActivity<ActPartnerProfileEditBinding> {
    @Override
    protected ActPartnerProfileEditBinding getViewBinding() {
        return ActPartnerProfileEditBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.partner_edit_profile);
    }

    @Override
    protected void initListener() {
        wkVBinding.basicInfoBtn.setOnClickListener(v -> startActivity(new Intent(this, MyInfoActivity.class)));
    }
}
