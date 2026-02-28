  package com.example.halliplanner

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

  class MainActivity : AppCompatActivity() {

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContentView(R.layout.activity_main)

          val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

          replaceFragment(HomeFragment())

          bottomNav.setOnItemSelectedListener {

              when(it.itemId) {
                  R.id.nav_home -> replaceFragment(HomeFragment())
                  R.id.nav_schedule -> replaceFragment(ScheduleFragment())
                  R.id.nav_tasks -> replaceFragment(TasksFragment())
                  R.id.nav_profile -> replaceFragment(ProfileFragment())
              }
              true
          }
      }

      private fun replaceFragment(fragment: Fragment) {
          supportFragmentManager.beginTransaction()
              .replace(R.id.fragment_container, fragment)
              .commit()
      }
  }