package com.test.demo2

import android.content.Intent
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.chat.base.WKBaseApplication
import com.chat.base.base.WKBaseActivity
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.ui.components.NormalClickableContent
import com.chat.base.ui.components.NormalClickableSpan
import com.chat.base.utils.WKDialogUtils
import com.chat.login.ui.PerfectUserInfoActivity
import com.chat.uikit.TabActivity
import com.test.ts.R
import com.test.ts.databinding.ActivityMainBinding
import com.xinbida.wukongim.WKIM

class MainActivity : WKBaseActivity<ActivityMainBinding>() {

    override fun getViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        super.initView()
        val isShowDialog = WKSharedPreferencesUtil.getInstance().getBoolean("show_agreement_dialog")
        if (isShowDialog) {
            showDialog()
        } else {
            gotoApp()
        }
    }

    private fun gotoApp() {
        val token = WKConfig.getInstance().token

        if (!TextUtils.isEmpty(token)) {
            if (TextUtils.isEmpty(WKConfig.getInstance().userInfo.name)) {
                startActivity(Intent(this@MainActivity, PerfectUserInfoActivity::class.java))
            } else {
                val publicRSAKey = WKIM.getInstance().cmdManager.rsaPublicKey
                if (TextUtils.isEmpty(publicRSAKey)) {
                    openCustomLogin()
                } else {
                    startActivity(Intent(this@MainActivity, TabActivity::class.java))
                }
            }
        } else {
            openCustomLogin()
        }

        finish()
    }

    /**
     * 不直接依赖 MyLoginActivity，先保证项目能编译。
     * 等你创建了 MyLoginActivity 后，会自动跳过去。
     */
    private fun openCustomLogin() {
        try {
            val clazz = Class.forName("${packageName}.MyLoginActivity")
            val intent = Intent(this@MainActivity, clazz)
            intent.putExtra("from", getIntent().getIntExtra("from", 0))
            startActivity(intent)
        } catch (e: ClassNotFoundException) {
            Toast.makeText(
                this,
                "请先创建 MyLoginActivity",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "打开自定义登录页失败: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDialog() {
        val content = getString(R.string.dialog_content)
        val linkSpan = SpannableStringBuilder()
        linkSpan.append(content)

        val userAgreementText = getString(R.string.main_user_agreement)
        val privacyPolicyText = getString(R.string.main_privacy_policy)

        val userAgreementIndex = content.indexOf(userAgreementText)
        if (userAgreementIndex >= 0) {
            linkSpan.setSpan(
                NormalClickableSpan(
                    true,
                    ContextCompat.getColor(this, R.color.blue),
                    NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""),
                    object : NormalClickableSpan.IClick {
                        override fun onClick(view: View) {
                            showWebView(WKApiConfig.baseWebUrl + "user_agreement.html")
                        }
                    }
                ),
                userAgreementIndex,
                userAgreementIndex + userAgreementText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val privacyPolicyIndex = content.indexOf(privacyPolicyText)
        if (privacyPolicyIndex >= 0) {
            linkSpan.setSpan(
                NormalClickableSpan(
                    true,
                    ContextCompat.getColor(this, R.color.blue),
                    NormalClickableContent(NormalClickableContent.NormalClickableTypes.Other, ""),
                    object : NormalClickableSpan.IClick {
                        override fun onClick(view: View) {
                            showWebView(WKApiConfig.baseWebUrl + "privacy_policy.html")
                        }
                    }
                ),
                privacyPolicyIndex,
                privacyPolicyIndex + privacyPolicyText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        WKDialogUtils.getInstance().showDialog(
            this,
            getString(R.string.dialog_title),
            linkSpan,
            false,
            getString(R.string.disagree),
            getString(R.string.agree),
            0,
            0
        ) { index ->
            if (index == 1) {
                WKSharedPreferencesUtil.getInstance().putBoolean("show_agreement_dialog", false)
                WKBaseApplication.getInstance().init(
                    WKBaseApplication.getInstance().packageName,
                    WKBaseApplication.getInstance().application
                )
                gotoApp()
            } else {
                finish()
            }
        }
    }
}
