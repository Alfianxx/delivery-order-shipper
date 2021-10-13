package com.alfian.deliveryordershipper

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alfian.deliveryordershipper.adapter.MyShopAdapter
import com.alfian.deliveryordershipper.callback.IShopCallbackListener
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.ShopModel
import com.alfian.deliveryordershipper.model.ShipperUserModel
import com.alfian.deliveryordershipper.model.eventbus.ShopSelectEvent
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.gson.Gson
import io.paperdb.Paper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ShopListActivity : AppCompatActivity(), IShopCallbackListener {

    private lateinit var recyclerShop:RecyclerView
    lateinit var dialog: AlertDialog
    private lateinit var layoutAnimationController:LayoutAnimationController
    private var adapter: MyShopAdapter?=null

    private var serverRef:DatabaseReference?=null
    lateinit var listener: IShopCallbackListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop_list)

        initViews()
        loadShopFromFirebase()

    }

    private fun loadShopFromFirebase() {
        dialog.show()

        val shopModels = ArrayList<ShopModel>()
        val shopRef = FirebaseDatabase.getInstance()
            .getReference(Common.SHOP_REF)
        shopRef.addListenerForSingleValueEvent(object:ValueEventListener{
            override fun onDataChange(p0: DataSnapshot) {
                if (p0.exists())
                {
                    for (shopSnapshot in p0.children)
                    {
                        val shopModel = shopSnapshot.getValue(ShopModel::class.java)
                        shopModel!!.uid = shopSnapshot.key!!
                        shopModels.add(shopModel)
                    }
                    if (shopModels.size > 0)
                        listener.onShopLoadSuccess(shopModels)
                    else
                        listener.onShopLoadFailed("Shop list empty")
                }
                else
                {
                    listener.onShopLoadFailed("Shop list not found")
                }
            }

            override fun onCancelled(p0: DatabaseError) {
                listener.onShopLoadFailed(p0.message)
            }

        })
    }

    private fun initViews() {

        listener = this

        dialog = AlertDialog.Builder(this).setCancelable(false)
            .setMessage("Please wait...").create()
        dialog.show()
        layoutAnimationController = AnimationUtils.loadLayoutAnimation(this,R.anim.layout_item_from_left)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.orientation = RecyclerView.VERTICAL
        recyclerShop = findViewById(R.id.recycler_shop)
        recyclerShop.layoutManager = layoutManager
        recyclerShop.addItemDecoration(DividerItemDecoration(this,layoutManager.orientation))
    }

    override fun onShopLoadSuccess(shopList: List<ShopModel>) {
        dialog.dismiss()
        adapter = MyShopAdapter(this,shopList)
        recyclerShop.adapter = adapter!!
        recyclerShop.layoutAnimation = layoutAnimationController
    }

    override fun onShopLoadFailed(message: String) {
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onShopSelectEvent(shopSelectEvent: ShopSelectEvent)
    {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null)
            checkServerUserFromFirebase(user,shopSelectEvent.shopModel)
    }

    private fun checkServerUserFromFirebase(user: FirebaseUser, shopModel: ShopModel) {
        dialog.show()
        serverRef = FirebaseDatabase.getInstance()
            .getReference(Common.SHOP_REF)
            .child(shopModel.uid)
            .child(Common.SHIPPER_REF)

        serverRef!!.child(user.uid)
            .addListenerForSingleValueEvent(object:ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {
                    if (p0.exists())
                    {
                        val userModel = p0.getValue(ShipperUserModel::class.java)
                        if (userModel!!.isActive)
                            goToHomeActivity(userModel,shopModel)
                        else
                        {
                            dialog.dismiss()
                            Toast.makeText(this@ShopListActivity,"You must be allowed by Server app",Toast.LENGTH_SHORT).show()
                        }
                    }
                    else
                    {
                        dialog.dismiss()
                        showRegisterDialog(user,shopModel.uid)
                    }
                }

                override fun onCancelled(p0: DatabaseError) {
                    dialog.dismiss()
                    Toast.makeText(this@ShopListActivity,p0.message,Toast.LENGTH_SHORT).show()
                }

            })
    }

    private fun showRegisterDialog(user: FirebaseUser, uid: String) {
        val builder = AlertDialog.Builder(this@ShopListActivity)
        builder.setTitle("Register")
        builder.setMessage("Please fill information \n Admin will accept your account late")

        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register,null)
        val phoneInputLayout = itemView.findViewById<View>(R.id.phone_input_layout) as TextInputLayout
        val edtName = itemView.findViewById<View>(R.id.edt_name) as EditText
        val edtPhone = itemView.findViewById<View>(R.id.edt_phone) as EditText

        //set
        if (user.phoneNumber == null || TextUtils.isEmpty(user.phoneNumber))
        {
            phoneInputLayout.hint = "Email"
            edtPhone.setText(user.email)
            edtName.setText(user.displayName)
        }
        else
            edtPhone.setText(user.phoneNumber)

        builder.setNegativeButton("CANCEL") { dialogInterface, _ -> dialogInterface.dismiss() }
            .setPositiveButton("REGISTER") { _, _ ->
                if (TextUtils.isEmpty(edtName.text)) {
                    Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val serverUserModel = ShipperUserModel()
                serverUserModel.uid = user.uid
                serverUserModel.name = edtName.text.toString()
                serverUserModel.phone = edtPhone.text.toString()
                serverUserModel.isActive =
                    false //Default fail, we must active user by manual on Firebase

                dialog.show()
                //Init server ref
                serverRef = FirebaseDatabase.getInstance().getReference(Common.SHOP_REF)
                    .child(uid)
                    .child(Common.SHIPPER_REF)
                serverRef!!.child(serverUserModel.uid!!)
                    .setValue(serverUserModel)
                    .addOnFailureListener { e ->
                        dialog.dismiss()
                        Toast.makeText(
                            this@ShopListActivity,
                            "" + e.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnCompleteListener {
                        dialog.dismiss()
                        Toast.makeText(
                            this@ShopListActivity,
                            "Register success! Admin will check and active user soon",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }

        builder.setView(itemView)

        val registerDialog = builder.create()
        registerDialog.show()
    }

    private fun goToHomeActivity(userModel: ShipperUserModel, shopModel: ShopModel) {
        dialog.dismiss()
        Common.currentShipperUser = userModel
        val jsonEncode = Gson().toJson(shopModel) //encode all information
        Paper.init(this)
        Paper.book().write(Common.SHOP_SAVE,jsonEncode)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}