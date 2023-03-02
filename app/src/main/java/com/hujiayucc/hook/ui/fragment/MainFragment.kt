package com.hujiayucc.hook.ui.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication.Companion.appContext
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.bean.AppInfo
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.themes
import com.hujiayucc.hook.databinding.FragmentMainBinding
import com.hujiayucc.hook.ui.activity.MainActivity.Companion.searchText
import com.hujiayucc.hook.ui.adapter.ListViewAdapter
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import java.text.Collator
import java.util.*

class MainFragment : Fragment() {
    private lateinit var binding: FragmentMainBinding
    lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    lateinit var refresh: SwipeRefreshLayout
    val list = ArrayList<AppInfo>()
    var searchList = ArrayList<AppInfo>()
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
        refresh.setColorSchemeColors(appContext.modulePrefs.get(themes))
        refresh.setOnRefreshListener {
            if (searchText.isEmpty()) {
                loadAppList(isSystem)
            } else {
                val list: ArrayList<AppInfo> = ArrayList()
                for (app in this.list) {
                    if (app.app_name.contains(searchText) or app.app_package.contains(searchText)) list.add(app)
                }
                showList(list)
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
                var info: AppInfo = list[position]
                if (searchText.isNotEmpty()) info = searchList[position]
                val popupMenu = PopupMenu(activity, view)
                val menu = popupMenu.menu
                menu.add(getString(R.string.menu_open_application).format(info.app_name)).setOnMenuItemClickListener {
                    try {
                        val intent: Intent? = appContext.packageManager.getLaunchIntentForPackage(info.app_package)
                        appContext.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            appContext, getString(R.string.failed_to_open_application)
                                .format(info.app_name), Toast.LENGTH_SHORT
                        ).show()
                    }
                    false
                }
                menu.add(getString(R.string.menu_open_all)).setOnMenuItemClickListener {
                    for (app in list) {
                        activity?.modulePrefs?.putBoolean(app.app_package, true)
                    }
                    showList(list)
                    false
                }
                menu.add(getString(R.string.menu_close_all)).setOnMenuItemClickListener {
                    for (app in list) {
                        activity?.modulePrefs?.putBoolean(app.app_package, false)
                    }
                    showList(list)
                    false
                }
                menu.add(getString(R.string.menu_invert_selection)).setOnMenuItemClickListener {
                    for (app in list) {
                        val isChecked = !activity?.modulePrefs?.getBoolean(app.app_package, true)!!
                        activity?.modulePrefs?.putBoolean(app.app_package, isChecked)
                    }
                    showList(list)
                    false
                }
                popupMenu.show()
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
                    }
                    showList(list)
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
                /** 排序 */
                val isCheckList: ArrayList<AppInfo> = ArrayList()
                val notCheckList: ArrayList<AppInfo> = ArrayList()
                for (app in list) {
                    val isChecked = requireActivity().modulePrefs.getBoolean(app.app_package, true)
                    if (isChecked) isCheckList.add(app)
                    else notCheckList.add(app)
                }
                isCheckList.sortWith { o1, o2 ->
                    instance.compare(o1.app_name, o2.app_name)
                }
                notCheckList.sortWith { o1, o2 ->
                    instance.compare(o1.app_name, o2.app_name)
                }
                list.clear()
                list.addAll(isCheckList)
                list.addAll(notCheckList)
                searchList.clear()
                searchList.addAll(list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
        val adapter = ListViewAdapter(appContext, list, appContext.modulePrefs)
        activity?.runOnUiThread {
            listView.adapter = adapter
            progressBar.visibility = View.GONE
            refresh.visibility = View.VISIBLE
        }
        Log.d("加载完毕 共${list.size}个应用")
    }
}