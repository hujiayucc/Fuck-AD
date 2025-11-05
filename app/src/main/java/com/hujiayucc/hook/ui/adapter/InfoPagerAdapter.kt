package com.hujiayucc.hook.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hujiayucc.hook.ui.fragment.InfoListFragment

class InfoPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val packageName: String
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> InfoListFragment.newInstance(
                packageName,
                InfoListAdapter.ComponentType.ACTIVITY
            )
            1 -> InfoListFragment.newInstance(
                packageName,
                InfoListAdapter.ComponentType.SERVICE
            )
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}

