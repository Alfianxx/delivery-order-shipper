package com.alfian.deliveryordershipper

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.alfian.deliveryordershipper.common.Common
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.installations.FirebaseInstallations
import io.paperdb.Paper

class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private var menuClickId: Int=-1
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        updateToken()
        checkStartTrip()

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener (this)

        val headerView = navView.getHeaderView(0)
        val tvName = headerView.findViewById<TextView>(R.id.tvName)
        val tvEmail = headerView.findViewById<TextView>(R.id.tvEmail)

        Common.setSpanString("Hello ", Common.currentShipperUser?.name, tvName)
        tvEmail.text = Common.currentShipperUser?.phone
    }

    override fun onResume() {
        super.onResume()
        checkStartTrip()
    }

    private fun checkStartTrip() {
        Paper.init(this)
        val data = Paper.book().read<String>(Common.TRIP_START)
        if (!TextUtils.isEmpty(data))
            startActivity(Intent(this, ShippingActivity::class.java))
    }

    private fun updateToken() {
        FirebaseInstallations.getInstance().getToken(true)
            .addOnFailureListener { e -> Toast.makeText(this@HomeActivity,""+e.message,Toast.LENGTH_SHORT).show() }
            .addOnSuccessListener { instanceIdResult ->
                Common.updateToken(this@HomeActivity, instanceIdResult.token,
                    isServerToken = false,
                    isShipperToken = true
                )
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.home, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onNavigationItemSelected(p0: MenuItem): Boolean {
        p0.isCheckable = true
        drawerLayout.closeDrawers()
        when(p0.itemId){
            R.id.nav_sign_out -> signOut()
        }
        menuClickId = p0.itemId
        return true
    }

    private fun signOut() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Sign out")
            .setMessage("Do you really want to exit?")
            .setNegativeButton("CANCEL") { dialogInterface, _ -> dialogInterface.dismiss() }
            .setPositiveButton("OK"){ _, _ ->
                Common.currentShop = null
                Common.currentShipperUser = null
                Paper.init(this)
                Paper.book().delete(Common.SHOP_SAVE)     //fix : login
                FirebaseAuth.getInstance().signOut()

                val intent = Intent(this@HomeActivity,
                    MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

        val dialog = builder.create()
        dialog.show()
    }
}