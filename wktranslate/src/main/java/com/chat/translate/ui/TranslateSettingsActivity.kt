package com.chat.translate.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.chat.translate.R
import com.chat.translate.core.TranslateManager
import com.chat.translate.core.TranslateMode
import com.chat.translate.prefs.TranslatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TranslateSettingsActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var modeSpinner: Spinner
    private lateinit var aiEndpointEt: EditText
    private lateinit var aiKeyEt: EditText
    private lateinit var aiModelEt: EditText
    private lateinit var aiStatusTv: TextView
    private lateinit var machineEngineSpinner: Spinner
    private lateinit var machineUrlEt: EditText
    private lateinit var machineParserSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLightWindow()
        title = getString(R.string.wktranslate_title)
        setContentView(buildContentView())
        bindValues()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun applyLightWindow() {
        window.setBackgroundDrawableResource(android.R.color.white)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun buildContentView(): View {
        val scroll = ScrollView(this).apply {
            background = pageBackground()
            isFillViewport = true
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pageBackground()
            setPadding(dp(16), dp(18), dp(16), dp(28))
            clipToPadding = false
        }
        scroll.addView(
            root,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(titleView(getString(R.string.wktranslate_title)))
        root.addView(subtitleView(getString(R.string.wktranslate_ai_not_verified)))

        val modeCard = glassCard()
        modeCard.addView(section(getString(R.string.wktranslate_mode)))
        modeSpinner = spinner(
            listOf(
                getString(R.string.wktranslate_auto),
                getString(R.string.wktranslate_ai_translate),
                getString(R.string.wktranslate_machine_translate)
            )
        )
        modeCard.addView(modeSpinner, matchWrapLp())
        root.addView(modeCard, cardLp(top = 14))

        val aiCard = glassCard()
        aiCard.addView(section(getString(R.string.wktranslate_ai_translate)))
        aiEndpointEt = editText(getString(R.string.wktranslate_api_endpoint))
        aiKeyEt = editText(getString(R.string.wktranslate_api_key), password = true)
        aiModelEt = editText(getString(R.string.wktranslate_model))
        aiStatusTv = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(96, 96, 104))
            setPadding(dp(2), dp(8), dp(2), dp(10))
        }
        aiCard.addView(aiEndpointEt, fieldLp())
        aiCard.addView(aiKeyEt, fieldLp())
        aiCard.addView(aiModelEt, fieldLp())
        aiCard.addView(aiStatusTv, matchWrapLp())
        aiCard.addView(button(getString(R.string.wktranslate_test_ai)) { saveAiFields(); testAi() }, buttonLp())
        root.addView(aiCard, cardLp(top = 14))

        val machineCard = glassCard()
        machineCard.addView(section(getString(R.string.wktranslate_machine_translate)))
        machineEngineSpinner = spinner(
            listOf(
                getString(R.string.wktranslate_deeplx),
                getString(R.string.wktranslate_google),
                getString(R.string.wktranslate_custom_machine)
            )
        )
        machineParserSpinner = spinner(listOf(TranslatePrefs.PARSER_DEEPLX, TranslatePrefs.PARSER_GOOGLE, TranslatePrefs.PARSER_PLAIN))
        machineUrlEt = editText(getString(R.string.wktranslate_machine_url))
        machineCard.addView(machineEngineSpinner, fieldLp())
        machineCard.addView(machineUrlEt, fieldLp())
        machineCard.addView(label(getString(R.string.wktranslate_parser_type)))
        machineCard.addView(machineParserSpinner, fieldLp())
        machineCard.addView(button(getString(R.string.wktranslate_test_machine)) { saveMachineFields(); testMachine() }, buttonLp())
        root.addView(machineCard, cardLp(top = 14))

        root.addView(button(getString(R.string.wktranslate_save), primary = true) { saveAll(); toast(getString(R.string.wktranslate_saved)) }, saveButtonLp())
        return scroll
    }

    private fun bindValues() {
        modeSpinner.setSelection(when (TranslatePrefs.getMode(this)) {
            TranslateMode.AI -> 1
            TranslateMode.MACHINE -> 2
            TranslateMode.AUTO -> 0
        })
        val ai = TranslatePrefs.getAiConfig(this)
        aiEndpointEt.setText(ai.endpoint)
        aiKeyEt.setText(ai.apiKey)
        aiModelEt.setText(ai.model)
        updateAiStatus()
        val machine = TranslatePrefs.getMachineConfig(this)
        machineEngineSpinner.setSelection(when (machine.engine) {
            TranslatePrefs.ENGINE_GOOGLE -> 1
            TranslatePrefs.ENGINE_CUSTOM -> 2
            else -> 0
        })
        machineUrlEt.setText(machine.url)
        machineParserSpinner.setSelection(when (machine.parser) {
            TranslatePrefs.PARSER_GOOGLE -> 1
            TranslatePrefs.PARSER_PLAIN -> 2
            else -> 0
        })
    }

    private fun saveAll() {
        TranslatePrefs.setMode(this, when (modeSpinner.selectedItemPosition) {
            1 -> TranslateMode.AI
            2 -> TranslateMode.MACHINE
            else -> TranslateMode.AUTO
        })
        saveAiFields()
        saveMachineFields()
        updateAiStatus()
    }

    private fun saveAiFields() {
        TranslatePrefs.saveAiConfig(
            this,
            aiEndpointEt.text.toString(),
            aiKeyEt.text.toString(),
            aiModelEt.text.toString(),
            TranslatePrefs.AI_ADAPTER_DEEPSEEK
        )
    }

    private fun saveMachineFields() {
        val engine = when (machineEngineSpinner.selectedItemPosition) {
            1 -> TranslatePrefs.ENGINE_GOOGLE
            2 -> TranslatePrefs.ENGINE_CUSTOM
            else -> TranslatePrefs.ENGINE_DEEPLX
        }
        val parser = when (machineParserSpinner.selectedItemPosition) {
            1 -> TranslatePrefs.PARSER_GOOGLE
            2 -> TranslatePrefs.PARSER_PLAIN
            else -> TranslatePrefs.PARSER_DEEPLX
        }
        TranslatePrefs.saveMachineConfig(this, engine, machineUrlEt.text.toString(), parser)
    }

    private fun testAi() {
        saveAiFields()
        val ai = TranslatePrefs.getAiConfig(this)
        if (ai.endpoint.isBlank() || ai.apiKey.isBlank() || ai.model.isBlank()) {
            toast(getString(R.string.wktranslate_input_required))
            return
        }
        scope.launch {
            val result = TranslateManager.testAi(
                this@TranslateSettingsActivity,
                getString(R.string.wktranslate_test_text),
                getString(R.string.wktranslate_test_target_lang)
            )
            toast(if (result.success) getString(R.string.wktranslate_ai_test_success) else result.message.ifBlank { getString(R.string.wktranslate_translate_failed) })
            updateAiStatus()
        }
    }

    private fun testMachine() {
        saveMachineFields()
        scope.launch {
            val result = TranslateManager.testMachine(
                this@TranslateSettingsActivity,
                getString(R.string.wktranslate_test_text),
                getString(R.string.wktranslate_test_target_lang)
            )
            toast(if (result.success) getString(R.string.wktranslate_machine_test_success) else result.message.ifBlank { getString(R.string.wktranslate_translate_failed) })
        }
    }

    private fun updateAiStatus() {
        aiStatusTv.text = if (TranslatePrefs.isAiVerified(this)) {
            getString(R.string.wktranslate_ai_verified)
        } else {
            getString(R.string.wktranslate_ai_not_verified)
        }
    }

    private fun titleView(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(28, 28, 32))
        setPadding(dp(2), dp(8), dp(2), dp(4))
    }

    private fun subtitleView(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(105, 105, 116))
        setPadding(dp(2), 0, dp(2), dp(4))
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(34, 34, 40))
        setPadding(dp(2), 0, dp(2), dp(10))
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(102, 102, 112))
        setPadding(dp(2), dp(8), dp(2), dp(5))
    }

    private fun editText(hint: String, password: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        textSize = 15f
        setTextColor(Color.rgb(28, 28, 32))
        setHintTextColor(Color.rgb(150, 150, 160))
        background = fieldBackground()
        setPadding(dp(12), 0, dp(12), 0)
        inputType = if (password) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
    }

    private fun spinner(items: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@TranslateSettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
        background = fieldBackground()
        setPadding(dp(6), 0, dp(6), 0)
    }

    private fun button(text: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.WHITE else Color.rgb(36, 36, 42))
        background = if (primary) primaryButtonBackground() else glassButtonBackground()
        stateListAnimator = null
        minHeight = 0
        minimumHeight = 0
        includeFontPadding = false
        setPadding(dp(12), 0, dp(12), dp(1))
        setOnClickListener { onClick() }
    }

    private fun glassCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = frostedCardBackground()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = dp(5).toFloat()
            translationZ = dp(1).toFloat()
        }
    }

    private fun pageBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(255, 249, 253),
                Color.rgb(244, 248, 255),
                Color.rgb(252, 250, 255)
            )
        )
    }

    private fun frostedCardBackground(): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            Color.argb(226, 255, 255, 255),
            Color.argb(188, 255, 255, 255),
            Color.argb(206, 250, 252, 255)
        )).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.argb(120, 255, 255, 255))
        }
    }

    private fun fieldBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(13).toFloat()
            setColor(Color.argb(188, 255, 255, 255))
            setStroke(dp(1), Color.argb(120, 214, 221, 232))
        }
    }

    private fun glassButtonBackground(): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            Color.argb(218, 255, 255, 255),
            Color.argb(190, 250, 252, 255)
        )).apply {
            cornerRadius = dp(15).toFloat()
            setStroke(dp(1), Color.argb(120, 214, 221, 232))
        }
    }

    private fun primaryButtonBackground(): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            Color.rgb(255, 94, 167),
            Color.rgb(128, 118, 255)
        )).apply {
            cornerRadius = dp(18).toFloat()
        }
    }

    private fun matchWrapLp(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fieldLp(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply {
            topMargin = dp(8)
        }
    }

    private fun buttonLp(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(44)
        ).apply {
            topMargin = dp(10)
        }
    }

    private fun saveButtonLp(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply {
            topMargin = dp(18)
            leftMargin = dp(2)
            rightMargin = dp(2)
        }
    }

    private fun cardLp(top: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_FROM = "from"
        fun start(context: Context, from: String = "chat") {
            val intent = Intent(context, TranslateSettingsActivity::class.java).putExtra(EXTRA_FROM, from)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
