package com.licrafter.scrolllayout.view;

import android.content.Context;
import android.os.Build;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.Scroller;

import com.licrafter.scrolllayout.HomeFragment;
import com.licrafter.scrolllayout.R;

/**
 * author: shell
 * date 16/10/11 下午2:27
 **/
public class StickyLayout extends LinearLayout {

    private ViewPager mContentView;
    private TabLayout mStickyView;
    private LinearLayout mHeaderView;

    private float mLastY;
    private int mLastScrollerY;
    private int mHeaderHeight;
    //头部是否已经隐藏
    private boolean mIsSticky;
    //内嵌滚动控件是否受用户手势的操控
    private boolean mIsControlled;
    //整体布局是否被拖拽
    private boolean mIsDragging;

    private Scroller mScroller;
    private int mTouchSlop;
    private VelocityTracker mVelocityTracker;
    private int mMaximumVelocity, mMinimumVelocity;
    private DIRECTION mDirection;

    enum DIRECTION {
        UP, DOWN
    }

    public StickyLayout(Context context) {
        this(context, null);
    }

    public StickyLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StickyLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mMaximumVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
        mMinimumVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mScroller = new Scroller(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContentView = findViewById(R.id.goodsViewPager);
        mHeaderView = findViewById(R.id.header);
        mStickyView = findViewById(R.id.tabLayout);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //测量header高度,设置 StickyLayout 的高度
        mHeaderHeight = mHeaderView.getMeasuredHeight() - mStickyView.getMeasuredHeight();
        int newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) + mHeaderHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        acquireVelocityTracker(ev);
        float y = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dy = mLastY - y;
                //滑动有效 && 向下滑 && 内嵌滚动控件滑到了顶端 && 内嵌滚动控件受控制
                if (slideValidity(dy) && dy < 0 && isRecyclerViewTop(getRecyclerView()) && mIsControlled) {
                    mLastY = y;
                    mIsControlled = false;
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
                break;
            case MotionEvent.ACTION_UP:
                //惯性滑动
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityY = (int) mVelocityTracker.getYVelocity();
                mDirection = velocityY < 0 ? DIRECTION.UP : DIRECTION.DOWN;
                if (Math.abs(velocityY) > mMinimumVelocity) {
                    fling(-velocityY);
                }
                recycleVelocityTracker();
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        float y = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //正在滑动 && 头部没有隐藏 && (列表滑动到了头部 || 向上滑)   DOWN事件停止scroller
                if (!mScroller.isFinished() && !mIsSticky && (isRecyclerViewTop(getRecyclerView()) || mDirection == DIRECTION.UP)) {
                    mScroller.abortAnimation();
                    return true;
                }
                mLastY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                //非粘性状态列表没有滑动到顶部,向上滑||非粘性状态列表滑动到顶部 这2种情况都需要拦截滑动事件
                float dy = mLastY - y;
                if (slideValidity(dy) && !mIsSticky && (!isRecyclerViewTop(getRecyclerView()) && dy > 0
                        || isRecyclerViewTop(getRecyclerView()))) {
                    mLastY = y;
                    return true;
                } else if (slideValidity(dy)) {
                    mIsControlled = true;
                    mIsDragging = false;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = mLastY - y;
                if (slideValidity(dy) && !mIsDragging) {
                    mIsDragging = true;
                }
                if (mIsDragging) {
                    scrollBy(0, (int) (dy + 0.5));
                    //当滑动到黏性状态后转发事件模拟再次点击事件
                    if (mIsSticky) {
                        event.setAction(MotionEvent.ACTION_DOWN);
                        dispatchTouchEvent(event);
                        event.setAction(MotionEvent.ACTION_CANCEL);
                    }
                    mLastY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
                mIsDragging = false;
                break;
        }
        return super.onTouchEvent(event);
    }

    public void fling(int velocityY) {
        mScroller.fling(0, getScrollY(), 0, velocityY, 0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        mScroller.computeScrollOffset();
        mLastScrollerY = mScroller.getCurrY();
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int currY = mScroller.getCurrY();
            if (mDirection == DIRECTION.UP) {
                if (mIsSticky) {
                    int distance = mScroller.getFinalY() - currY;
                    int duration = mScroller.getDuration() - mScroller.timePassed();
                    getRecyclerView().fling(0, getScrollerVelocity(distance, duration));
                    mScroller.abortAnimation();
                } else {
                    scrollTo(0, currY);
                }
            } else {
                if (isRecyclerViewTop(getRecyclerView())) {
                    int delta = currY - mLastScrollerY;
                    int toY = getScrollY() + delta;
                    scrollTo(0, toY);
                    if (getScrollY() == 0 && !mScroller.isFinished()) {
                        mScroller.abortAnimation();
                    }
                }
                invalidate();
            }
            mLastScrollerY = currY;
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        if (y < 0) {
            y = 0;
        }
        if (y > mHeaderHeight) {
            y = mHeaderHeight;
        }
        mIsSticky = y == mHeaderHeight;
        super.scrollTo(x, y);
    }

    private boolean isRecyclerViewTop(RecyclerView recyclerView) {
        if (recyclerView != null) {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                int firstVisibleItemPosition = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                View childAt = recyclerView.getChildAt(0);
                return childAt == null || (firstVisibleItemPosition == 0 && childAt.getTop() == 0);
            }
        }
        return false;
    }

    private RecyclerView getRecyclerView() {
        HomeFragment.GoodsPageAdapter adapter = (HomeFragment.GoodsPageAdapter) mContentView.getAdapter();
        RecyclerView view = null;
        if (adapter != null) {
            view = (RecyclerView) adapter.getFragment(mContentView.getCurrentItem()).getView();
        }
        return view;
    }

    private int getScrollerVelocity(int distance, int duration) {
        if (mScroller == null) {
            return 0;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return (int) mScroller.getCurrVelocity();
        } else {
            return distance / duration;
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void acquireVelocityTracker(final MotionEvent event) {
        if (null == mVelocityTracker) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    private boolean slideValidity(float dy) {
        return Math.abs(dy) > mTouchSlop;
    }

}
