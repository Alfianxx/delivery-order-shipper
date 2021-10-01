package com.alfian.deliveryordershipper.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfian.deliveryordershipper.R
import com.alfian.deliveryordershipper.ShippingActivity
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.ShippingOrderModel
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import io.paperdb.Paper
import java.text.SimpleDateFormat

class MyShippingOrderAdapter(var context: Context,
                             private var shippingOrderModelList:List<ShippingOrderModel>) : RecyclerView.Adapter<MyShippingOrderAdapter.MyViewHolder>() {

    private var simpleDateFormat:SimpleDateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss")

    init {
        Paper.init(context)
    }

    inner class MyViewHolder(itemView: View):RecyclerView.ViewHolder(itemView)
    {
        var txtDate:TextView = itemView.findViewById(R.id.txt_date) as TextView
        var txtOrderAddress:TextView = itemView.findViewById(R.id.txt_order_address) as TextView
        var txtOrderNumber:TextView = itemView.findViewById(R.id.txt_order_number) as TextView
        var txtPayment:TextView = itemView.findViewById(R.id.txt_payment) as TextView
        var imgFood:ImageView = itemView.findViewById(R.id.img_food) as ImageView
        var btnShipNow:MaterialButton = itemView.findViewById(R.id.btn_ship_now) as MaterialButton

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(context).inflate(R.layout.layout_shipping_order,parent,false)
        return MyViewHolder(itemView)
    }

    override fun getItemCount(): Int {
        return shippingOrderModelList.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        Glide.with(context)
            .load(
                shippingOrderModelList[position]
                    .orderModel!!.cartItemList!![0].foodImage)
            .into(holder.imgFood)
        holder.txtDate.text = StringBuilder(simpleDateFormat.format(shippingOrderModelList[position].orderModel!!.createDate))
        Common.setSpanStringColor("No.: ",shippingOrderModelList[position].orderModel!!.key,
        holder.txtOrderNumber,Color.parseColor("#BA454A"))

        Common.setSpanStringColor("Address.: ",shippingOrderModelList[position].orderModel!!.shippingAddress,
            holder.txtOrderAddress,Color.parseColor("#BA454A"))

        Common.setSpanStringColor("Payment.: ",shippingOrderModelList[position].orderModel!!.transactionId,
            holder.txtPayment,Color.parseColor("#BA454A"))

        if (shippingOrderModelList[position].isStartTrip)
        {
            holder.btnShipNow.isEnabled=false
        }

        //Event
        holder.btnShipNow.setOnClickListener {

            //Write data
            Paper.book().write(Common.SHIPPING_DATA, Gson().toJson(shippingOrderModelList[0]))

            context.startActivity(Intent(context, ShippingActivity::class.java))
        }
    }
}