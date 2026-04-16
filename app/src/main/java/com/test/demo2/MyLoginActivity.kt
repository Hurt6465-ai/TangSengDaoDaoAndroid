package com.test.demo2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chat.base.config.WKConfig
import com.chat.uikit.TabActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MyLoginActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var usernameInput: EditText

    private val baseUrl = "https://bbs.886.best/bridge"

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
            text = "登录 / 注册"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
        }

        val subTitle = TextView(this).apply {
            text = "先用邮箱注册登录进入 App"
            setTextColor(Color.parseColor("#666666"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(24))
        }

        emailInput = createInput("邮箱，例如 hurt@gmail.com")
        passwordInput = createInput("密码，至少 6 位")
        usernameInput = createInput("用户名（注册时可填）")

        val registerBtn = createButton("邮箱注册") {
            doRegister()
        }

        val loginBtn = createButton("邮箱登录") {
            doLogin()
        }

        val oldLoginBtn = createButton("继续使用原唐僧登录页") {
            Toast.makeText(this, "你现在已经有自定义邮箱登录了", Toast.LENGTH_SHORT).show()
        }

        val info = TextView(this).apply {
            text =
                "说明：\n" +
                "1. 先用邮箱注册和登录。\n" +
                "2. 注册成功后，后端会自动创建悟空用户并返回 token。\n" +
                "3. 这版先解决“进入 App”的问题。"
            setTextColor(Color.parseColor("#888888"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(24), 0, 0)
        }

        root.addView(title)
        root.addView(subTitle)
        root.addView(emailInput)
        root.addView(passwordInput)
        root.addView(usernameInput)
        root.addView(registerBtn)
        root.addView(loginBtn)
        root.addView(oldLoginBtn)
        root.addView(info)

        scrollView.addView(root)
        return scrollView
    }

    private fun createInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(dp(14), dp(12), dp(14), dp(12))

            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(12)
            layoutParams = lp
        }
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

    private fun doRegister() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val username = usernameInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("请填写邮箱和密码")
            return
        }

        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("username", username)
        }

        postJson(
            "$baseUrl/auth/register",
            json,
            onSuccess = { body ->
                handleLoginSuccess(body)
            },
            onError = { err ->
                toast(err)
            }
        )
    }

    private fun doLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("请填写邮箱和密码")
            return
        }

        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        postJson(
            "$baseUrl/auth/login",
            json,
            onSuccess = { body ->
                handleLoginSuccess(body)
            },
            onError = { err ->
                toast(err)
            }
        )
    }

    private fun handleLoginSuccess(body: String) {
        try {
            val obj = JSONObject(body)
            val token = obj.optString("token")
            val uid = obj.optString("uid")
            val username = obj.optString("username")

            if (token.isEmpty() || uid.isEmpty()) {
                toast("登录返回数据不完整")
                return
            }

            runOnUiThread {
                try {
                    WKConfig.getInstance().token = token
                    WKConfig.getInstance().uid = uid
                    WKConfig.getInstance().userInfo.uid = uid
                    WKConfig.getInstance().userInfo.name = username

                    toast("登录成功")
                    startActivity(Intent(this, TabActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    toast("保存登录态失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            toast("解析登录结果失败: ${e.message}")
        }
    }

    private fun postJson(
        url: String,
        json: JSONObject,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    onError("请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string().orEmpty()

                runOnUiThread {
                    if (response.isSuccessful) {
                        onSuccess(bodyString)
                    } else {
                        onError("请求失败: $bodyString")
                    }
                }
            }
        })
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
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
