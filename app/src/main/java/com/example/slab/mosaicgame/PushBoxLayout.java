package com.example.slab.mosaicgame;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PushBoxLayout extends ViewGroup {

    @IntDef({DirectionL, DirectionR, DirectionT, DirectionD})
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.FIELD)
    @interface DirectionDef {
    }

    private final static String TAG = "PushBoxLayout";
    private static final int MIN_FLING_VELOCITY = 400; // dips per second
    public final int DirectionL = 0;
    public final int DirectionR = 1;
    public final int DirectionT = 2;
    public final int DirectionD = 3;

    private int N = 3;
    private int maximumBlock = N * N;
    private ViewDragHelper mDragHelper;
    private View targetView;
    private int resId;


    /**
     * 代表了id为index的在九宫格中的位置，grid[1] = 8 代表id=1的在正下方
     */
    private int[] id2position = new int[maximumBlock];
    private int[] position2id = new int[maximumBlock];

    public PushBoxLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PushBoxLayout(Context context) {
        this(context, null, 0);
    }

    public PushBoxLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PushBoxLayout);
        int resId = a.getResourceId(R.styleable.PushBoxLayout_bottomDrawable, -1);
        int level = a.getInt(R.styleable.PushBoxLayout_level, 3);
        a.recycle();
        setLevel(level);
        setResId(resId);
        startGame();
    }

    public PushBoxLayout setLevel(int level) {
        if (mDragHelper != null) {
            mDragHelper = null;
        }
        this.N = level;
        this.maximumBlock = N*N;
        this.id2position = new int[maximumBlock];
        this.position2id = new int[maximumBlock];
        return this;
    }

    public PushBoxLayout setResId(@DrawableRes int resId) {
        this.resId = resId;
        return this;
    }
    public void startGame() {
        startGame(null);
    }

    private void startGame(@Nullable PushboxSavedState savedInstance) {
        if (savedInstance != null) {
            this.setLevel(savedInstance.N);
            this.setResId(savedInstance.resId);
            this.id2position = savedInstance.id2position;
            this.position2id = savedInstance.postion2id;
        }
        if (resId == -1) {
            return;
        }

        prepareGame();

        if (savedInstance == null) {
            shuffle();
        }

        initDragHelper();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        PushboxSavedState ss = new PushboxSavedState(superState);
        ss.N = N;
        ss.id2position = id2position;
        ss.postion2id = position2id;
        ss.resId  = resId;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        PushboxSavedState ss = (PushboxSavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        startGame(ss);
    }

    private void prepareGame() {
        if (resId == -1) {
            return;
        }
        Bitmap mBitmap = BitmapFactory.decodeResource(getResources(), resId);
        removeAllViews();
        float averWidth = mBitmap.getWidth() / (float) N;
        float averHeight = mBitmap.getHeight() / (float) N;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                Bitmap cellBitmap = Bitmap.createBitmap(mBitmap,
                        ((int) (j * averWidth)),
                        ((int) (i * averHeight)),
                        ((int) averWidth),
                        ((int) averHeight));
                ImageView cellImageView = new ImageView(getContext());
                cellImageView.setId(i * N + j);
                cellImageView.setImageBitmap(cellBitmap);
                cellImageView.setBackgroundColor(0xffffffff);
                cellImageView.setScaleType(ScaleType.FIT_XY);
                addView(cellImageView);
            }
        }
        targetView = getChildAt(maximumBlock - 1);
        targetView.setVisibility(View.INVISIBLE);

    }

    private void shuffle() {
        id2position[maximumBlock - 1] = maximumBlock - 1; //最后一个作为不可见的第一次固定出现在右下方
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < maximumBlock - 1; i++) {
            ids.add(i);
        }
        // http://blog.sina.com.cn/s/blog_5396eb5301017qv0.html
        int sum;
        do {
            Collections.shuffle(ids);
            sum = 0;
            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    if (ids.get(i) > ids.get(j)) {
                        sum += 1;
                    }
                }
            }
            Log.d("PushBoxLayout", "sum " + sum);
        } while (sum == 0 || sum % 2 != 0);

        for (int i = 0; i < ids.size(); i++) {
            id2position[i] = ids.get(i);
        }

        for (int i = 0; i < id2position.length; i++) {
            position2id[id2position[i]] = i;
        }
    }


    private void initDragHelper() {

        mDragHelper = ViewDragHelper.create(this, 0.5f, new ViewDragHelper.Callback() {
            private float mOffsetFactor = 0f;
            private int previousLeft;
            private int previousTop;
            private int currentState = ViewDragHelper.STATE_IDLE;
            @DirectionDef
            int direction;


            @Override
            public void onViewCaptured(View capturedChild, int activePointerId) {
                super.onViewCaptured(capturedChild, activePointerId);
            }

            @Override
            public boolean tryCaptureView(View child, int pointerId) {
                Log.d(TAG, "try capture childId:" + child.getId());
                if (currentState != ViewDragHelper.STATE_IDLE)
                    return false;
                if (child.getId() < 0 || child.getId() > maximumBlock) // not a valid child
                    return false;
                if (child.getId() == maximumBlock - 1) //is the invisiable block
                    return false;
                for (int i : getAdjChildids(id2position[child.getId()])) {
                    if (i == maximumBlock - 1) {
                        previousLeft = child.getLeft();
                        previousTop = child.getTop();
                        int d = id2position[i] - id2position[child.getId()];
                        if (d == 1) {
                            direction = DirectionR;
                            return true;
                        } else if (d == -1) {
                            direction = DirectionL;
                            return true;
                        } else if (d == N) {
                            direction = DirectionD;
                            return true;
                        } else if (d == -N) {
                            direction = DirectionT;
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
                return false;
            }

            private int[] getAdjChildids(int position) {
                int count = 0;
                int[] result = new int[4];
                Arrays.fill(result, -1);
                for (int i = 0; i < maximumBlock; i++) {
                    if ((Math.abs(position - i) == 1 && (Math.min(position, i) + 1) % N != 0)
                            || Math.abs(position - i) == N) {
                        result[count++] = position2id[i];
                    }
                }
                //System.out.println(Arrays.toString(result));
                return result;
            }


            @Override
            public void onViewReleased(View releasedChild, float xvel, float yvel) {
                if (mOffsetFactor > 0.2f) {
                    //交换位置
                    int posTarget = id2position[targetView.getId()];
                    int posMove = id2position[releasedChild.getId()];
                    id2position[releasedChild.getId()] = posTarget;
                    id2position[targetView.getId()] = posMove;
                    position2id[posMove] = targetView.getId();
                    position2id[posTarget] = releasedChild.getId();
                    mDragHelper.settleCapturedViewAt(targetView.getLeft(), targetView.getTop());
                } else {
                    //放回原处
                    mDragHelper.settleCapturedViewAt(previousLeft, previousTop);

                }
                invalidate();

            }


            @Override
            public void onViewDragStateChanged(int state) {
                super.onViewDragStateChanged(state);
                if (state == ViewDragHelper.STATE_IDLE) {
                    int i = 0;
                    for (; i < maximumBlock && (id2position[i] == i); i++) {}
                    if (i >= maximumBlock) {
                        targetView.setVisibility(View.VISIBLE);
                        mDragHelper = null;
                    }
                    requestLayout();
                }
                currentState = state;

            }


            @Override
            public void onViewPositionChanged(View changedView, int left,
                                              int top, int dx, int dy) {
                switch (direction) {
                    case DirectionD:
                    case DirectionT:
                        mOffsetFactor = Math.abs(top - previousTop) / (float) targetView.getHeight();
                        break;
                    case DirectionR:
                    case DirectionL:
                        mOffsetFactor = Math.abs(left - previousLeft) / (float) targetView.getWidth();
                        break;
                    default:
                        mOffsetFactor = 0;
                        break;

                }
                //Log.d(TAG, "offset"+mOffsetFactor);
            }


            @Override
            public int getViewHorizontalDragRange(View child) {
                return targetView.getWidth();
            }

            @Override
            public int getViewVerticalDragRange(View child) {
                return targetView.getHeight();
            }

            @Override
            public int clampViewPositionHorizontal(View child, int left, int dx) {
                int rLimit;
                int lLimit;
                switch (direction) {
                    case DirectionD:
                        rLimit = lLimit = previousLeft;
                        break;
                    case DirectionL:
                        lLimit = targetView.getLeft();
                        rLimit = previousLeft;
                        break;
                    case DirectionR:
                        rLimit = targetView.getLeft();
                        lLimit = previousLeft;
                        break;
                    case DirectionT:
                        rLimit = lLimit = previousLeft;
                        break;
                    default:
                        rLimit = lLimit = previousLeft;
                        break;

                }

                return Math.min(rLimit, Math.max(left, lLimit));
            }

            @Override
            public int clampViewPositionVertical(View child, int top, int dy) {
                int tLimit;
                int dLimit;
                switch (direction) {
                    case DirectionD:
                        tLimit = previousTop;
                        dLimit = targetView.getTop();
                        break;
                    case DirectionL:
                        tLimit = previousTop;
                        dLimit = previousTop;
                        break;
                    case DirectionR:
                        tLimit = previousTop;
                        dLimit = previousTop;
                        break;
                    case DirectionT:
                        tLimit = targetView.getTop();
                        dLimit = previousTop;
                        break;
                    default:
                        tLimit = dLimit = previousTop;
                        break;

                }

                return Math.min(dLimit, Math.max(top, tLimit));
            }


            @Override
            public void onEdgeDragStarted(int edgeFlags, int pointerId) {
                //mDragHelper.captureChildView(mTop, pointerId);
            }

        });

        final float density = getResources().getDisplayMetrics().density;
        //必须设置最小速度
        mDragHelper.setMinVelocity(MIN_FLING_VELOCITY * density);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        return super.getChildDrawingOrder(childCount, i);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
        setMeasuredDimension(Math.min(getMeasuredHeight(), getMeasuredWidthAndState()),
                Math.min(getMeasuredHeight(), getMeasuredWidthAndState()));

    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int leftStart = getPaddingLeft();
        int topStart = getPaddingTop();
        int dividerWidth = 2;
        int totalWidth = r - l - getPaddingLeft() - getPaddingRight() - (N - 1) * dividerWidth;
        int step = (int) (totalWidth / (float) N + 0.5f);
        int count = getChildCount();

        for (int i = count - 1; i >= 0; i--) {
            View child = getChildAt(i);
            int column = id2position[child.getId()] % N;
            int row = id2position[child.getId()] / N;
            setChildFrameAt(child, row, column, step, dividerWidth, leftStart, topStart);
        }

    }

    /**
     * 在computeScroll中调用mDragHelper.continueSettling(true)
     */
    @Override
    public void computeScroll() {
        if (mDragHelper != null && mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return mDragHelper != null && mDragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mDragHelper != null) {
            mDragHelper.processTouchEvent(ev);
        }
        return true;
    }


    private void setChildFrameAt(View child, int row, int colum, int step, int divider, int basex, int basey) {
        child.layout(basex + step * colum + divider * colum,
                basey + step * row + divider * row,
                basex + step * (colum + 1) + divider * colum,
                basey + step * (row + 1) + divider * row);
    }

    //===========================
    private static final class PushboxSavedState extends BaseSavedState{
        int[] id2position;
        int[] postion2id;
        int N;
        int resId;

        public PushboxSavedState(Parcelable superState) {
            super(superState);
        }

        public PushboxSavedState(Parcel source) {
            super(source);
            N = source.readInt();
            resId = source.readInt();
            id2position = new int[N * N];
            postion2id = new int[N * N];
            source.readIntArray(id2position);
            source.readIntArray(postion2id);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(N);
            out.writeInt(resId);
            out.writeIntArray(id2position);
            out.writeIntArray(postion2id);
        }

        public static final Parcelable.Creator<PushboxSavedState> CREATOR
                = new Parcelable.Creator<PushboxSavedState>() {
            public PushboxSavedState createFromParcel(Parcel in) {
                return new PushboxSavedState(in);
            }

            public PushboxSavedState[] newArray(int size) {
                return new PushboxSavedState[size];
            }
        };

    }

}
