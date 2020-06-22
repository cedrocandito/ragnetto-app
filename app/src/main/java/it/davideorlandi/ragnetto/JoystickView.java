package it.davideorlandi.ragnetto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.List;

import static android.hardware.Sensor.TYPE_ALL;

public class JoystickView extends View implements SensorEventListener
{
    // constants

    private static final String TAG = JoystickView.class.getSimpleName();
    private static final int GRID_DIVISIONS = 5;
    private static final float HALF_PI = (float) (Math.PI / 2.0);
    private static final float QUARTER_PI = (float) (Math.PI / 4.0);

    /**
     * Joystick endo-of-scale (range is 2 * END_OF_SCALE)
     */
    private static final float END_OF_SCALE = 1.0f;

    private static final int TYPE_X = 1;
    private static final int TYPE_Y = 2;
    private static final int TYPE_XY = 3;

    // default values for attributes

    private static final int DEFAULT_GRID_BACKGROUND_COLOR = Color.parseColor("#ffa000");
    private static final int DEFAULT_GRID_FOREGROUND_COLOR = Color.parseColor("#ff2000");
    private static final float DEFAULT_GRID_FOREGROUND_STROKE_WIDTH = 4.0f;
    private static final int DEFAULT_STICK_COLOR = Color.parseColor("#c0000040");
    private static final float DEFAULT_STICK_SIZE = 80.0f;
    private static final int DEFAULT_TYPE = TYPE_XY;

    // attributes

    protected int gridBackgroundColor;
    protected int gridForegroundColor;
    protected float gridForegroundStrokeWidth;
    protected int stickColor;
    protected float stickSize;
    protected int type = TYPE_XY;

    // internal variables

    protected int cx;
    protected int cy;
    protected float r;

    protected boolean touching;

    /**
     * x position of the stick, relative to center, in pixels
     */
    protected float stick_x;
    /**
     * y position of the stick, relative to center, in pixels (positive = bottom)
     */
    protected float stick_y;

    protected Paint gridBackgroundPaint;
    protected Paint gridForegroundPaint;
    protected Paint stickPaint;

    protected SensorManager sensorManager;
    protected Sensor sensor;

    // ========================================================================


    public JoystickView(Context context)
    {
        super(context);
    }

