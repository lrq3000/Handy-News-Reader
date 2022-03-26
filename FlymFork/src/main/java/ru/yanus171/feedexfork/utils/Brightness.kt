@file:Suppress("FunctionName")

package ru.yanus171.feedexfork.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.widget.TextView
import ru.yanus171.feedexfork.R
import ru.yanus171.feedexfork.utils.PrefUtils.GetTapZoneSize
import ru.yanus171.feedexfork.utils.UiUtils.SetSize
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class Brightness(private val mActivity: Activity, rootView: View) {
    private val mDimFrame: View = rootView.findViewById(R.id.dimFrame)
    private val mInfo: TextView? = rootView.findViewById(R.id.brightnessInfo)
    var mCurrentAlpha : Float = 128F

    init {
        mInfo?.visibility = View.GONE
        mCurrentAlpha = PrefUtils.getFloat(PrefUtils.LAST_BRIGHTNESS_FLOAT, mCurrentAlpha)
        UiUtils.HideButtonText(rootView, R.id.brightnessSliderLeft, true)
        UiUtils.HideButtonText(rootView, R.id.brightnessSliderRight, true)
        SetSize(rootView, R.id.brightnessSliderLeft, (GetTapZoneSize() * 1.5).toInt(), MATCH_PARENT)
        SetSize(rootView, R.id.brightnessSliderRight, (GetTapZoneSize() * 1.5).toInt(), MATCH_PARENT)

        if (PrefUtils.getBoolean(PrefUtils.BRIGHTNESS_GESTURE_ENABLED, false)) {
            val touchListener = object : View.OnTouchListener {
                private var paddingX = 0
                private var paddingY = 0
                private var initialX = 0
                private var initialY = 0
                private var currentX = 0
                private var currentY = 0
                private var mInitialAlpha: Float = 0F

                @SuppressLint("ClickableViewAccessibility", "DefaultLocale")
                override fun onTouch(view1: View, event: MotionEvent): Boolean {

                    when {
                        event.action == MotionEvent.ACTION_DOWN -> {
                            paddingX = 0
                            paddingY = 0
                            initialX = event.x.toInt()
                            initialY = event.y.toInt()
                            currentX = event.x.toInt()
                            currentY = event.y.toInt()
                            mInitialAlpha = mCurrentAlpha
                            //view1.parent.requestDisallowInterceptTouchEvent(false)

                            Dog.v("onTouch ACTION_DOWN")
                            return true
                        }
                        event.action == MotionEvent.ACTION_MOVE -> {

                            currentX = event.x.toInt()
                            currentY = event.y.toInt()
                            paddingX = currentX - initialX
                            paddingY = currentY - initialY

                            if (abs(paddingY) > abs(paddingX) ) {
                                //if( abs(paddingY) <= abs(paddingX) )
                                //    view1.parent.requestDisallowInterceptTouchEvent(true)
                                val delta : Float = paddingY.toFloat() / mDimFrame.height.toFloat()
                                val coeff : Float = max(0.1F, (min(1F, abs(delta))) * 2)
                                var currentAlpha : Float = (mInitialAlpha + delta * 255F * coeff.toDouble().pow(3.0)).toFloat()
                                if (currentAlpha > 255)
                                    currentAlpha = 255F
                                else if (currentAlpha < 1)
                                    currentAlpha = 1F
                                Dog.v("onTouch ACTION_MOVE $paddingX, $paddingY, $delta, $coeff, $currentAlpha")
                                setBrightness(currentAlpha)
                                mInfo?.visibility = View.VISIBLE
                                mInfo?.text = String.format("%s: %.1f %%",
                                        mInfo?.context?.getString(R.string.brightness),
                                        if (currentAlpha >= 255F / 100F) {
                                            (255 - currentAlpha) / 255.toFloat() * 100
                                        } else {
                                            100F
                                        })
                            }
                            return true
                        }
                        event.action == MotionEvent.ACTION_UP -> {
                            mInfo?.visibility = View.GONE
                            return false
                        }
                        else -> {
                            mInfo?.visibility = View.GONE
                            return false
                        }
                    }

                }
            }
            rootView.findViewById<View>(R.id.brightnessSliderLeft)?.setOnTouchListener(touchListener);
            rootView.findViewById<View>(R.id.brightnessSliderRight)?.setOnTouchListener(touchListener);
        }
    }

    fun OnResume() {
        mInfo?.visibility = View.GONE
        if (!PrefUtils.getBoolean(PrefUtils.BRIGHTNESS_GESTURE_ENABLED, false))
            return
        val period = (PrefUtils.getIntFromText("settings_brightness_read_from_system_period_min", 10) * 1000 * 60).toLong()
        val now = Date().time
        var brightness = PrefUtils.getFloat(PrefUtils.LAST_BRIGHTNESS_FLOAT, 0F)
        if (period != 0L && now - PrefUtils.getLong(PrefUtils.LAST_BRIGHTNESS_ONPAUSE_TIME, now) > period)
        //brightness = 255 - (int) (mActivity.getWindow().getAttributes().screenBrightness / (float)255 * 100);
            brightness = 255F - android.provider.Settings.System.getInt(mActivity.contentResolver, SCREEN_BRIGHTNESS, brightness.toInt())
        setBrightness(brightness)
    }

    fun OnPause() {
        PrefUtils.putFloat(PrefUtils.LAST_BRIGHTNESS_FLOAT, mCurrentAlpha)
        PrefUtils.putLong(PrefUtils.LAST_BRIGHTNESS_ONPAUSE_TIME, Date().time)
    }
    //    private int GetAlpha() {
    //        if ( PrefUtils.getBoolean( "brightness_with_dim_activity", false ) )
    //            return mColor.alpha( ( (ColorDrawable)mDimFrame.getBackground() ).getColor() );
    //        else
    //            return 255 - (int) (255 * mActivity.getWindow().getAttributes().screenBrightness);
    //    }

    @SuppressLint("DefaultLocale")
    private fun setBrightness(currentAlpha: Float) {
        mCurrentAlpha = currentAlpha
        Dog.d(String.format("setBrightness currentAlpha=%.2f", currentAlpha))
        if (PrefUtils.getBoolean("brightness_with_dim_activity", false)) {
            SetWindowBrightness(0F)
            val newColor = Color.argb(currentAlpha.toInt(), 0, 0, 0)
            mDimFrame.setBackgroundColor(newColor)
        } else {
            mDimFrame.setBackgroundColor(Color.TRANSPARENT)
            SetWindowBrightness(currentAlpha)
        }
    }

    private fun SetWindowBrightness(currentAlpha: Float) {
        setBrightness(currentAlpha, mActivity.window)
    }

    companion object {

        fun setBrightness(currentAlpha: Float, window: Window) {
            val brightness : Float = if ( currentAlpha > 254 ) { 1F  } else { 255F - currentAlpha }
            //                    if ( Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite( getContext() )) {
            //                        android.provider.Settings.System.putInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
            //                        android.provider.Settings.System.putInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, currentAlpha);
            //                    }
            val lp = window.attributes
            lp.screenBrightness = brightness / 255F
            window.attributes = lp
            //getActivity().getWindow().addFlags( WindowManager.LayoutParams.FLAGS_CHANGED );
        }
    }

}
