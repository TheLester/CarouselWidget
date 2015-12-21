package com.appl.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * @author Martin Appl
 */
public class CoverFlowCarousel extends Carousel {
    private static final String TAG = "CoverFlowCarousel";
    /**
     * Widget size on which was tuning of parameters done. This value is used to scale parameters on when widgets has different size
     */
    private int mTuningWidgetSize = 1280;

    /**
     * Distance from center as fraction of half of widget size where covers start to rotate into center
     * 1 means rotation starts on edge of widget, 0 means only center rotated
     */
    private float mRotationThreshold = 0.3f;

    /**
     * Distance from center as fraction of half of widget size where covers start to zoom in
     * 1 means scaling starts on edge of widget, 0 means only center scaled
     */
    private float mScalingThreshold = 0.3f;

    /**
     * Distance from center as fraction of half of widget size,
     * where covers start enlarge their spacing to allow for smooth passing each other without jumping over each other
     * 1 means edge of widget, 0 means only center
     */
    private float mAdjustPositionThreshold = 0.1f;

    /**
     * By enlarging this value, you can enlarge spacing in center of widget done by position adjustment
     */
    private float mAdjustPositionMultiplier = 0.8f;

    /**
     * Absolute value of rotation angle of cover at edge of widget in degrees
     */
    private float mMaxRotationAngle = 70.0f;

    /**
     * Scale factor of item in center
     */
    private float mMaxScaleFactor = 1.2f;

    /**
     * Radius of circle path which covers follow. Range of screen is -1 to 1, minimal radius is therefore 1
     */
    private float mRadius = 2f;

    /**
     * Size multiplier used to simulate perspective
     */
    private float mPerspectiveMultiplier = 1f;

    /**
     * Size of reflection as a fraction of original image (0-1)
     */
    private float mReflectionHeight = 0.5f;

    /**
     * Starting opacity of reflection. Reflection fades from this value to transparency;
     */
    private int mReflectionOpacity = 0x70;

    //reflection
    private final Matrix mReflectionMatrix = new Matrix();
    private final Paint mPaint = new Paint();
    //private final Paint mReflectionPaint = new Paint();
    private final PorterDuffXfermode mXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final Canvas mReflectionCanvas = new Canvas();

    //private boolean mInvalidated = false;

    public CoverFlowCarousel(Context context) {
        super(context);
    }

    public CoverFlowCarousel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CoverFlowCarousel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void setTransformation(View v){
        int c = getChildCenter(v);
//        v.setRotationY(getRotationAngle(c) - getAngleOnCircle(c));
//        v.setTranslationX(getChildAdjustPosition(v));
        v.setRotationX(getRotationAngle(c) - getAngleOnCircle(c));
        v.setTranslationY(getChildAdjustPosition(v));

        float scale = getScaleFactor(c) - getChildCircularPathZOffset(c);
        v.setScaleX(scale);
        v.setScaleY(scale);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int bitmask = Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG;
        canvas.setDrawFilter(new PaintFlagsDrawFilter(bitmask, bitmask));
        super.dispatchDraw(canvas);
    }


    @Override
    public void computeScroll() {
        super.computeScroll();

        for(int i=0; i < getChildCount(); i++){
            setTransformation(getChildAt(i));
        }
    }

    @Override
    protected int getPartOfViewCoveredBySibling() {
        return 0;
    }

    @Override
    protected View getViewFromAdapter(int position){
        CoverFrame frame = (CoverFrame) mCache.getCachedView();
        View recycled = null;
        if(frame != null) {
            recycled = frame.getChildAt(0);
        }

        View v = mAdapter.getView(position, recycled , this);
        if(frame == null) {
            frame = new CoverFrame(getContext(), v);
        } else {
            frame.setCover(v);
        }

        //to enable drawing cache
        if(android.os.Build.VERSION.SDK_INT >= 11) frame.setLayerType(LAYER_TYPE_SOFTWARE, null);
        frame.setDrawingCacheEnabled(true);

        return frame;
    }

    private float getRotationAngle(int childCenter){
        return mMaxRotationAngle * getClampedRelativePosition(getRelativePosition(childCenter), mRotationThreshold * getWidgetSizeMultiplier());
    }

