package com.qwe7002.telegram_sms

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat

@Suppress("SameParameterValue")
class FakeStatusBar {
    fun fakeStatusBar(context:Context,window: Window) {
        val decorView = window.decorView as ViewGroup
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)

        val fakeStatusBarView = View(window.context)
        fakeStatusBarView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            getStatusBarHeight(window.context)
        )
        fakeStatusBarView.setBackgroundColor(getColorCompat(context,R.color.colorPrimaryDark))
        rootView.addView(fakeStatusBarView, 0)
    }
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun getStatusBarHeight(context: Context): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun getColorCompat(context: Context, colorRes: Int): Int {
        return ContextCompat.getColor(context, colorRes)
    }
}
