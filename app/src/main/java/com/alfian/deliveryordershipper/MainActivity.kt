package com.alfian.deliveryordershipper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.ShopModel
import com.alfian.deliveryordershipper.model.ShipperUserModel
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dmax.dialog.SpotsDialog
import io.paperdb.Paper
import java.util.*

class MainActivity : AppCompatActivity() {

    private var firebaseAuth: FirebaseAuth? = null
    private var listener: FirebaseAuth.AuthStateListener? = null
    private var dialog: AlertDialog? = null
    private var serverRef: DatabaseReference? = null
    private var providers : List<AuthUI.IdpConfig>? = null

    companion object{
        private const val APP_REQUEST_CODE = 7171
    }

    override fun onStart() {
        super.onStart()
        firebaseAuth!!.addAuthStateListener(listener!!)
    }

    override fun onStop() {
        firebaseAuth!!.removeAuthStateListener(listener!!)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()

        //Delete data
        Paper.init(this)
//        Paper.book().delete(Common.TRIP_START)
//        Paper.book().delete(Common.SHIPPING_DATA)
    }

    private fun init() {
        providers = listOf(AuthUI.IdpConfig.PhoneBuilder().build(),
        AuthUI.IdpConfig.EmailBuilder().build())

        serverRef = FirebaseDatabase.getInstance().getReference(Common.SHIPPER_REF)
        firebaseAuth = FirebaseAuth.getInstance()
        dialog = SpotsDialog.Builder().setContext(this).setCancelable(false).build()
        listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Paper.init(this@MainActivity)
                val jsonEncode = Paper.book().read<String>(Common.SHOP_SAVE)
                Log.d("abcde", "MainActiv jsonEncode : $jsonEncode")
                val shopModel = Gson().fromJson<ShopModel>(jsonEncode,
                    object:TypeToken<ShopModel>(){}.type)
                if (shopModel != null)
                    checkServerUserFromFirebase(user, shopModel)
                else {
                    startActivity(Intent(this@MainActivity,ShopListActivity::class.java))
                    finish()
                }
            } else {
                phoneLogin()
            }
        }
    }

    private fun checkServerUserFromFirebase(user: FirebaseUser, shopModel: ShopModel) {
        dialog!!.show()
        //Init serverRef
        serverRef = FirebaseDatabase.getInstance().getReference(Common.SHOP_REF)
            .child(shopModel.uid)
            .child(Common.SHIPPER_REF)
        serverRef!!.child(user.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    dialog!!.dismiss()
                    Toast.makeText(this@MainActivity,""+p0.message,Toast.LENGTH_SHORT).show()
                }

                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists())
                    {
                        val userModel = dataSnapshot.getValue(ShipperUserModel::class.java)
                        if (userModel!!.isActive)
                        {
                            goToHomeActivity(userModel,shopModel)
                        }
                        else{
                            dialog!!.dismiss()
                            Toast.makeText(this@MainActivity,"You must be allowed from Admin to access this app",Toast.LENGTH_SHORT).show()

                        }
                    }

                }

            })
    }

    private fun goToHomeActivity(userModel: ShipperUserModel, shopModel: ShopModel) {
        dialog!!.dismiss()
        Common.currentShop = shopModel
        Common.currentShipperUser = userModel
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun phoneLogin() {
        startActivityForResult(AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers!!)
            .setTheme(R.style.LoginTheme)
            .setLogo(R.drawable.delivery_logo)
            .build(),APP_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == APP_REQUEST_CODE)
        {

            if (resultCode == Activity.RESULT_OK)
            {
                val user = FirebaseAuth.getInstance().currentUser
            }
            else
            {
                Toast.makeText(this,"Failed to sign in",Toast.LENGTH_SHORT).show()
            }
        }
    }
}
