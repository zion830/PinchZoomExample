package com.zion830.pinchzoomexample;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomImageView extends AppCompatImageView {

    private static final int MAX_DURATION = 200;
    private Mode mode = Mode.NONE;
    private Matrix matrix = new Matrix();
    private PointF lastPoint = new PointF(); // 마지막 좌표
    private float[] matrixValue; // Matrix 값을 보관할 3 * 3 배열
    private float minScale = 0.5f; // 축소 최대 비율
    private float maxScale = 2f; // 확대 최대 비율
    private float saveScale = 1f; // 현재 이미지 비율
    private float right;
    private float bottom;
    private float originalBitmapWidth;
    private float originalBitmapHeight;
    private ScaleGestureDetector mScaleDetector;

    // 더블 클릭 처리를 위한 속성
    private int clickCount = 0;
    private long startTime;
    private long duration;

    private void init(Context context) {
        super.setClickable(true);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        matrixValue = new float[9];
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);
    }

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setZoomScale(float min, float max) {
        minScale = min;
        maxScale = max;
    }

    private void fitCenter() {
        Drawable drawable = getDrawable();
        int bmWidth = (drawable != null) ? drawable.getIntrinsicWidth() : 0;
        int bmHeight = (drawable != null) ? drawable.getIntrinsicHeight() : 0;
        float width = getMeasuredWidth();
        float height = getMeasuredHeight();
        float scale = (width > height) ? height / bmHeight : width / bmWidth; // 이미지 비율 계산

        matrix.setScale(scale, scale);
        saveScale = 1f;

        originalBitmapWidth = scale * bmWidth;
        originalBitmapHeight = scale * bmHeight;

        float redundantYSpace = (height - originalBitmapHeight);
        float redundantXSpace = (width - originalBitmapWidth);

        matrix.postTranslate(redundantXSpace / 2, redundantYSpace / 2); // 중앙으로 이동
        setImageMatrix(matrix);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        fitCenter();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);

        matrix.getValues(matrixValue);
        float x = matrixValue[Matrix.MTRANS_X];
        float y = matrixValue[Matrix.MTRANS_Y];
        PointF currentPoint = new PointF(event.getX(), event.getY());
        printBitmapLocation();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: // 한손 클릭 시 drag
                startTime = System.currentTimeMillis();
                clickCount++;

                lastPoint.set(currentPoint);
                mode = Mode.DRAG;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                lastPoint.set(currentPoint);
                mode = Mode.ZOOM;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == Mode.ZOOM || (mode == Mode.DRAG && saveScale > minScale)) {
                    float deltaX = currentPoint.x - lastPoint.x;
                    float deltaY = currentPoint.y - lastPoint.y;
                    float scaleWidth = Math.round(originalBitmapWidth * saveScale);
                    float scaleHeight = Math.round(originalBitmapHeight * saveScale);

                    boolean limitX = false;
                    boolean limitY = false;

                    if (!(scaleWidth < getWidth() && scaleHeight < getHeight())) {
                        if (scaleWidth < getWidth()) {
                            deltaX = 0;
                            limitY = true;
                        } else if (scaleHeight < getHeight()) {
                            deltaY = 0;
                            limitX = true;
                        } else {
                            limitX = true;
                            limitY = true;
                        }
                    }

                    if (limitY) {
                        if (y + deltaY > 0) {
                            deltaY = -y;
                        } else if (y + deltaY < -bottom) {
                            deltaY = -(y + bottom);
                        }
                    }

                    if (limitX) {
                        if (x + deltaX > 0) {
                            deltaX = -x;
                        } else if (x + deltaX < -right) {
                            deltaX = -(x + right);
                        }
                    }

                    if (saveScale > 1.0f) {
                        matrix.postTranslate(deltaX, deltaY); // 확대할 경우에만 드래그 가능
                    }

                    lastPoint.set(currentPoint.x, currentPoint.y);
                }
                break;
            case MotionEvent.ACTION_UP: // 첫번째 손가락 뗐을 때
                long time = System.currentTimeMillis() - startTime;
                duration = duration + time;
                if (clickCount == 2) {
                    if (duration <= MAX_DURATION) {
                        fitCenter();
                    }
                    clickCount = 0;
                    duration = 0;
                }

                mode = Mode.NONE;
                break;
            case MotionEvent.ACTION_POINTER_UP: // 두번째 손가락 뗐을 때
                mode = Mode.NONE;
                break;
        }

        setImageMatrix(matrix);
        invalidate();
        return true;
    }

    private void printBitmapLocation() {
        Log.d("Bitmap location", "left: " + matrixValue[Matrix.MTRANS_X] +
                " top : " + matrixValue[Matrix.MTRANS_Y]);
    }

    private enum Mode {
        NONE, DRAG, ZOOM
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = Mode.ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = saveScale * scaleFactor;

            if (newScale < maxScale && newScale > minScale) {
                saveScale = newScale;
                float width = getWidth();
                float height = getHeight();
                float scaledBitmapWidth = originalBitmapWidth * saveScale;
                float scaledBitmapHeight = originalBitmapHeight * saveScale;
                right = scaledBitmapWidth - width;
                bottom = scaledBitmapHeight - height;

                if (scaledBitmapWidth <= width || scaledBitmapHeight <= height) {
                    matrix.postScale(scaleFactor, scaleFactor, width / 2, height / 2);
                } else {
                    matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                }
            }
            return true;
        }
    }
}