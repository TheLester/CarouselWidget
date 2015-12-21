package com.appl.library;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.RelativeLayout;
import android.widget.Scroller;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

/**
 * @author Martin Appl (appl.m@seznam.cz)
 */
public class Carousel extends ViewGroup {
    private static final String TAG = "Carousel";

    protected final int NO_VALUE = Integer.MIN_VALUE + 1777;

    /**
     * Children added with this layout mode will be added after the last child
     */
    protected static final int LAYOUT_MODE_AFTER = 0;

    /**
     * Children added with this layout mode will be added before the first child
     */
    protected static final int LAYOUT_MODE_TO_BEFORE = 1;

    /**
     * User is not touching the list
     */
    protected static final int TOUCH_STATE_RESTING = 0;

    /**
     * User is scrolling the list
     */
    protected static final int TOUCH_STATE_SCROLLING = 1;

    /**
     * Fling gesture in progress
     */
    protected static final int TOUCH_STATE_FLING = 2;

    /** Aligning in progress */
    protected static final int TOUCH_STATE_ALIGN = 3;

    private final Scroller mScroller = new Scroller(getContext());
    private VelocityTracker mVelocityTracker;
    protected int mTouchSlop;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
//    private float mLastMotionX;
    private float mLastMotionY;


    protected int mTouchState = TOUCH_STATE_RESTING;

    private final DataSetObserver mDataObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            reset();
        }

        @Override
        public void onInvalidated() {
            removeAllViews();
            invalidate();
        }

    };

    /**
     * Relative spacing value of Views in container. If <1 Views will overlap, if >1 Views will have spaces between them
     */
    protected float mSpacing = 0.5f;
    /**
     * Index of view in center of screen, which is most in foreground
     */
    private int mReverseOrderIndex = -1;
    /**
     * Movement speed will be divided by this coefficient;
     */
    private int mSlowDownCoefficient = 1;

    protected int mChildWidth = 360;
    protected int mChildHeight = 240;

    private int mSelection;
    protected Adapter mAdapter;

    private int mFirstVisibleChild;
    private int mLastVisibleChild;

    protected final ViewCache<View> mCache = new ViewCache<>();

