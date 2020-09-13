package it.davideorlandi.ragnetto;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class SeekBarAndValueView extends LinearLayout
{
    private static final String TAG = "SeekbarAndValueView";

    private TextView v_title;
    private SeekBar v_seekBar;
    private TextView v_value;
    private Button v_plus;
    private Button v_minus;


    private int min;
    private int max;

    private boolean userIsMovineSeekBar;

    public SeekBarAndValueView(Context context)
    {
        this(context, null);
    }

    public SeekBarAndValueView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);

        inflate(context, R.layout.seekbarandvalueview, this);

        v_seekBar = findViewById(R.id.skb_seekbar);
        v_value = findViewById(R.id.txt_value);
        v_title = findViewById(R.id.txt_title);
        v_plus = findViewById(R.id.btn_plus);
        v_minus = findViewById(R.id.btn_minus);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.SeekBarAndValueView,
                0, 0);

        try
        {
            min = a.getInteger(R.styleable.SeekBarAndValueView_min, 0);
            max = a.getInteger(R.styleable.SeekBarAndValueView_max, 255);
            v_title.setText(a.getString(R.styleable.SeekBarAndValueView_title));
        } finally
        {
            a.recycle();
        }

        updateMinMax();
        updateTextValue();

        v_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                if (fromUser)
                {
                    v_value.setText(String.valueOf(getProgress()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
                userIsMovineSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
                userIsMovineSeekBar = false;
            }
        });

        v_plus.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int progress = getProgress();
                if (progress <= max)
                    setProgress(++progress);
            }
        });

        v_minus.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                int progress = getProgress();
                if (progress >= min)
                    setProgress(--progress);
            }
        });
    }

    public int getMin()
    {
        return min;
    }

    public void setMin(int min)
    {
        this.min = min;
        updateMinMax();
    }

    public int getMax()
    {
        return max;
    }

    public void setMax(int max)
    {
        this.max = max;
        updateMinMax();
    }

    public CharSequence getTitle()
    {
        return v_title.getText();
    }

    public void setTitle(CharSequence title)
    {
        v_title.setText(title);
    }

    public int getProgress()
    {
        return v_seekBar.getProgress() + min;
    }


    public void setProgress(int progress)
    {
        v_seekBar.setProgress(progress - min);
        updateTextValue();
    }

    private void updateMinMax()
    {
        v_seekBar.setMax(max - min);
    }

    private void updateTextValue()
    {
        v_value.setText(String.valueOf(getProgress()));
    }
}
