package com.hujiayucc.hook.ui.fragment

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.hujiayucc.hook.databinding.FragmentAppInfoListBinding
import com.hujiayucc.hook.ui.adapter.InfoListAdapter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class InfoListFragment : Fragment() {
    private var _binding: FragmentAppInfoListBinding? = null
    private val binding get() = _binding!!

    private lateinit var listView: ListView
    private lateinit var adapter: InfoListAdapter
    private val componentList = ArrayList<InfoListAdapter.ComponentItem>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadExecutor: ExecutorService? = null

    private var packageName: String = ""
    private var componentType: InfoListAdapter.ComponentType = InfoListAdapter.ComponentType.ACTIVITY

    private var pendingQuery: String? = null

    companion object {
        private const val ARG_PACKAGE_NAME = "packageName"
        private const val ARG_COMPONENT_TYPE = "componentType"
        private const val FR_SEARCH_KEY_ACTIVITY = "app_info_search_activity"
        private const val FR_SEARCH_KEY_SERVICE = "app_info_search_service"
        private const val FR_QUERY_KEY = "query"


        fun newInstance(packageName: String, componentType: InfoListAdapter.ComponentType): InfoListFragment {
            val fragment = InfoListFragment()
            val args = Bundle()
            args.putString(ARG_PACKAGE_NAME, packageName)
            args.putSerializable(ARG_COMPONENT_TYPE, componentType)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            packageName = it.getString(ARG_PACKAGE_NAME) ?: ""
            componentType = getComponentType(it)
        }
    }

    private fun getComponentType(args: Bundle): InfoListAdapter.ComponentType {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            args.getSerializable(ARG_COMPONENT_TYPE, InfoListAdapter.ComponentType::class.java)
        } else {
            @Suppress("DEPRECATION")
            args.getSerializable(ARG_COMPONENT_TYPE) as? InfoListAdapter.ComponentType
        } ?: InfoListAdapter.ComponentType.ACTIVITY
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppInfoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView = binding.componentList
        adapter = InfoListAdapter(componentList)
        listView.adapter = adapter
        setupSearchReceiver()

        loadComponents()
    }

    override fun onDestroyView() {
        loadExecutor?.shutdownNow()
        loadExecutor = null
        super.onDestroyView()
        _binding = null
    }

    private fun setupSearchReceiver() {
        val key = when (componentType) {
            InfoListAdapter.ComponentType.ACTIVITY -> FR_SEARCH_KEY_ACTIVITY
            InfoListAdapter.ComponentType.SERVICE -> FR_SEARCH_KEY_SERVICE
        }
        parentFragmentManager.setFragmentResultListener(key, viewLifecycleOwner) { _, bundle ->
            val q = bundle.getString(FR_QUERY_KEY, "")
            pendingQuery = q
            adapter.filter.filter(q)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadComponents() {
        if (packageName.isEmpty()) {
            showError("包名为空")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.textView.visibility = View.VISIBLE
        listView.visibility = View.GONE

        loadExecutor?.shutdownNow()
        val executor = Executors.newSingleThreadExecutor()
        loadExecutor = executor
        executor.execute {
            val result = runCatching {
                val packageInfo = requireContext().packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                )
                buildComponentItems(packageInfo)
            }

            mainHandler.post {
                if (_binding == null || loadExecutor !== executor) return@post
                result.onSuccess { items -> showComponents(items) }
                    .onFailure { error -> showError("加载失败: ${error.message}") }
            }
        }
    }

    private fun buildComponentItems(packageInfo: PackageInfo): List<InfoListAdapter.ComponentItem> {
        val items = ArrayList<InfoListAdapter.ComponentItem>()
        when (componentType) {
            InfoListAdapter.ComponentType.ACTIVITY -> {
                val activities = packageInfo.activities ?: emptyArray()
                activities.forEach { activityInfo ->
                    items.add(
                        InfoListAdapter.ComponentItem(
                            name = activityInfo.name,
                            type = InfoListAdapter.ComponentType.ACTIVITY,
                            exported = activityInfo.exported
                        )
                    )
                }
            }

            InfoListAdapter.ComponentType.SERVICE -> {
                val services = packageInfo.services ?: emptyArray()
                services.forEach { serviceInfo ->
                    items.add(
                        InfoListAdapter.ComponentItem(
                            name = serviceInfo.name,
                            type = InfoListAdapter.ComponentType.SERVICE,
                            exported = serviceInfo.exported
                        )
                    )
                }
            }
        }
        return items.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun showComponents(items: List<InfoListAdapter.ComponentItem>) {
        componentList.clear()
        componentList.addAll(items)
        adapter.updateData(componentList.toList())
        pendingQuery?.let { adapter.filter.filter(it) }

        binding.progressBar.visibility = View.GONE
        binding.textView.visibility = View.GONE
        listView.visibility = View.VISIBLE
    }

    @SuppressLint("SetTextI18n")
    private fun showError(message: String) {
        if (_binding == null) return
        binding.progressBar.visibility = View.GONE
        binding.textView.text = message
        binding.textView.visibility = View.VISIBLE
        if (::listView.isInitialized) listView.visibility = View.GONE
    }
}
