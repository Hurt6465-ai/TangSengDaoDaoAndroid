package com.chat.translate.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
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
import kotlinx.coroutines.withContext

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
        title = getString(R.string.wktranslate_title)
        setContentView(buildContentView())
        bindValues()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildContentView(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(32))
        }
        scroll.addView(root)
        root.addView(section(getString(R.string.wktranslate_mode)))
        modeSpinner = spinner(listOf(getString(R.string.wktranslate_auto), getString(R.string.wktranslate_ai_translate), getString(R.string.wktranslate_machine_translate)))
        root.addView(modeSpinner)
        root.addView(section(getString(R.string.wktranslate_ai_translate)))
        aiEndpointEt = editText(getString(R.string.wktranslate_api_endpoint))
        aiKeyEt = editText(getString(R.string.wktranslate_api_key), password = true)
        aiModelEt = editText(getString(R.string.wktranslate_model))
        aiStatusTv = TextView(this).apply { textSize = 13f; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(aiEndpointEt)
        root.addView(aiKeyEt)
        root.addView(aiModelEt)
        root.addView(aiStatusTv)
        root.addView(button(getString(R.string.wktranslate_test_ai)) { saveAiFields(); testAi() })
        root.addView(section(getString(R.string.wktranslate_machine_translate)))
        machineEngineSpinner = spinner(listOf(getString(R.string.wktranslate_deeplx), getString(R.string.wktranslate_google), getString(R.string.wktranslate_custom_machine)))
        machineParserSpinner = spinner(listOf(TranslatePrefs.PARSER_DEEPLX, TranslatePrefs.PARSER_GOOGLE, TranslatePrefs.PARSER_PLAIN))
        machineUrlEt = editText(getString(R.string.wktranslate_machine_url))
        root.addView(machineEngineSpinner)
        root.addView(machineUrlEt)
        root.addView(label(getString(R.string.wktranslate_parser_type)))
        root.addView(machineParserSpinner)
        root.addView(button(getString(R.string.wktranslate_test_machine)) { saveMachineFields(); testMachine() })
        root.addView(section(""))
        root.addView(button(getString(R.string.wktranslate_save)) { saveAll(); toast(getString(R.string.wktranslate_saved)) })
        root.addView(button(getString(R.string.wktranslate_clear_cache)) { clearCache() })
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
        TranslatePrefs.saveAiConfig(this, aiEndpointEt.text.toString(), aiKeyEt.text.toString(), aiModelEt.text.toString(), TranslatePrefs.AI_ADAPTER_DEEPSEEK)
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
        val ai = TranslatePrefs.getAiConfig(this)
        if (ai.endpoint.isBlank() || ai.apiKey.isBlank() || ai.model.isBlank()) {
            toast(getString(R.string.wktranslate_input_required)); return
        }
        scope.launch {
            val result = TranslateManager.testAi(this@TranslateSettingsActivity, getString(R.string.wktranslate_test_text), getString(R.string.wktranslate_test_target_lang))
            toast(if (result.success) getString(R.string.wktranslate_ai_test_success) else result.message.ifBlank { getString(R.string.wktranslate_translate_failed) })
            updateAiStatus()
        }
    }

    private fun testMachine() {
        scope.launch {
            val result = TranslateManager.testMachine(this@TranslateSettingsActivity, getString(R.string.wktranslate_test_text), getString(R.string.wktranslate_test_target_lang))
            toast(if (result.success) getString(R.string.wktranslate_machine_test_success) else result.message.ifBlank { getString(R.string.wktranslate_translate_failed) })
        }
    }

    private fun clearCache() {
        scope.launch {
            withContext(Dispatchers.IO) { TranslateManager.clearCache(this@TranslateSettingsActivity) }
            toast(getString(R.string.wktranslate_cache_cleared))
        }
    }

    private fun updateAiStatus() {
        aiStatusTv.text = if (TranslatePrefs.isAiVerified(this)) getString(R.string.wktranslate_ai_verified) else getString(R.string.wktranslate_ai_not_verified)
    }

    private fun section(text: String): TextView = TextView(this).apply { this.text = text; textSize = 17f; setPadding(0, dp(18), 0, dp(8)) }
    private fun label(text: String): TextView = TextView(this).apply { this.text = text; textSize = 13f; setPadding(0, dp(10), 0, dp(4)) }
    private fun editText(hint: String, password: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    }
    private fun spinner(items: List<String>): Spinner = Spinner(this).apply { adapter = ArrayAdapter(this@TranslateSettingsActivity, android.R.layout.simple_spinner_dropdown_item, items) }
    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply { this.text = text; setOnClickListener { onClick() } }
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
