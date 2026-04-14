package com.dynamsoft.dbr.scanbrandlabel;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.core.basic_structures.CoreException;

import java.util.List;

public class ResultActivity extends AppCompatActivity {

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
        TextView barcodeResultView = findViewById(R.id.tv_barcode_results);
        TextView textLineResultView = findViewById(R.id.tv_text_line_results);

        try {
            Bitmap resultBitmap = payload.getDeskewedImage().toBitmap();
            resultImageView.setImageBitmap(resultBitmap);
        } catch (CoreException exception) {
            resultImageView.setImageDrawable(null);
        }

        barcodeResultView.setText(formatLines(getString(R.string.result_barcode_item_format), payload.getBarcodeTexts(), R.string.result_no_barcode));
        textLineResultView.setText(formatLines(getString(R.string.result_text_line_item_format), payload.getTextLineContents(), R.string.result_no_text_line));

        findViewById(R.id.btn_scan_again).setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            ResultPayloadStore.clear();
        }
        super.onDestroy();
    }

    private String formatLines(String itemFormat, List<String> values, int emptyTextResId) {
        if (values == null || values.isEmpty()) {
            return getString(emptyTextResId);
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append("\n");
            }
            builder.append(String.format(itemFormat, index + 1, values.get(index)));
        }
        return builder.toString();
    }
}