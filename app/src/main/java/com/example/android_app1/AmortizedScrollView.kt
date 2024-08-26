package com.example.android_app1
import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView
class AmortizedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var amortizationThreshold = 30 // Start amortization when scrolling within *30* px of the bottom
    private var scrollFactor = 1.0 // scroll speed increase factor, doesn't seem to have much effect at this amortization threshold but its there

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)

        val maxScrollY = getChildAt(0).measuredHeight - height
        val scrollRemaining = maxScrollY - scrollY

        if (scrollRemaining <= amortizationThreshold) {
            val additionalScroll = ((amortizationThreshold - scrollRemaining) * scrollFactor).toInt()
            scrollBy(0, additionalScroll)
        }
    }
}