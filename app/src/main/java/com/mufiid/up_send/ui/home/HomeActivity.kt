package com.mufiid.up_send.ui.home

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import android.widget.SearchView.OnQueryTextListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.mufiid.up_send.R
import com.mufiid.up_send.data.EventEntity
import com.mufiid.up_send.databinding.ActivityHomeBinding
import com.mufiid.up_send.ui.detail.DetailActivity
import com.mufiid.up_send.ui.event.EventActivity
import com.mufiid.up_send.ui.login.LoginActivity
import com.mufiid.up_send.ui.profile.ProfileActivity
import com.mufiid.up_send.ui.scanner.ScannerActivity
import com.mufiid.up_send.utils.helper.CustomView
import com.mufiid.up_send.utils.pref.UserPref

class HomeActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: EventAdapter

    companion object {
        const val ACTIVITY_NAME = "HomeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // view pager tabs
//        val pagerAdapter = SectionPagerAdapter(this, supportFragmentManager)
//        activityHomeBinding.viewPager.adapter = pagerAdapter
//        activityHomeBinding.tabs.setupWithViewPager(activityHomeBinding.viewPager)
        initSupportActionBar()
        init()
        checkPermission()
        showDataEvent()
        setRecyclerView()
        showDataBySearch()

    }

    override fun onResume() {
        super.onResume()
        checkLogin()
        binding.include.svHome.setQuery("", false)
        binding.include.svHome.isIconified = true
    }

    private fun checkLogin() {
        UserPref.getIsLoggedIn(this)?.let {
            if(!it) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }

    }

    private fun init() {
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.NewInstanceFactory()
        )[HomeViewModel::class.java]
        viewModel.getState().observer(this, Observer {
            handlerUIState(it)
        })

        binding.include.ibScan.setOnClickListener(this)
        binding.include.ibSetting.setOnClickListener(this)
        binding.include.ibAdd.setOnClickListener(this)
    }

    private fun handlerUIState(it: EventState?) {
        when (it) {
            is EventState.IsLoading -> showLoading(it.state)
            is EventState.Error -> showToast(it.err, false)
            is EventState.IsSuccess -> showToast(it.message)
        }
    }

    private fun setRecyclerView() {
        adapter = EventAdapter { event ->
            showSelectedData(event)
        }.apply {
            notifyDataSetChanged()
        }

        binding.rvEvents.layoutManager = LinearLayoutManager(this)
        binding.rvEvents.adapter = adapter
    }

    private fun showDataEvent() {
        UserPref.getUserData(this)?.let {
            it.token?.let { it1 -> Log.d("TOKEN", it1) }
            viewModel.getAllDataByJoin(it.id, it.token)
        }

        viewModel.getEvents().observe(this, Observer {
            if (it.isNullOrEmpty()) {
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = getString(R.string.data_not_found)
            } else {
                binding.tvMessage.visibility = View.GONE
                adapter.setEvent(it)
            }
        })
    }

    private fun showDataBySearch() {
        binding.include.svHome.setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.getEventsBySearch(
                    UserPref.getUserData(this@HomeActivity)?.token,
                    query,
                    UserPref.getUserData(this@HomeActivity)?.id
                )
                binding.include.svHome.clearFocus()
                return true
            }

            override fun onQueryTextChange(query: String?): Boolean {
                return false
            }
        })

        binding.include.svHome.setOnCloseListener {
            showDataEvent()
            return@setOnCloseListener false
        }
    }

    private fun initSupportActionBar() {
        setSupportActionBar(binding.include.toolbar)
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (
                checkSelfPermission(
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    1
                )
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.ib_setting -> {
                PopupMenu(this, binding.include.ibSetting).apply {
                    inflate(R.menu.setting_menu)
                    setOnMenuItemClickListener {
                        when(it.itemId) {
                            R.id.menu_profile -> {
                                startActivity(Intent(this@HomeActivity, ProfileActivity::class.java))
                            }
                            R.id.menu_logout -> {
                                AlertDialog.Builder(this@HomeActivity).apply {
                                    setTitle(getString(R.string.confirm_logout))
                                    setMessage(getString(R.string.question_logout))
                                        .setPositiveButton(getString(R.string.yes)) { _, _->
                                            UserPref.setIsLoggedIn(this@HomeActivity, false)
                                            viewModel.logout(UserPref.getUserData(this@HomeActivity)?.token)
                                            UserPref.clear(this@HomeActivity)
                                            Handler(mainLooper).postDelayed({
                                                startActivity(Intent(this@HomeActivity, LoginActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                })
                                                finish()
                                            }, 1000)
                                        }
                                        .setNegativeButton(getString(R.string.no)) { dialogInterface, _ ->
                                            dialogInterface.dismiss()
                                        }
                                }.show()
                            }
                        }
                        return@setOnMenuItemClickListener false
                    }
                }.show()
            }
            R.id.ib_scan -> {
                startActivity(Intent(this, ScannerActivity::class.java).apply {
                    putExtra(ScannerActivity.EXTRAS_ACTIVITY, ACTIVITY_NAME)
                })
            }
            R.id.ib_add -> {
                startActivity(Intent(this, EventActivity::class.java))
            }
        }
    }

    private fun showLoading(state: Boolean) {
        if (state) {
            binding.progressBar.visibility = View.VISIBLE
        } else {
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun showToast(message: String?, state: Boolean? = true) {
        state?.let { isSuccess ->
            if (isSuccess) {
                CustomView.customToast(this, message, true, isSuccess = true)
            } else {
                CustomView.customToast(this, message, true, isSuccess = false)
            }
        }
    }

    private fun showSelectedData(event: EventEntity) {
        startActivity(Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRAS_DATA, event)
        })
    }
}