//    protected int mRightEdge = NO_VALUE;
//    protected int mLeftEdge = NO_VALUE;

    protected int mTopEdge = NO_VALUE;
    protected int mBottomEdge = NO_VALUE;


    private OnItemSelectedListener mOnItemSelectedListener;

    public Carousel(Context context) {
        this(context, null);
    }

    public Carousel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Carousel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setChildrenDrawingOrderEnabled(true);
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity()/10;
    }

    public Adapter getAdapter() {
        return mAdapter;
    }

    public void setAdapter(Adapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mDataObserver);
        }
        mAdapter = adapter;
        mAdapter.registerDataSetObserver(mDataObserver);
        reset();
    }

    public View getSelectedView() {
        return getChildAt(mReverseOrderIndex);
    }

    public int getSelection() {
        return mSelection;
    }

    public void setSelection(int position) {
        if (mAdapter == null)
            throw new IllegalStateException("You are trying to set selection on widget without adapter");
        if (position < 0 || position > mAdapter.getCount() - 1)
            throw new IllegalArgumentException("Position index must be in range of adapter values (0 - getCount()-1)");

        mSelection = position;

        reset();
    }

    @Override
    public void computeScroll() {
        final int centerItemTop = getHeight() / 2 - mChildHeight / 2;
        final int centerItemBottom = getHeight() / 2 + mChildHeight / 2;

        if (mTopEdge != NO_VALUE && mScroller.getFinalY() > mTopEdge - centerItemTop) {
            mScroller.setFinalY(mTopEdge - centerItemTop);
        }
        if (mBottomEdge != NO_VALUE && mScroller.getFinalY() < mBottomEdge - centerItemBottom) {
            mScroller.setFinalY(mBottomEdge - centerItemBottom);
        }

        if (mScroller.computeScrollOffset()) {
            if (mScroller.getFinalY() == mScroller.getCurrY()) {
                mScroller.abortAnimation();
                mTouchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
            } else {
                final int y = mScroller.getCurrY();
                scrollTo(0, y);

                postInvalidate();
            }
        } else if (mTouchState == TOUCH_STATE_FLING) {
            mTouchState = TOUCH_STATE_RESTING;
            clearChildrenCache();
        }

        refill();
        updateReverseOrderIndex();
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mAdapter == null || mAdapter.getCount() == 0) {
            return;
        }
        View v = null;
        if (getChildCount() == 0) {
            v = getViewFromAdapter(mSelection);
            addAndMeasureChild(v, LAYOUT_MODE_AFTER);
            //todo check
            final int horizontalCenter = getWidth() / 2;
            final int verticalCenter = getHeight() / 2;
            final int left = horizontalCenter - v.getMeasuredWidth() / 2;
            final int right = left + v.getMeasuredWidth();
            final int top = verticalCenter - v.getMeasuredHeight() / 2;
            final int bottom = top + v.getMeasuredHeight();
            v.layout(left, top, right, bottom);

            mFirstVisibleChild = mSelection;
            mLastVisibleChild = mSelection;

            if (mLastVisibleChild == mAdapter.getCount() - 1) {
                mTopEdge = top;
            }
            if (mFirstVisibleChild == 0) {
                mBottomEdge = bottom;
            }
        }

        refill();

        if (v != null) {
            mReverseOrderIndex = indexOfChild(v);
            v.setSelected(true);
        } else {
            updateReverseOrderIndex();
        }
    }

    private void updateReverseOrderIndex() {
        int oldReverseIndex = mReverseOrderIndex;
        final int screenCenter = getScrollY() - getHeight() / 2 ;
        final int c = getChildCount();

        int minDiff = Integer.MAX_VALUE;
        int minDiffIndex = -1;

        int viewCenter, diff;
        for (int i = 0; i < c; i++) {
            viewCenter = getChildCenter(i);
            diff = Math.abs(screenCenter - viewCenter);
            if (diff < minDiff) {
                minDiff = diff;
                minDiffIndex = i;
            }
        }

        if (minDiff != Integer.MAX_VALUE) {
            mReverseOrderIndex = minDiffIndex;
        }

        if (oldReverseIndex != mReverseOrderIndex) {
            Log.i(TAG, "updateReverseOrderIndex: " +oldReverseIndex+" / "+mReverseOrderIndex);
            View oldSelected = getChildAt(oldReverseIndex);
            View newSelected = getChildAt(mReverseOrderIndex);

           if(oldSelected!=null){
               oldSelected.setSelected(false);
           }
            if (newSelected != null) {
                newSelected.setSelected(true);
            }

            mSelection = mFirstVisibleChild + mReverseOrderIndex;
            if (mOnItemSelectedListener != null) {
                mOnItemSelectedListener.onItemSelected(newSelected, mSelection);
            }
        }

    }

    /**
     * Layout children from right to left
     */
    protected int layoutChildToBefore(View v, int top) {
//        final int verticalCenter = getHeight() / 2;
        final int horizontalCenter = getWidth() / 2;
        //TODO check

        int l, t, r, b;
        l = horizontalCenter - v.getMeasuredWidth()/2;
        t = top;

        r = horizontalCenter + v.getMeasuredWidth() /2;
        b = top + v.getMeasuredHeight();

        v.layout(l, t, r, b);
        return t - (int)(v.getMeasuredHeight() * mSpacing);
    }

    /**
     * @param left X coordinate where should we start layout
     */
    protected int layoutChild(View v, int bottom) {
        final int horizontalCenter = getWidth() / 2;
    //TODO check
        int l, t, r, b;

        l = horizontalCenter - v.getMeasuredWidth()/2;
        t = bottom - v.getMeasuredHeight();

        r = horizontalCenter + v.getMeasuredWidth()/2;
        b = bottom;

        v.layout(l, t, r, b);
        return b + (int)(v.getMeasuredHeight() * mSpacing);
    }

    /**
     * Adds a view as a child view and takes care of measuring it
     *
     * @param child      The view to add
     * @param layoutMode Either LAYOUT_MODE_LEFT or LAYOUT_MODE_RIGHT
     * @return child which was actually added to container, subclasses can override to introduce frame views
     */
    protected View addAndMeasureChild(final View child, final int layoutMode) {
        if (child.getLayoutParams() == null){
            LayoutParams params = new LayoutParams(mChildWidth,
                    mChildHeight);
            child.setLayoutParams(params);
        }
        Log.i(TAG, "addAndMeasureChild: "+getChildCount());
        final int index = layoutMode == LAYOUT_MODE_TO_BEFORE ? 0 : -1;
        addViewInLayout(child, index, child.getLayoutParams(), true);

        final int pwms = MeasureSpec.makeMeasureSpec(mChildWidth, MeasureSpec.EXACTLY);
        final int phms = MeasureSpec.makeMeasureSpec(mChildHeight, MeasureSpec.EXACTLY);
        measureChild(child, pwms, phms);
        child.setDrawingCacheEnabled(isChildrenDrawnWithCacheEnabled());

        return child;
    }

    /**
     * Remove all data, reset to initial state and attempt to refill
     */
    private void reset() {
        if(mAdapter == null || mAdapter.getCount() == 0){
            return;
        }

        if(getChildCount() == 0){
            requestLayout();
            return;
        }

        View selectedView = getChildAt(mReverseOrderIndex);
        int selectedBottom = selectedView.getBottom();
        int selectedTop = selectedView.getTop();


        removeAllViewsInLayout();
        mTopEdge = NO_VALUE;
        mBottomEdge = NO_VALUE;

        View v = mAdapter.getView(mSelection, null, this);
        addAndMeasureChild(v, LAYOUT_MODE_AFTER);
        mReverseOrderIndex = 0;

        final int top = selectedBottom + v.getMeasuredHeight();
        final int bottom = selectedTop + v.getMeasuredHeight();
        v.layout(selectedBottom, selectedTop, top, bottom);

        mFirstVisibleChild = mSelection;
        mLastVisibleChild = mSelection;

        if (mLastVisibleChild == mAdapter.getCount() - 1) {
            mTopEdge = top;
        }
        if (mFirstVisibleChild == 0) {
            mBottomEdge = selectedBottom;
        }

        refill();

        mReverseOrderIndex = indexOfChild(v);
        v.setSelected(true);
    }

    protected void refill() {
        if (mAdapter == null || getChildCount() == 0) return;
        //todo
//        final int topScreenEdge = getScrollY();
//        int bottomScreenEdge = topScreenEdge - getHeight();
        final int topScreenEdge = getScrollY()==0 ? getHeight() :getScrollY();//getScrollY()+getHeight();
        int bottomScreenEdge = topScreenEdge - getHeight();

        Log.i(TAG, "refillt: "+topScreenEdge);
        Log.i(TAG, "refillb: "+bottomScreenEdge);

//        removeNonVisibleViewsBottomToTop(bottomScreenEdge);
//        removeNonVisibleViewsTopToBottom(topScreenEdge);

        refillBottomToTop(topScreenEdge);
        refillTopToBottom(bottomScreenEdge);
    }

    protected int getPartOfViewCoveredBySibling(){
        return (int)(mChildHeight * (1.0f - mSpacing));//todo check
    }

    protected View getViewFromAdapter(int position){
        return mAdapter.getView(position, mCache.getCachedView(), this);
    }

    /**
     * Checks and refills empty area on the left
     *
     * @return firstItemPosition
     */
    protected void refillTopToBottom(final int bottomScreenEdge) {
        if (getChildCount() == 0) return;

        View child = getChildAt(0);
        int childTop = child.getTop();
        int newTop = childTop - (int)(mChildHeight * mSpacing);

        while (newTop - getPartOfViewCoveredBySibling() > bottomScreenEdge && mFirstVisibleChild > 0) {
            mFirstVisibleChild--;
           // Log.i(TAG, "refillTopToBottom: "+mFirstVisibleChild);
            child = getViewFromAdapter(mFirstVisibleChild);
            child.setSelected(false);
            mReverseOrderIndex++;

            addAndMeasureChild(child, LAYOUT_MODE_TO_BEFORE);
            newTop = layoutChildToBefore(child, newTop);

            if (mFirstVisibleChild <= 0) {
                mBottomEdge = child.getBottom();
            }
        }
        return;
    }

    /**
     * Checks and refills empty area on the top
     */
    protected void refillBottomToTop(final int topScreenEdge) {

        View child;
        int newBottom;

        int index = 0;
//        Log.i(TAG, "index: "+index);
        child = getChildAt(index);
        int childBottom = child.getBottom();

        newBottom = childBottom + (int)(mChildHeight * mSpacing);
        Log.i(TAG, "refillLeftToRight: "+newBottom +" / "+" /r "+topScreenEdge+" / "+(mLastVisibleChild < mAdapter
                .getCount() - 1));
        while (newBottom + getPartOfViewCoveredBySibling() < topScreenEdge && mLastVisibleChild < mAdapter
            .getCount() - 1) {
            mLastVisibleChild++;
//            Log.i(TAG, "refillBottomToTop: "+mLastVisibleChild);
            child = getViewFromAdapter(mLastVisibleChild);
            child.setSelected(false);

            addAndMeasureChild(child, LAYOUT_MODE_AFTER);
            newBottom = layoutChild(child, newBottom);

            if (mLastVisibleChild >= mAdapter.getCount() - 1) {
                mTopEdge = child.getTop();
            }
        }
    }


    /**
     * Remove non visible views from left edge of screen
     */
    protected void removeNonVisibleViewsBottomToTop(final int bottomScreenEdge) {
        if (getChildCount() == 0) return;
        //todo check

        // check if we should remove any views in the left
        View firstChild = getChildAt(0);

        while (firstChild != null && firstChild.getBottom()+(mChildHeight * mSpacing)  < bottomScreenEdge && getChildCount() > 1) {

            // remove view
            removeViewsInLayout(0, 1);

            mCache.cacheView(firstChild);

            mFirstVisibleChild++;
            mReverseOrderIndex--;

            if (mReverseOrderIndex == 0) {
                break;
            }

            // Continue to check the next child only if we have more than
            // one child left
            if (getChildCount() > 1) {
                firstChild = getChildAt(0);
            } else {
                firstChild = null;
            }
        }

    }

    /**
     * Remove non visible views from right edge of screen
     */
    protected void removeNonVisibleViewsTopToBottom(final int topScreenEdge) {
        if (getChildCount() == 0) return;
        //todo check
        // check if we should remove any views in the right
        View lastChild = getChildAt(getChildCount() - 1);
        while (lastChild != null && lastChild.getTop() - (mChildHeight * mSpacing)  > topScreenEdge &&
            getChildCount() > 1) {
            // remove the right view
            removeViewsInLayout(getChildCount() - 1, 1);

            mCache.cacheView(lastChild);

            mLastVisibleChild--;
            if (getChildCount() - 1 == mReverseOrderIndex) {
                break;
            }

            // Continue to check the next child only if we have more than
            // one child left
            if (getChildCount() > 1) {
                lastChild = getChildAt(getChildCount() - 1);
            } else {
                lastChild = null;
            }
        }

    }

    protected int getChildCenter(View v) {
        final int h = v.getTop() - v.getBottom();
        return v.getBottom() + h / 2;
    }

    protected int getChildCenter(int i) {
        return getChildCenter(getChildAt(i));
    }

