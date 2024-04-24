package com.airfreshener.telegram_sms.logScreen

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import android.widget.Toolbar.LayoutParams
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.databinding.ActivityLogcatBinding
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import com.airfreshener.telegram_sms.utils.ContextUtils.dpToPx
import kotlinx.coroutines.launch

class LogcatActivity : AppCompatActivity(R.layout.activity_logcat) {

    private val viewModel: LogViewModel by viewModels { LogViewModelFactory(applicationContext) }
    private val binding by viewBinding(ActivityLogcatBinding::bind)

    private val logRepository by lazy { app().logRepository }
    private var adapter: Adapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setTitle(R.string.logcat)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false).apply {
                stackFromEnd = true
            }
            setHasFixedSize(true)
            adapter = Adapter().apply { this@LogcatActivity.adapter = this }
        }
        lifecycleScope.launch { viewModel.logs.collect { adapter?.setItems(it) } }
    }

    private class Adapter : RecyclerView.Adapter<ViewHolder>() {
        private var items: ArrayList<String> = ArrayList()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,).apply {
                    val margin = context.dpToPx(8).toInt()
                    topMargin = margin
                    bottomMargin = margin
                    leftMargin = margin
                    rightMargin = margin
                }
            }
            return ViewHolder(tv)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = items[position]
        }

        fun setItems(newItems: List<String>) {
            val oldItems = items
            items = ArrayList(newItems)
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldItems.size
                override fun getNewListSize(): Int = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    oldItems[oldItemPosition] == newItems[newItemPosition]

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    oldItems[oldItemPosition] == newItems[newItemPosition]
            }).dispatchUpdatesTo(this)
        }

    }

    private class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        logRepository.resetLogFile()
        return true
    }

}
