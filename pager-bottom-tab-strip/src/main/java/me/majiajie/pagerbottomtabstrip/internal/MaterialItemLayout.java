package me.majiajie.pagerbottomtabstrip.internal;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.majiajie.pagerbottomtabstrip.MaterialMode;
import me.majiajie.pagerbottomtabstrip.ItemController;
import me.majiajie.pagerbottomtabstrip.R;
import me.majiajie.pagerbottomtabstrip.item.MaterialItemView;
import me.majiajie.pagerbottomtabstrip.listener.OnTabItemSelectedListener;

public class MaterialItemLayout extends ViewGroup implements ItemController {

    private final int DEFAULT_SELECTED = 0;

    private final int MATERIAL_BOTTOM_NAVIGATION_ACTIVE_ITEM_MAX_WIDTH;
    private final int MATERIAL_BOTTOM_NAVIGATION_ITEM_MAX_WIDTH;
    private final int MATERIAL_BOTTOM_NAVIGATION_ITEM_MIN_WIDTH;
    private final int MATERIAL_BOTTOM_NAVIGATION_ITEM_HEIGHT;

    private final DelayedAnimationHelper mDelayedAnimationHelper = new DelayedAnimationHelper();

    private List<MaterialItemView> mItems;

    private List<OnTabItemSelectedListener> mListeners = new ArrayList<>();

    private int[] mTempChildWidths;
    private int mItemTotalWidth;

    private int mSelected = -1;
    private int mOldSelected = -1;

    private boolean mShiftingMode;

    //切换背景颜色时使用
    private final int ANIM_TIME = 300;
    private Interpolator mInterpolator;
    private boolean mChangeBackgroundMode;
    private List<Integer> mColors;
    private List<Oval> mOvals;
    private RectF mTempRectF;
    private Paint mPaint;

    //最后手指抬起的坐标
    private float mLastUpX;
    private float mLastUpY;

    public MaterialItemLayout(Context context) {
        this(context,null);
    }

    public MaterialItemLayout(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public MaterialItemLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final Resources res = getResources();

        MATERIAL_BOTTOM_NAVIGATION_ACTIVE_ITEM_MAX_WIDTH = res.getDimensionPixelSize(R.dimen.material_bottom_navigation_active_item_max_width);
        MATERIAL_BOTTOM_NAVIGATION_ITEM_MAX_WIDTH = res.getDimensionPixelSize(R.dimen.material_bottom_navigation_item_max_width);
        MATERIAL_BOTTOM_NAVIGATION_ITEM_MIN_WIDTH = res.getDimensionPixelSize(R.dimen.material_bottom_navigation_item_min_width);
        MATERIAL_BOTTOM_NAVIGATION_ITEM_HEIGHT = res.getDimensionPixelSize(R.dimen.material_bottom_navigation_height);

        //材料设计规范限制最多只能有5个导航按钮
        mTempChildWidths = new int[5];
    }