//    @Override
//    protected void dispatchDraw(Canvas canvas) {
//        super.dispatchDraw(canvas);
//    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (i < mReverseOrderIndex) {
            return i;
        } else {
            return childCount - 1 - (i - mReverseOrderIndex);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */


        /*
         * Shortcut the most recurring case: the user is in the dragging
         * state and he is moving his finger.  We want to intercept this
         * motion.
         */
        final int action = ev.getAction();
        if ((action == MotionEvent.ACTION_MOVE) && (mTouchState == TOUCH_STATE_SCROLLING)) {
            return true;
        }

        final float x = ev.getX();
        final float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                /*
                 * not dragging, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                 * Locally do absolute value. mLastMotionX is set to the x value
                 * of the down event.
                 */
                //diff!
                final int yDiff = (int)Math.abs(y - mLastMotionY);

                final int touchSlop = mTouchSlop;
                final boolean yMoved = yDiff > touchSlop;

                if (yMoved) {
                    // Scroll if the user moved far enough along the axis
                    mTouchState = TOUCH_STATE_SCROLLING;
                    enableChildrenCache();
                    cancelLongPress();
                }

                break;

            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mLastMotionY = y;

                /*
                 * If being flinged and user touches the screen, initiate drag;
                 * otherwise don't.  mScroller.isFinished should be false when
                 * being flinged.
                 */
                mTouchState = mScroller.isFinished() ? TOUCH_STATE_RESTING : TOUCH_STATE_SCROLLING;
                break;

            case MotionEvent.ACTION_UP:
                mTouchState = TOUCH_STATE_RESTING;
                clearChildrenCache();
                break;
        }

        return mTouchState == TOUCH_STATE_SCROLLING;

    }

    protected void scrollByDelta(int deltaY) {
        deltaY /= mSlowDownCoefficient;

        final int centerItemBottom = getHeight() / 2 - mChildHeight / 2;
        final int centerItemTop = getHeight() / 2 + mChildHeight / 2;

        final int topInPixels;
        final int bottomInPixels;
        if (mTopEdge == NO_VALUE) {
            topInPixels = Integer.MAX_VALUE;
        } else {
            topInPixels = mTopEdge;
        }

        if (mBottomEdge == NO_VALUE) {
            bottomInPixels = Integer.MIN_VALUE + getHeight(); //we cant have min value because of integer overflow
        } else {
            bottomInPixels = mBottomEdge;
        }

        final int y = getScrollY() + deltaY;

        if (y < (bottomInPixels - centerItemBottom)) {
            deltaY -= y - (bottomInPixels - centerItemBottom);
        } else if (y > topInPixels - centerItemTop) {
            deltaY -= y - (topInPixels - centerItemTop);
        }

        scrollBy(0, deltaY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            /*
             * If being flinged and user touches, stop the fling. isFinished
             * will be false if being flinged.
             */
                if (!mScroller.isFinished()) {
                    mScroller.forceFinished(true);
                }

                // Remember where the motion event started
                mLastMotionY = y;

                break;
            case MotionEvent.ACTION_MOVE:

                if (mTouchState == TOUCH_STATE_SCROLLING) {
                    // Scroll to follow the motion event
                    final int deltaY = (int)(mLastMotionY - y);
                    mLastMotionY = y;

                    scrollByDelta(deltaY);
                } else {
                    final int yDiff = (int)Math.abs(y - mLastMotionY);

                    final int touchSlop = mTouchSlop;
                    final boolean yMoved = yDiff > touchSlop;


                    if (yMoved) {
                        // Scroll if the user moved far enough along the axis
                        mTouchState = TOUCH_STATE_SCROLLING;
                        enableChildrenCache();
                        cancelLongPress();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                //if we had normal down click and we haven't moved enough to initiate drag, take action as a click on down coordinates
                if (mTouchState == TOUCH_STATE_SCROLLING) {

                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int initialXVelocity = (int)mVelocityTracker.getXVelocity();
                    int initialYVelocity = (int)mVelocityTracker.getYVelocity();

                    if (Math.abs(initialXVelocity) + Math.abs(initialYVelocity) > mMinimumVelocity) {
                        fling(-initialXVelocity, -initialYVelocity);
                    } else {
                        // Release the drag
                        clearChildrenCache();
                        mTouchState = TOUCH_STATE_RESTING;
                    }

                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                        mVelocityTracker = null;
                    }

                    break;
                }

                // Release the drag
                clearChildrenCache();
                mTouchState = TOUCH_STATE_RESTING;

                break;
            case MotionEvent.ACTION_CANCEL:
                mTouchState = TOUCH_STATE_RESTING;
        }

        return true;
    }

    public void fling(int velocityX, int velocityY) {
        velocityX /= mSlowDownCoefficient;

        mTouchState = TOUCH_STATE_FLING;
        final int x = getScrollX();
        final int y = getScrollY();

        final int centerItemBottom = getHeight() / 2 - mChildHeight / 2;
        final int centerItemTop = getHeight() / 2 + mChildHeight / 2;
        final int topInPixels;
        final int bottomInPixels;
        if (mTopEdge == NO_VALUE) topInPixels = Integer.MAX_VALUE;
        else topInPixels = mTopEdge;
        if (mBottomEdge == NO_VALUE) bottomInPixels = Integer.MIN_VALUE + getHeight();
        else bottomInPixels = mBottomEdge;

        mScroller.fling(x, y, velocityX, velocityY,0,0, bottomInPixels - centerItemBottom,
            topInPixels - centerItemTop + 1);//todo check

        invalidate();
    }

    private void enableChildrenCache() {
        setChildrenDrawnWithCacheEnabled(true);
        setChildrenDrawingCacheEnabled(true);
    }

    private void clearChildrenCache() {
        setChildrenDrawnWithCacheEnabled(false);
    }

    /**
     * Set widget spacing (float means fraction of widget size, 1 = widget size)
     *
     * @param spacing the spacing to set
     */
    public void setSpacing(float spacing) {
        this.mSpacing = spacing;
    }

    public void setChildWidth(int width) {
        mChildWidth = width;
    }

    public void setChildHeight(int height) {
        mChildHeight = height;
    }

    public void setSlowDownCoefficient(int c) {
        if(c < 1) throw new IllegalArgumentException("Slowdown coeficient must be greater than 0");
        mSlowDownCoefficient = c;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener onItemSelectedListener) {
        mOnItemSelectedListener = onItemSelectedListener;
    }

    public interface OnItemSelectedListener {
        void onItemSelected(View child, int position);
    }

    protected static class ViewCache<T extends View> {
        private final LinkedList<WeakReference<T>> mCachedItemViews = new LinkedList<WeakReference<T>>();

        /**
         * Check if list of weak references has any view still in memory to offer for recycling
         *
         * @return cached view
         */
        public T getCachedView() {
            if (mCachedItemViews.size() != 0) {
                T v;
                do {
                    v = mCachedItemViews.removeFirst().get();
                }
                while (v == null && mCachedItemViews.size() != 0);
                return v;
            }
            return null;
        }

        public void cacheView(T v) {
            WeakReference<T> ref = new WeakReference<T>(v);
            mCachedItemViews.addLast(ref);
        }
    }
}
