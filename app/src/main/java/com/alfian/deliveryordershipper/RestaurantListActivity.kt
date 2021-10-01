package com.alfian.deliveryordershipper

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
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
import com.alfian.deliveryordershipper.adapter.MyRestaurantAdapter
import com.alfian.deliveryordershipper.callback.IRestaurantCallbackListener
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.RestaurantModel
import com.alfian.deliveryordershipper.model.ShipperUserModel
import com.alfian.deliveryordershipper.model.eventbus.RestaurantSelectEvent
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.gson.Gson
import io.paperdb.Paper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class RestaurantListActivity : AppCompatActivity(), IRestaurantCallbackListener {

    private lateinit var recyclerRestaurant:RecyclerView
    lateinit var dialog: AlertDialog
    private lateinit var layoutAnimationController:LayoutAnimationController
    private var adapter: MyRestaurantAdapter?=null

    private var serverRef:DatabaseReference?=null
    lateinit var listener: IRestaurantCallbackListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_list)

        initViews()
        loadRestaurantFromFirebase()

    }

    private fun loadRestaurantFromFirebase() {
        dialog.show()

        val restaurantModels = ArrayList<RestaurantModel>()
        val restaurantRef = FirebaseDatabase.getInstance()
            .getReference(Common.RESTAURANT_REF)
        restaurantRef.addListenerForSingleValueEvent(object:ValueEventListener{
            override fun onDataChange(p0: DataSnapshot) {
                if (p0.exists())
                {
                    for (restaurantSnapshot in p0.children)
                    {
                        val restaurantModel = restaurantSnapshot.getValue(RestaurantModel::class.java)
                        restaurantModel!!.uid = restaurantSnapshot.key!!
                        restaurantModels.add(restaurantModel)
                    }
                    if (restaurantModels.size > 0)
                        listener.onRestaurantLoadSuccess(restaurantModels)
                    else
                        listener.onRestaurantLoadFailed("Restaurant list empty")
                }
                else
                {
                    listener.onRestaurantLoadFailed("Restaurant list not found")
                }
            }

            override fun onCancelled(p0: DatabaseError) {
                listener.onRestaurantLoadFailed(p0.message)
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
        recyclerRestaurant = findViewById(R.id.recycler_restaurant)
        recyclerRestaurant.layoutManager = layoutManager
        recyclerRestaurant.addItemDecoration(DividerItemDecoration(this,layoutManager.orientation))
    }

    override fun onRestaurantLoadSuccess(restaurantList: List<RestaurantModel>) {
        dialog.dismiss()
        adapter = MyRestaurantAdapter(this,restaurantList)
        recyclerRestaurant.adapter = adapter!!
        recyclerRestaurant.layoutAnimation = layoutAnimationController
    }

    override fun onRestaurantLoadFailed(message: String) {
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
    fun onRestaurantSelectEvent(restaurantSelectEvent: RestaurantSelectEvent)
    {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null)
            checkServerUserFromFirebase(user,restaurantSelectEvent.restaurantModel)
    }

    private fun checkServerUserFromFirebase(user: FirebaseUser, restaurantModel: RestaurantModel) {
        dialog.show()
        serverRef = FirebaseDatabase.getInstance()
            .getReference(Common.RESTAURANT_REF)
            .child(restaurantModel.uid)
            .child(Common.SHIPPER_REF)

        serverRef!!.child(user.uid)
            .addListenerForSingleValueEvent(object:ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {
                    if (p0.exists())
                    {
                        val userModel = p0.getValue(ShipperUserModel::class.java)
                        if (userModel!!.isActive)
                            goToHomeActivity(userModel,restaurantModel)
                        else
                        {
                            dialog.dismiss()
                            Toast.makeText(this@RestaurantListActivity,"You must be allowed by Server app",Toast.LENGTH_SHORT).show()
                        }
                    }
                    else
                    {
                        dialog.dismiss()
                        showRegisterDialog(user,restaurantModel.uid)
                    }
                }

                override fun onCancelled(p0: DatabaseError) {
                    dialog.dismiss()
                    Toast.makeText(this@RestaurantListActivity,p0.message,Toast.LENGTH_SHORT).show()
                }

            })
    }

    private fun showRegisterDialog(user: FirebaseUser, uid: String) {
        val builder = AlertDialog.Builder(this@RestaurantListActivity)
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
                serverRef = FirebaseDatabase.getInstance().getReference(Common.RESTAURANT_REF)
                    .child(uid)
                    .child(Common.SHIPPER_REF)
                serverRef!!.child(serverUserModel.uid!!)
                    .setValue(serverUserModel)
                    .addOnFailureListener { e ->
                        dialog.dismiss()
                        Toast.makeText(
                            this@RestaurantListActivity,
                            "" + e.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnCompleteListener {
                        dialog.dismiss()
                        Toast.makeText(
                            this@RestaurantListActivity,
                            "Register success! Admin will check and active user soon",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }

        builder.setView(itemView)

        val registerDialog = builder.create()
        registerDialog.show()
    }

    private fun goToHomeActivity(userModel: ShipperUserModel,restaurantModel: RestaurantModel) {
        dialog.dismiss()
        Common.currentShipperUser = userModel
        val jsonEncode = Gson().toJson(restaurantModel) //encode all information
        Paper.init(this)
        Paper.book().write(Common.RESTAURANT_SAVE,jsonEncode)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}