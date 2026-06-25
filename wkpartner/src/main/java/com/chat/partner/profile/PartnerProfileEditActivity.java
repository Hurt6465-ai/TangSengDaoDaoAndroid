package com.chat.partner.profile;

import android.content.Intent;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.partner.R;
import com.chat.partner.databinding.ActPartnerProfileEditBinding;
import com.chat.uikit.user.MyInfoActivity;

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
