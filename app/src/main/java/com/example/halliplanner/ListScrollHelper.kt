package com.example.halliplanner

import android.view.MotionEvent
import android.widget.ListView

object ListScrollHelper {
    fun enableNestedScrolling(listView: ListView) {
        listView.isNestedScrollingEnabled = true

        var lastY = 0f
        listView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    view.parent?.requestDisallowInterceptTouchEvent(listView.canScrollList(-1) || listView.canScrollList(1))
                }
                MotionEvent.ACTION_MOVE -> {
                    val movingDown = event.y > lastY
                    val canScroll = if (movingDown) listView.canScrollList(-1) else listView.canScrollList(1)
                    view.parent?.requestDisallowInterceptTouchEvent(canScroll)
                    lastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }
}
