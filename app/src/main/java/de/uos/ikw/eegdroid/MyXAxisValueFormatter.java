package de.uos.ikw.eegdroid;

import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.DecimalFormat;

public class MyXAxisValueFormatter extends ValueFormatter {
    private final DecimalFormat mFormat;

    public MyXAxisValueFormatter() {
        // format values to 1 decimal digit
        mFormat = new DecimalFormat("###,###,##0.0");
    }

    @Override
    public String getFormattedValue(float value) {
        // "value" represents the position of the label on the axis (x or y)
        return mFormat.format(value / 1000);  //
    }
}