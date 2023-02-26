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
import com.hujiayucc.hook.bean.AppInfo
import com.hujiayucc.hook.databinding.AppChildBinding

class ListViewAdapter(
    val appContext: Context,
    val appList: List<AppInfo>,
    val modulePrefs: YukiHookModulePrefs,
) : BaseAdapter() {
    private lateinit var binding: AppChildBinding
    override fun getCount(): Int {
        return appList.size
    }

    override fun getItem(position: Int): AppInfo {
        return appList.get(position)
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
        info.switchCheck.setOnCheckedChangeListener { buttonView, isChecked ->
            modulePrefs.put(PrefsData(info.app_package, false), isChecked)
        }
        if (info.app_icon != null) binding.appIcon.setImageDrawable(info.app_icon)
        binding.appName.text = info.app_name
        binding.appPackage.text = info.app_package
        binding.switchCheck.isChecked = modulePrefs.get(PrefsData(info.app_package, true))
        return view
    }
}