    private float getAngleOnCircle(int childCenter){
        float y = getRelativePosition(childCenter)/mRadius;
        if(y < -1.0f) y = -1.0f;
        if(y > 1.0f) y = 1.0f;
        //todo check
        return (float) (Math.acos(y)/Math.PI*180.0f - 90.0f);
    }

    private float getScaleFactor(int childCenter){
        return 1 + (mMaxScaleFactor-1) * (1 - Math.abs(getClampedRelativePosition(getRelativePosition(childCenter), mScalingThreshold * getWidgetSizeMultiplier())));
    }

    /**
     * Clamps relative position by threshold, and produces values in range -1 to 1 directly usable for transformation computation
     * @param position value int range -1 to 1
     * @param threshold always positive value of threshold distance from center in range 0-1
     * @return
     */
    private float getClampedRelativePosition(float position, float threshold){
        if(position < 0){
            if(position < -threshold) return -1f;
            else return position/threshold;
        }
        else{
            if(position > threshold) return 1;
            else return position/threshold;
        }
    }

    /**
     * Calculates relative position on screen in range -1 to 1, widgets out of screen can have values ove 1 or -1
     * @param pixexPos Absolute position in pixels including scroll offset
     * @return relative position
     */
    private float getRelativePosition(int pixexPos){
        final int half = getHeight()/2;
        final int centerPos = getScrollY() + half;

        return (pixexPos - centerPos)/((float) half);
    }

    private float getWidgetSizeMultiplier(){
        return ((float)mTuningWidgetSize)/((float)getHeight());
    }

    private float getChildAdjustPosition(View child) {
        final int c = getChildCenter(child);
        final float crp = getClampedRelativePosition(getRelativePosition(c), mAdjustPositionThreshold * getWidgetSizeMultiplier());
        final float d = mChildHeight * mAdjustPositionMultiplier * mSpacing * crp * getSpacingMultiplierOnCirlce(c);

        return d;
    }

    private float getSpacingMultiplierOnCirlce(int childCenter){
        float y = getRelativePosition(childCenter)/mRadius;
        return (float) Math.sin(Math.acos(y));
    }

    /**
     * Compute offset following path on circle
     * @param childCenter
     * @return offset from position on unitary circle
     */
    private float getOffsetOnCircle(int childCenter){
        float y = getRelativePosition(childCenter)/mRadius;
        if(y < -1.0f) y = -1.0f;
        if(y > 1.0f) y = 1.0f;

        return (float) (1 - Math.sin(Math.acos(y)));
    }

    private float getChildCircularPathZOffset(int center){

        final float v = getOffsetOnCircle(center);
        final float z = mPerspectiveMultiplier * v;

        return  z;
    }

    private class CoverFrame extends FrameLayout {
        private Bitmap mReflectionCache;
        private boolean mReflectionCacheInvalid = false;


        public CoverFrame(Context context, View cover) {
            super(context);
            setCover(cover);
        }

        public void setCover(View cover){
            removeAllViews();
            //mReflectionCacheInvalid = true; //todo uncomment after adding support for reflection
            if(cover.getLayoutParams() != null) setLayoutParams(cover.getLayoutParams());

            final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            lp.leftMargin = 1;
            lp.topMargin = 1;
            lp.rightMargin = 1;
            lp.bottomMargin = 1;

            if (cover.getParent()!=null && cover.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) cover.getParent();
                parent.removeView(cover);
            }

            addView(cover,lp);
        }
        //todo investigate why we need drawing cache(- only for reflection image?)
        @Override
        public Bitmap getDrawingCache(boolean autoScale) {
            final Bitmap b = super.getDrawingCache(autoScale);

            if(mReflectionCacheInvalid){
                if(/*(mTouchState != TOUCH_STATE_FLING && mTouchState != TOUCH_STATE_ALIGN) || */mReflectionCache == null){
                    try{
                        mReflectionCacheInvalid = false;
                    }
                    catch (NullPointerException e){
                        Log.e(VIEW_LOG_TAG, "Null pointer in createReflectionBitmap. Bitmap b=" + b, e);
                    }
                }
            }
            return b;
        }

        public void recycle(){ //todo add puttocache method and call recycle
            if(mReflectionCache != null){
                mReflectionCache.recycle();
                mReflectionCache = null;
            }
            mReflectionCacheInvalid = true;

            //removeAllViewsInLayout();
        }

    }


}
