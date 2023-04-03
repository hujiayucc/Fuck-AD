package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookModulePrefs
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.AppChildBinding
import com.hujiayucc.hook.utils.AppInfo
import com.hujiayucc.hook.utils.Data.updateConfig

class ListViewAdapter(
    private val appContext: Context,
    private val appList: List<AppInfo>,
    private val modulePrefs: YukiHookModulePrefs,
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

    @SuppressLint("ViewHolder", "InflateParams", "UseSwitchCompatOrMaterialCode")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = LayoutInflater.from(appContext).inflate(R.layout.app_child, null)
        binding = AppChildBinding.bind(view)
        val info = getItem(position)
        info.switchCheck = binding.switchCheck
        info.switchCheck.setOnCheckedChangeListener { _, isChecked ->
            modulePrefs.put(PrefsData(info.app_package, false), isChecked)
            appContext.updateConfig(modulePrefs.all())
        }
        if (info.app_icon != null) binding.appIcon.setImageDrawable(info.app_icon)
        binding.appName.text = info.app_name
        binding.appPackage.text = info.app_package
        binding.switchCheck.isChecked = modulePrefs.get(PrefsData(info.app_package, true))
        return view
    }
}