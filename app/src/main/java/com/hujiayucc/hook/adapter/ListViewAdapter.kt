package com.hujiayucc.hook.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookModulePrefs
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.hujiayucc.hook.R
import com.hujiayucc.hook.bean.AppInfo
import com.hujiayucc.hook.utils.Log

class ListViewAdapter(
    val appContext: Context,
    val appList: List<AppInfo>,
    val modulePrefs: YukiHookModulePrefs,
) : BaseAdapter() {
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
        val info = getItem(position)
        val icon = view.findViewById<ImageView>(R.id.app_icon)
        val name = view.findViewById<TextView>(R.id.app_name)
        val appPackage = view.findViewById<TextView>(R.id.app_package)
        val check = view.findViewById<Switch>(R.id.switch_check)
        check.setOnCheckedChangeListener { buttonView, isChecked ->
            modulePrefs.put(PrefsData(info.app_package!!, false), isChecked)
        }
        if (info.app_icon != null) icon.setImageDrawable(info.app_icon)
        name.text = info.app_name
        appPackage.text = info.app_package
        check.isChecked = modulePrefs.get(PrefsData(info.app_package!!, true))
        check.setOnClickListener {
            Log.d("${info.app_name}")
        }
        return view
    }
}