package com.hujiayucc.hook.ui.fragment

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import com.hujiayucc.hook.databinding.FragmentComponentListBinding
import com.hujiayucc.hook.ui.adapter.InfoListAdapter
import java.util.*

class InfoListFragment : Fragment() {
    private var _binding: FragmentComponentListBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var adapter: InfoListAdapter
    private val componentList = ArrayList<InfoListAdapter.ComponentItem>()
    
    private var packageName: String = ""
    private var componentType: InfoListAdapter.ComponentType = InfoListAdapter.ComponentType.ACTIVITY

    companion object {
        private const val ARG_PACKAGE_NAME = "packageName"
        private const val ARG_COMPONENT_TYPE = "componentType"

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
            componentType = it.getSerializable(ARG_COMPONENT_TYPE) as? InfoListAdapter.ComponentType
                ?: InfoListAdapter.ComponentType.ACTIVITY
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComponentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        listView = binding.componentList
        searchView = binding.searchView
        adapter = InfoListAdapter(componentList)
        listView.adapter = adapter
        setupSearchView()

        loadComponents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun loadComponents() {
        if (packageName.isEmpty()) {
            binding.progressBar.visibility = View.GONE
            binding.textView.text = "包名为空"
            binding.textView.visibility = View.VISIBLE
            listView.visibility = View.GONE
            return
        }

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
            )

            componentList.clear()

            when (componentType) {
                InfoListAdapter.ComponentType.ACTIVITY -> {
                    val activities = packageInfo.activities ?: emptyArray()
                    activities.forEach { activityInfo ->
                        componentList.add(
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
                        componentList.add(
                            InfoListAdapter.ComponentItem(
                                name = serviceInfo.name,
                                type = InfoListAdapter.ComponentType.SERVICE,
                                exported = serviceInfo.exported
                            )
                        )
                    }
                }
            }

            componentList.sortBy { it.name.lowercase(Locale.getDefault()) }
            adapter.updateData(componentList.toList())

            binding.progressBar.visibility = View.GONE
            binding.textView.visibility = View.GONE
            listView.visibility = View.VISIBLE
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.textView.text = "加载失败: ${e.message}"
            binding.textView.visibility = View.VISIBLE
            listView.visibility = View.GONE
        }
    }
}

