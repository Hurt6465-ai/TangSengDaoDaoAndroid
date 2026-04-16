package com.test.demo2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chat.login.ui.WKLoginActivity

class MyLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
    }

    private fun buildContentView(): ScrollView {
        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(Color.WHITE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = "欢迎使用"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
        }

        val subTitle = TextView(this).apply {
            text = "先用自定义登录页替换默认唐僧注册页"
            setTextColor(Color.parseColor("#666666"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(28))
        }

        val googleBtn = createButton("Google 登录（占位）") {
            Toast.makeText(
                this,
                "这里先留占位，后面接 Google 登录和你的后端接口",
                Toast.LENGTH_LONG
            ).show()
        }

        val emailBtn = createButton("邮箱登录（占位）") {
            Toast.makeText(
                this,
                "这里先留占位，后面接邮箱登录和你的后端接口",
                Toast.LENGTH_LONG
            ).show()
        }

        val testBtn = createButton("继续使用原唐僧登录页") {
            val intent = Intent(this, WKLoginActivity::class.java)
            intent.putExtra("from", getIntent().getIntExtra("from", 0))
            startActivity(intent)
            finish()
        }

        val info = TextView(this).apply {
            text =
                "说明：\n" +
                "1. 这版先把默认登录入口替换掉。\n" +
                "2. Google / 邮箱按钮先做占位，不会真的登录。\n" +
                "3. 你后面把 Google 登录成功后的 token 发给自己的后端，\n" +
                "   再由后端自动创建或更新唐僧 / 悟空 / NodeBB 用户。"
            setTextColor(Color.parseColor("#888888"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(24), 0, 0)
        }

        root.addView(title)
        root.addView(subTitle)
        root.addView(googleBtn)
        root.addView(emailBtn)
        root.addView(testBtn)
        root.addView(info)

        scrollView.addView(root)
        return scrollView
    }

    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2F80ED"))

            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            lp.topMargin = dp(14)
            layoutParams = lp

            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
