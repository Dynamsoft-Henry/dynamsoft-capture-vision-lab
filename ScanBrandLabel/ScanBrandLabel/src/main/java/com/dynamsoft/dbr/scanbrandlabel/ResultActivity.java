package com.dynamsoft.dbr.scanbrandlabel;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.core.basic_structures.CoreException;

public class ResultActivity extends AppCompatActivity {

    private static final String TRACEABILITY_LABEL = "Traceability Code";
    private static final String SERIAL_NUMBER_LABEL = "Serial Number (S/N)";
    private static final String PART_NUMBER_LABEL = "Part Number (P/N)";
    private static final String LOT_CODE_LABEL = "Lot Code";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ResultPayloadStore.Payload payload = ResultPayloadStore.get();
        if (payload == null) {
            finish();
            return;
        }

        ImageView resultImageView = findViewById(R.id.iv_result_image);
        TextView resultDetailsView = findViewById(R.id.tv_result_details);

        try {
            Bitmap resultBitmap = payload.getLabelImage().toBitmap();
            resultImageView.setImageBitmap(resultBitmap);
        } catch (CoreException exception) {
            resultImageView.setImageDrawable(null);
        }

        resultDetailsView.setText(buildResultDetails(payload));

        findViewById(R.id.btn_scan_again).setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            ResultPayloadStore.clear();
        }
        super.onDestroy();
    }

    private CharSequence buildResultDetails(ResultPayloadStore.Payload payload) {
        SpannableStringBuilder builder = new SpannableStringBuilder();

        appendResultLine(builder, TRACEABILITY_LABEL, payload.getTraceabilityCode());
        appendResultLine(builder, SERIAL_NUMBER_LABEL, payload.getSerialNumber());
        appendResultLine(builder, PART_NUMBER_LABEL, payload.getPartNumber());
        appendResultLine(builder, LOT_CODE_LABEL, payload.getLotCode());

        return builder;
    }

    private void appendResultLine(SpannableStringBuilder builder, String label, String value) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }

        int start = builder.length();
        builder.append(label);
        builder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(": ");
        builder.append(value);
    }
}