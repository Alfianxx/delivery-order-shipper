package com.alfian.deliveryordershipper.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfian.deliveryordershipper.R
import com.alfian.deliveryordershipper.callback.IRecyclerItemClickListener
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.ShopModel
import com.alfian.deliveryordershipper.model.eventbus.ShopSelectEvent
import com.bumptech.glide.Glide
import org.greenrobot.eventbus.EventBus

class MyShopAdapter (internal var context: Context,
                     internal var shopList: List<ShopModel>) :
    RecyclerView.Adapter<MyShopAdapter.MyViewHolder>()  {

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        override fun onClick(view: View?) {
            listener!!.onItemClick(view!!,adapterPosition)
        }

        var txtShopName: TextView?=null
        var txtShopAddress: TextView?=null
        var imgShop: ImageView?=null

        private var listener: IRecyclerItemClickListener?=null

        fun setListener(listener: IRecyclerItemClickListener)
        {
            this.listener = listener
        }

        init{
            txtShopName = itemView.findViewById(R.id.txt_shop_name) as TextView
            txtShopAddress = itemView.findViewById(R.id.txt_shop_address) as TextView
            imgShop = itemView.findViewById(R.id.img_shop) as ImageView
            itemView.setOnClickListener(this)
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyShopAdapter.MyViewHolder {
        return MyViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_shop,parent,false))
    }

    override fun onBindViewHolder(holder: MyShopAdapter.MyViewHolder, position: Int) {
        Glide.with(context).load(R.drawable.img_shop)
            .into(holder.imgShop!!)
        holder.txtShopName!!.text = shopList[position].name
        holder.txtShopAddress!!.text = shopList[position].address

        holder.setListener(object : IRecyclerItemClickListener{
            override fun onItemClick(view: View, pos: Int) {
                //code late
                Common.currentShop = shopList[pos]
                EventBus.getDefault().postSticky(ShopSelectEvent(shopList[pos]))
            }

        })
    }

    override fun getItemCount(): Int {
        return shopList.size
    }

}