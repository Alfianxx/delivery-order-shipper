package com.alfian.deliveryordershipper.ui.home

import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.alfian.deliveryordershipper.callback.IShippingOrderCallbackListener
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.ShippingOrderModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeViewModel : ViewModel(), IShippingOrderCallbackListener {

    private val orderModelMutableLiveData:MutableLiveData<List<ShippingOrderModel>> =
        MutableLiveData()
    val messageError:MutableLiveData<String> = MutableLiveData()
    private val listener: IShippingOrderCallbackListener

    init {
        listener = this
    }

    fun getOrderModelMutableLiveData(shipperPhone:String):MutableLiveData<List<ShippingOrderModel>>{
        //Fix crash when press back button - put app to background
        if (!TextUtils.isEmpty(shipperPhone))
            loadOrderByShipper(shipperPhone)
        return orderModelMutableLiveData
    }

    private fun loadOrderByShipper(shipperPhone: String) {
        val tempList : MutableList<ShippingOrderModel> = ArrayList()
        val orderRef = FirebaseDatabase.getInstance()
            .getReference(Common.SHOP_REF)
            .child(Common.currentShop!!.uid)
            .child(Common.SHIPPING_ORDER_REF)
            .orderByChild("shipperPhone")
            .equalTo(Common.currentShipperUser!!.phone)

        orderRef.addListenerForSingleValueEvent(object:ValueEventListener{
            override fun onCancelled(p0: DatabaseError) {
                listener.onShippingOrderLoadFailed(p0.message)
            }

            override fun onDataChange(p0: DataSnapshot) {
                for (itemSnapshot in p0.children)
                {
                    val shippingOrder = itemSnapshot.getValue(ShippingOrderModel::class.java)
                    shippingOrder!!.key = itemSnapshot.key
                    tempList.add(shippingOrder)
                }
                listener.onShippingOrderLoadSuccess(tempList)
            }

        })
    }

    override fun onShippingOrderLoadSuccess(shippingOrders: List<ShippingOrderModel>) {
        orderModelMutableLiveData.value = shippingOrders
    }

    override fun onShippingOrderLoadFailed(message: String) {
        messageError.value = message
    }

}