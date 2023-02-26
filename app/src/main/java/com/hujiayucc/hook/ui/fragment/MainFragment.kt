package com.hujiayucc.hook.ui.fragment

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.bean.AppInfo
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.databinding.FragmentMainBinding
import com.hujiayucc.hook.ui.activity.MainActivity.Companion.searchText
import com.hujiayucc.hook.ui.adapter.ListViewAdapter
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import java.text.Collator
import java.util.*

class MainFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding
    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    lateinit var refresh: SwipeRefreshLayout
    val list = ArrayList<AppInfo>()
    private var isSystem: Boolean = false

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, null, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMainBinding.bind(view)
        listView = binding.list
        progressBar = binding.progress
        refresh = binding.refresh

        refresh.setOnRefreshListener {
            if (searchText.isEmpty()) {
                loadAppList(isSystem)
            }
            refresh.isRefreshing = false
        }
        isSystem = requireArguments().getBoolean("system")
        loadAppList(isSystem)
        listView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            val info = listView.adapter.getItem(position) as AppInfo
            info.switchCheck.isChecked = !info.switchCheck.isChecked
        }

        listView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { parent, view, position, id ->
                true
            }
    }

    private fun loadAppList(showSysApp: Boolean) {
        if (list.isEmpty()) {
            progressBar.progress = 0
            refresh.visibility = View.GONE
            binding.progress.visibility = View.VISIBLE
            Thread {
                try {
                    val apps =
                        appContext.packageManager.getInstalledApplications(PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                    var i = 0
                    progressBar.max = apps.size
                    for (info in apps) {
                        if (!info.packageName.equals(BuildConfig.APPLICATION_ID) && !info.sourceDir.equals("/system/app/HybridPlatform/HybridPlatform.apk")) {
                            val icon = info.loadIcon(appContext.packageManager)
                            val label = appContext.packageManager.getApplicationLabel(info)
                            val appinfo = AppInfo(icon, label, info.packageName)
                            val flag = info.flags
                            if (!showSysApp) {
                                if ((flag and ApplicationInfo.FLAG_SYSTEM) == 0) list.add(appinfo)
                            } else {
                                if ((flag and ApplicationInfo.FLAG_SYSTEM) != 0) list.add(appinfo)
                            }
                        }
                        progressBar.progress = i
                        i++
                        if (i == apps.size) showList(list)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } else {
            showList(list)
        }
    }

    fun showList(list: ArrayList<AppInfo>) {
        Thread {
            val instance = Collator.getInstance(Language.fromId(appContext.modulePrefs.get(Data.localeId)))
            try {
                list.sortWith { o1, o2 ->
                    val o1Boolean = requireActivity().modulePrefs.getBoolean(o1.app_package, false)
                    val o2Boolean = requireActivity().modulePrefs.getBoolean(o2.app_package, false)

                    /** 排序 */
                    if (o1Boolean && !o2Boolean) -1
                    else if (!o1Boolean && o2Boolean) 0
                    else instance.compare(o1.app_name, o2.app_name)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
        val adapter = ListViewAdapter(appContext, list, appContext.modulePrefs)
        requireActivity().runOnUiThread {
            listView.adapter = adapter
            progressBar.visibility = View.GONE
            refresh.visibility = View.VISIBLE
        }
        Log.d("加载完毕 共${list.size}个应用")
    }
}