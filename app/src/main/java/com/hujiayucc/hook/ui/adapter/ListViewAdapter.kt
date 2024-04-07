package com.hujiayucc.hook.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.AppChildBinding
import com.hujiayucc.hook.data.Data.updateConfig

class ListViewAdapter(
    private val appList: List<AppInfo>,
) : BaseAdapter() {
    private lateinit var binding: AppChildBinding
    override fun getCount(): Int {
        return appList.size
    }

    override fun getItem(position: Int): AppInfo {
        return appList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = LayoutInflater.from(appContext).inflate(R.layout.app_child, null)
        binding = AppChildBinding.bind(view)
        val info = getItem(position)
        info.switchCheck = binding.switchCheck
        info.switchCheck?.setOnCheckedChangeListener { _, isChecked ->
            appContext.prefs().edit {
                putBoolean(info.packageName, isChecked)
            }
            Thread{appContext.updateConfig(appContext.prefs().all())}.start()
        }
        if (info.appIcon != null) binding.appIcon.setImageDrawable(info.appIcon)
        binding.appName.text = info.appName
        binding.appPackage.text = info.packageName
        binding.switchCheck.isChecked = appContext.prefs().get(PrefsData(info.packageName, true))
        return view
    }
}