    public JoystickView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.JoystickView,
                0, 0);

        try
        {
            gridBackgroundColor = a.getColor(R.styleable.JoystickView_gridBackgroundColor, DEFAULT_GRID_BACKGROUND_COLOR);
            gridForegroundColor = a.getColor(R.styleable.JoystickView_gridForegroundColor, DEFAULT_GRID_FOREGROUND_COLOR);
            gridForegroundStrokeWidth = a.getDimension(R.styleable.JoystickView_gridForegroundStrokeWidth, DEFAULT_GRID_FOREGROUND_STROKE_WIDTH);
            stickColor = a.getColor(R.styleable.JoystickView_stickColor, DEFAULT_STICK_COLOR);
            stickSize = a.getDimension(R.styleable.JoystickView_stickSize, DEFAULT_STICK_SIZE);
            type = a.getInteger(R.styleable.JoystickView_type, DEFAULT_TYPE);
        } finally
        {
            a.recycle();
        }


        gridForegroundPaint = new Paint();
        gridForegroundPaint.setAntiAlias(true);
        gridForegroundPaint.setColor(gridForegroundColor);
        gridForegroundPaint.setStyle(Paint.Style.STROKE);
        gridForegroundPaint.setStrokeWidth(gridForegroundStrokeWidth);

        gridBackgroundPaint = new Paint();
        gridBackgroundPaint.setAntiAlias(true);
        gridBackgroundPaint.setColor(gridBackgroundColor);
        gridBackgroundPaint.setStyle(Paint.Style.FILL);

        stickPaint = new Paint();
        stickPaint.setAntiAlias(true);
        stickPaint.setColor(stickColor);
        stickPaint.setStyle(Paint.Style.FILL);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = sensorManager.getSensorList(TYPE_ALL);
        for (Sensor s : sensors)
        {
            Log.v(TAG, String.format("Available sensor %s (type %d); vendor=%s, version=%d", s.getName(), s.getType(), s.getVendor(), s.getVersion()));
        }
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor != null)
        {
            Log.d(TAG, "Rotation vector sensor found");
        }
        else
        {
            Log.d(TAG, "Rotation vector sensor not found");
        }
    }

    /**
     * Normalized stick x position, range [-1, +1]
     *
     * @return normalized stick x position.
     */
    public float getStickX()
    {
        return stick_x * END_OF_SCALE / r;
    }

    /**
     * Normalized stick y position, range [-1, +1], positive = top
     *
     * @return normalized stick x position.
     */
    public float getStickY()
    {
        return stick_y * END_OF_SCALE / r;
    }


    // =============================================================/

    /**
     * Starts receiving data from sensor if available.
     *
     * @return ture if sensor successfully started, false if sensor not available or not started correctly.
     */
    public boolean startSensor()
    {
        if (sensor != null)
        {
            Log.d(TAG, "Starting rotation sensor");
            return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        }
        else
        {
            Log.d(TAG, "Rotation sensor not started because it's not available");
            return false;
        }
    }

    /**
     * Stops receivind data from sensor.
     */
    public void stopSensor()
    {
        if (sensor != null)
        {
            Log.d(TAG, "Stopping rotation sensor");
            sensorManager.unregisterListener(this, sensor);
        }
    }

    /**
     * Returns true if the rotation sensor is available.
     *
     * @return sensor available.
     */
    public boolean isSensorAvailable()
    {
        return sensor != null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);
        this.cx = w / 2;
        this.cy = h / 2;
        switch (type)
        {
            case TYPE_XY:
                this.r = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight()) / 2.0f - gridForegroundStrokeWidth / 2.0f;
                break;
            case TYPE_X:
                this.r = (getWidth() - getPaddingLeft() - getPaddingRight()) / 2.0f - gridForegroundStrokeWidth / 2.0f;
                break;
            case TYPE_Y:
                this.r = (getHeight() - getPaddingTop() - getPaddingBottom()) / 2.0f - gridForegroundStrokeWidth / 2.0f;
                break;
            default:
                Log.wtf(TAG, "Invalid type: " + type);
        }

        this.stick_x = 0;
        this.stick_y = 0;
    }

    /* warning suppressed beacuse there is no "click" involved in this joystick */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        int action = event.getActionMasked();
        switch (action)
        {
            case MotionEvent.ACTION_DOWN:
                touching = true;
            case MotionEvent.ACTION_MOVE:
                if (type == TYPE_XY || type == TYPE_X)
                    stick_x = event.getX() - cx;

                if (type == TYPE_XY || type == TYPE_Y)
                    stick_y = event.getY() - cy;

                clampStickToRadius();
                break;

            case MotionEvent.ACTION_UP:
                touching = false;
                stick_x = 0;
                stick_y = 0;
                break;
        }

        invalidate();
        return true;
    }

    private void clampStickToRadius()
    {
        float stick_r = (float) Math.sqrt(stick_x * stick_x + stick_y * stick_y);
        if (stick_r > r)
        {
            float normalization_factor = r / stick_r;
            stick_x *= normalization_factor;
            stick_y *= normalization_factor;
        }
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        drawGrid(canvas);
        canvas.drawCircle(stick_x + cx, stick_y + cy, stickSize, stickPaint);
    }

    protected void drawGrid(Canvas canvas)
    {
        switch (type)
        {
            case TYPE_XY:
                // background
                canvas.drawCircle(cx, cy, r, gridBackgroundPaint);
                // grid circles
                for (int i = 1; i <= GRID_DIVISIONS; i++)
                {
                    canvas.drawCircle(cx, cy, r * i / GRID_DIVISIONS, gridForegroundPaint);
                }
                // grid lines
                canvas.drawLine(cx - r, cy, cx + r, cy, gridForegroundPaint);
                canvas.drawLine(cx, cy - r, cx, cy + r, gridForegroundPaint);
                break;

            case TYPE_X:
                // background
                canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), gridBackgroundPaint);
                // border
                canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), gridForegroundPaint);
                // center line
                canvas.drawLine(getPaddingLeft(), cy, getWidth() - getPaddingRight(), cy, gridForegroundPaint);
                // grid
                for (int i = 0; i <= GRID_DIVISIONS; i++)
                {
                    float delta = r * i / GRID_DIVISIONS;
                    canvas.drawLine(cx - delta, getPaddingTop(), cx - delta, getHeight() - getPaddingBottom(), gridForegroundPaint);
                    canvas.drawLine(cx + delta, getPaddingTop(), cx + delta, getHeight() - getPaddingBottom(), gridForegroundPaint);
                }
                break;

            case TYPE_Y:
                // background
                canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), gridBackgroundPaint);
                // border
                canvas.drawRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), gridForegroundPaint);
                // center line
                canvas.drawLine(cx, getPaddingTop(), cx, getHeight() - getPaddingBottom(), gridForegroundPaint);
                // grid
                for (int i = 0; i <= GRID_DIVISIONS; i++)
                {
                    float delta = r * i / GRID_DIVISIONS;
                    canvas.drawLine(getPaddingLeft(), cy - delta, getHeight() - getPaddingRight(), cy - delta, gridForegroundPaint);
                    canvas.drawLine(getPaddingLeft(), cy + delta, getHeight() - getPaddingRight(), cy + delta, gridForegroundPaint);
                }
                break;

            default:
                Log.wtf(TAG, "Invalid type" + type);

        }
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        float[] m = new float[9];
        SensorManager.getRotationMatrixFromVector(m, event.values);
        float[] azimuthPitchRoll = new float[3];
        SensorManager.getOrientation(m, azimuthPitchRoll);
        Log.v(TAG, String.format("Azimuth=%.02f, Pitch=%.02f, Roll=%.02f", azimuthPitchRoll[0], azimuthPitchRoll[1], azimuthPitchRoll[2]));
        if (!touching)
        {
            float pitch = -azimuthPitchRoll[1] - QUARTER_PI;
            float roll = azimuthPitchRoll[2];

            stick_x = roll * r / QUARTER_PI;
            stick_y = pitch * r / QUARTER_PI;

            if (stick_x > r)
                stick_x = r;
            if (stick_x < -r)
                stick_x = -r;
            if (stick_y > r)
                stick_y = r;
            if (stick_y < -r)
                stick_y = -r;

            clampStickToRadius();
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        Log.v(TAG, String.format("Sensor accuracy changed: %d", accuracy));
    }
}
