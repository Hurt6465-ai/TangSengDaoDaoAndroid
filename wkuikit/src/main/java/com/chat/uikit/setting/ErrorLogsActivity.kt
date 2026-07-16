package com.chat.uikit.setting

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.chat.base.base.WKBaseActivity
import com.chat.base.endpoint.EndpointManager
import com.chat.base.endpoint.entity.SendFileMenu
import com.chat.base.entity.PopupMenuItem
import com.chat.base.utils.DataCleanManager
import com.chat.base.utils.WKDialogUtils
import com.chat.base.utils.WKFileUtils
import com.chat.base.utils.WKPermissions
import com.chat.base.utils.WKPermissions.IPermissionResult
import com.chat.base.utils.WKReader
import com.chat.base.utils.WKTimeUtils
import com.chat.uikit.R
import com.chat.uikit.databinding.ActCommonListLayoutBinding
import java.io.File
import java.nio.charset.Charset

class ErrorLogsActivity : WKBaseActivity<ActCommonListLayoutBinding>() {
    private lateinit var adapter: FileAdapter

    override fun getViewBinding(): ActCommonListLayoutBinding {
        return ActCommonListLayoutBinding.inflate(layoutInflater)
    }

    override fun setTitle(titleTv: TextView?) {
        titleTv!!.setText(R.string.error_data)
    }

    override fun initPresenter() {
        val desc = String.format(
            getString(R.string.file_permissions_des),
            getString(R.string.app_name)
        )
        if (Build.VERSION.SDK_INT < 33) {
            WKPermissions.getInstance().checkPermissions(
                object : IPermissionResult {
                    override fun onResult(result: Boolean) {}
                    override fun clickResult(isCancel: Boolean) { finish() }
                },
                this,
                desc,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            WKPermissions.getInstance().checkPermissions(
                object : IPermissionResult {
                    override fun onResult(result: Boolean) {}
                    override fun clickResult(isCancel: Boolean) { finish() }
                },
                this,
                desc,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        }
    }

    override fun initView() {
        adapter = FileAdapter()
        initAdapter(wkVBinding.recyclerView, adapter)
    }

    override fun initListener() {
        adapter.setOnItemClickListener { _, _, position ->
            val item = adapter.data.getOrNull(position) ?: return@setOnItemClickListener
            showLogPreview(item)
        }
    }

    override fun initData() {
        val list = getData()
        adapter.setList(list)
        if (list.isEmpty()) {
            Toast.makeText(this, "暂无崩溃日志，复现闪退后再进入这里", Toast.LENGTH_LONG).show()
        }
    }

    fun getData(): ArrayList<LogEntity> {
        val path = WKFileUtils.getInstance().getNormalFileSavePath("wkCrash")
        val fileList: ArrayList<LogEntity> = ArrayList()
        val dir = File(path)
        val tempList: Array<File> = dir.listFiles() ?: return fileList
        for (log in tempList) {
            if (log.isFile && log.name.endsWith(".log")) {
                val size = WKFileUtils.getInstance().getFileSize(log)
                val sizeStr = DataCleanManager.getFormatSize(size.toDouble())
                fileList.add(LogEntity(log.name, log.toString(), sizeStr, log.lastModified(), size))
            }
        }
        if (WKReader.isNotEmpty(fileList)) {
            fileList.sortWith { left: LogEntity, right: LogEntity -> right.time.compareTo(left.time) }
        }
        return fileList
    }

    private fun showLogPreview(item: LogEntity) {
        val file = File(item.path)
        if (!file.exists()) {
            Toast.makeText(this, "日志文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val fullText = runCatching { file.readText(Charset.defaultCharset()) }.getOrElse { error ->
            "读取日志失败：${error.message}"
        }
        val preview = if (fullText.length > 20000) {
            "日志较长，仅显示最后 20000 字符。\n\n" + fullText.takeLast(20000)
        } else {
            fullText
        }
        val textView = TextView(this)
        textView.text = preview
        textView.textSize = 12f
        val padding = dp(16)
        textView.setPadding(padding, padding, padding, padding)
        textView.setTextIsSelectable(true)
        val scrollView = ScrollView(this)
        scrollView.addView(textView)
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setView(scrollView)
            .setPositiveButton("复制") { _, _ -> copyText(fullText) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun copyText(text: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("crash_log", text))
        Toast.makeText(this, "已复制崩溃日志", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    class LogEntity(
        val name: String,
        val path: String,
        val sizeStr: String,
        val time: Long,
        val size: Long
    )

    class FileAdapter : BaseQuickAdapter<LogEntity, BaseViewHolder>(R.layout.item_logger_layout) {
        override fun convert(holder: BaseViewHolder, item: LogEntity) {
            holder.setText(R.id.nameTv, item.name)
            holder.setText(R.id.sizeTv, item.sizeStr)
            holder.setText(R.id.timeTv, WKTimeUtils.getInstance().time2DateStr1(item.time))
            val list: MutableList<PopupMenuItem> = ArrayList()
            list.add(
                PopupMenuItem(context.getString(R.string.forward),
                    R.mipmap.msg_forward, object : PopupMenuItem.IClick {
                        override fun onClick() {
                            EndpointManager.getInstance().invoke(
                                "forward_file",
                                SendFileMenu(item.name, item.path, item.size)
                            )
                        }
                    })
            )
            list.add(
                PopupMenuItem(context.getString(R.string.str_delete), R.mipmap.msg_delete,
                    object : PopupMenuItem.IClick {
                        override fun onClick() {
                            val file = File(item.path)
                            if (file.exists()) {
                                file.delete()
                                removeAt(holder.bindingAdapterPosition)
                            }
                        }
                    })
            )
            WKDialogUtils.getInstance()
                .setViewLongClickPopup(holder.getView(R.id.contentLayout), list)
        }
    }
}
