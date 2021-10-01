package com.alfian.deliveryordershipper.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alfian.deliveryordershipper.R
import com.alfian.deliveryordershipper.adapter.MyShippingOrderAdapter
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.ShippingOrderModel
import com.alfian.deliveryordershipper.model.eventbus.UpdateShippingOrderEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class HomeFragment : Fragment() {
    
    private var recyclerOrder: RecyclerView?=null
    private var layoutAnimationController : LayoutAnimationController?= null
    private var adapter: MyShippingOrderAdapter?=null

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        initViews(root)
        homeViewModel.messageError.observe(viewLifecycleOwner,
            { s:String-> Toast.makeText(context,s,Toast.LENGTH_SHORT).show() })
        homeViewModel.getOrderModelMutableLiveData(Common.currentShipperUser!!.phone!!)
            .observe(viewLifecycleOwner, { shippingOrderModels:List<ShippingOrderModel> ->
                adapter = MyShippingOrderAdapter(requireContext(),shippingOrderModels)
                recyclerOrder!!.adapter = adapter
                recyclerOrder!!.layoutAnimation = layoutAnimationController
            })
        return root
    }

    private fun initViews(root: View?) {
        recyclerOrder = root!!.findViewById(R.id.recycler_order) as RecyclerView
        recyclerOrder!!.setHasFixedSize(true)
        recyclerOrder!!.layoutManager = LinearLayoutManager(context)
        layoutAnimationController = AnimationUtils.loadLayoutAnimation(context,R.anim.layout_item_from_left)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().hasSubscriberForEvent(UpdateShippingOrderEvent::class.java))
            EventBus.getDefault().removeStickyEvent(UpdateShippingOrderEvent::class.java)
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun pnUpdateShippingOrder(event:UpdateShippingOrderEvent)
    {
        homeViewModel.getOrderModelMutableLiveData(Common.currentShipperUser!!.phone!!)
    }
}