    /**
     * 初始化方法
     *
     * @param items 按钮集合
     * @param mode  {@link MaterialMode}
     */
    public void initialize(List<MaterialItemView> items,int mode)
    {
        mItems = items;

        //判断是否需要切换背景
        if((mode & MaterialMode.CHANGE_BACKGROUND_COLOR) > 0) {
            //初始化一些成员变量
            mChangeBackgroundMode = true;
            mOvals = new ArrayList<>();
            mColors = new ArrayList<>();
            mInterpolator = new AccelerateDecelerateInterpolator();
            mTempRectF = new RectF();
            mPaint  = new Paint();

            //获取各项的选中颜色，并替换成白色
            for(MaterialItemView v:mItems) {
                mColors.add(v.getCheckedColor());
                v.setCheckedColor(Color.WHITE);
            }

            //设置默认的背景
            setBackgroundColor(mColors.get(DEFAULT_SELECTED));
        } else {
            //设置按钮点击效果
            for(MaterialItemView v:mItems) {
                v.setBackgroundResource(R.drawable.material_item_background);
            }
        }

        //判断是否隐藏文字
        if((mode & MaterialMode.HIDE_TEXT) > 0) {
            mShiftingMode = true;
            for(MaterialItemView v:mItems) {
                v.setShiftingMode(true);
            }
        }

        //添加按钮到布局，并注册点击事件
        int n = mItems.size();
        for (int i = 0; i < n; i++){
            MaterialItemView v = mItems.get(i);
            v.setChecked(false);
            this.addView(v);

            final int finali = i;
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {

                    setSelect(finali,mLastUpX,mLastUpY);
                }
            });
        }

        //默认选中第一项
        mSelected = DEFAULT_SELECTED;
        mItems.get(DEFAULT_SELECTED).setChecked(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //排除空状态
        if (mItems == null || mItems.size() <= 0 ) {
            super.onMeasure(widthMeasureSpec,heightMeasureSpec);
            return;
        }

        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int count = getChildCount();

        final int heightSpec = MeasureSpec.makeMeasureSpec(MATERIAL_BOTTOM_NAVIGATION_ITEM_HEIGHT, MeasureSpec.EXACTLY);

        if (mShiftingMode) {
            final int inactiveCount = count - 1;
            final int activeMaxAvailable = width - inactiveCount * MATERIAL_BOTTOM_NAVIGATION_ITEM_MIN_WIDTH;
            final int activeWidth = Math.min(activeMaxAvailable, MATERIAL_BOTTOM_NAVIGATION_ACTIVE_ITEM_MAX_WIDTH);
            final int inactiveMaxAvailable = (width - activeWidth) / inactiveCount;
            final int inactiveWidth = Math.min(inactiveMaxAvailable, MATERIAL_BOTTOM_NAVIGATION_ITEM_MAX_WIDTH);
            int extra = width - activeWidth - inactiveWidth * inactiveCount;
            for (int i = 0; i < count; i++) {
                mTempChildWidths[i] = (i == mSelected) ? activeWidth : inactiveWidth;
                if (extra > 0) {
                    mTempChildWidths[i]++;
                    extra--;
                }
            }
        } else {
            final int maxAvailable = width / (count == 0 ? 1 : count);
            final int childWidth = Math.min(maxAvailable, MATERIAL_BOTTOM_NAVIGATION_ACTIVE_ITEM_MAX_WIDTH);
            int extra = width - childWidth * count;
            for (int i = 0; i < count; i++) {
                mTempChildWidths[i] = childWidth;
                if (extra > 0) {
                    mTempChildWidths[i]++;
                    extra--;
                }
            }
        }

        mItemTotalWidth = 0;
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(MeasureSpec.makeMeasureSpec(mTempChildWidths[i], MeasureSpec.EXACTLY),
                    heightSpec);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            params.width = child.getMeasuredWidth();
            mItemTotalWidth += child.getMeasuredWidth();
        }

        super.onMeasure(widthMeasureSpec,heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int count = getChildCount();
        final int width = right - left;
        final int height = bottom - top;
        //只支持top、bottom的padding
        final int padding_top = getPaddingTop();
        final int padding_bottom = getPaddingBottom();
        int used = 0;

        if(mItemTotalWidth > 0 && mItemTotalWidth < width) {
            used = (width - mItemTotalWidth) / 2;
        }

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                child.layout(width - used - child.getMeasuredWidth(), padding_top, width - used, height - padding_bottom);
            } else {
                child.layout(used, padding_top, child.getMeasuredWidth() + used, height - padding_bottom);
            }
            used += child.getMeasuredWidth();
        }
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        if(mChangeBackgroundMode)
        {
            int width = getWidth();
            int height = getHeight();

            Iterator<Oval> iterator = mOvals.iterator();
            while(iterator.hasNext())
            {
                Oval oval = iterator.next();
                mPaint.setColor(oval.color);
                if(oval.r < oval.maxR)
                {
                    mTempRectF.set(oval.getLeft(),oval.getTop(),oval.getRight(),oval.getBottom());
                    canvas.drawOval(mTempRectF, mPaint);
                }
                else
                {
                    this.setBackgroundColor(oval.color);
                    canvas.drawRect(0,0,width,height,mPaint);
                    iterator.remove();
                }
                invalidate();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        if(ev.getAction() == MotionEvent.ACTION_UP)
        {
            mLastUpX = ev.getX();
            mLastUpY = ev.getY();
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public void setSelect(int index) {
        //不正常的选择项
        if(index >= mItems.size() || index < 0){return;}

        View v = mItems.get(index);
        setSelect(index,v.getX() + v.getWidth() / 2f,v.getY() + v.getHeight() / 2f);
    }

    @Override
    public void setMessageNumber(int index, int number) {
        mItems.get(index).setMessageNumber(number);
    }

    @Override
    public void setHasMessage(int index, boolean hasMessage) {
        mItems.get(index).setHasMessage(hasMessage);
    }

    @Override
    public void addTabItemSelectedListener(OnTabItemSelectedListener listener) {
        mListeners.add(listener);
    }

    @Override
    public int getSelected() {
        return mSelected;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public String getItemTitle(int index) {
        return mItems.get(index).getTitle();
    }

    private void setSelect(int index,float x,float y) {

        //重复选择
        if(index == mSelected){
            for(OnTabItemSelectedListener listener:mListeners) {
                listener.onRepeat(mSelected);
            }
            return;
        }

        //记录前一个选中项和当前选中项
        mOldSelected = mSelected;
        mSelected = index;

        mDelayedAnimationHelper.beginDelayedTransition(this);

        //切换背景颜色
        if(mChangeBackgroundMode)
        {
            addOvalColor(mColors.get(mSelected),x,y);
        }

        //前一个选中项必须不小于0才有效
        if(mOldSelected >= 0) {
            mItems.get(mOldSelected).setChecked(false);
        }

        mItems.get(mSelected).setChecked(true);

        //事件回调
        for(OnTabItemSelectedListener listener:mListeners) {
            listener.onSelected(mSelected,mOldSelected);
        }
    }

    /**
     * 添加一个圆形波纹动画
     * @param color 颜色
     * @param x X座标
     * @param y y座标
     */
    private void addOvalColor(int color,float x,float y)
    {
        final Oval oval = new Oval(color,2,x,y);

        oval.maxR = getR(x,y);
        mOvals.add(oval);

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(oval.r,oval.maxR);
        valueAnimator.setInterpolator(mInterpolator);
        valueAnimator.setDuration(ANIM_TIME);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float n = (float) valueAnimator.getAnimatedValue();
                oval.r = n;
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                invalidate();
            }
        });
        valueAnimator.start();
    }

    /**
     * 以矩形内一点为圆心画圆，覆盖矩形，求这个圆的最小半径
     * @param x 横坐标
     * @param y 纵坐标
     * @return  最小半径
     */
    private float getR(float x,float y)
    {
        int width = getWidth();
        int height = getHeight();

        double r1_square = x*x + y*y;
        double r2_square = (width-x)*(width-x) + y*y;
        double r3_square = (width-x)*(width-x) + (height-y)*(height-y);
        double r4_square = x*x + (height-y)*(height-y);

        return (float) Math.sqrt(Math.max(Math.max(r1_square,r2_square),Math.max(r3_square,r4_square)));
    }

    private class Oval
    {
        int color;
        float r;
        float x;
        float y;
        float maxR;

        Oval(int color, float r, float x, float y) {
            this.color = color;
            this.r = r;
            this.x = x;
            this.y = y;
        }

        float getLeft() {
            return x - r;
        }

        float getTop() {
            return y - r;
        }

        float getRight() {
            return x + r;
        }

        float getBottom() {
            return y + r;
        }
    }
}
