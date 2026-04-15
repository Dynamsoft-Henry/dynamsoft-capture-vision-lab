package com.dynamsoft.dbr.scanbrandlabel;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.core.basic_structures.EnumCrossVerificationStatus;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.core.intermediate_results.IntermediateResultExtraInfo;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.cvr.intermediate_results.IntermediateResultReceiver;
import com.dynamsoft.dbr.EnumBarcodeFormat;
import com.dynamsoft.dbr.BarcodeResultItem;
import com.dynamsoft.dbr.DecodedBarcodesResult;
import com.dynamsoft.dbr.scanbrandlabel.ui.resultsview.CustomizedResultsDisplayView;
import com.dynamsoft.dbr.intermediate_results.LocalizedBarcodeElement;
import com.dynamsoft.dbr.intermediate_results.LocalizedBarcodesUnit;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingItem;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.EnumFocusMode;
import com.dynamsoft.dce.QuadDrawingItem;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.ddn.DeskewedImageResultItem;
import com.dynamsoft.ddn.DetectedQuadResultItem;
import com.dynamsoft.ddn.EnhancedImageResultItem;
import com.dynamsoft.ddn.ProcessedDocumentResult;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.dlr.intermediate_results.LocalizedTextLineElement;
import com.dynamsoft.dlr.intermediate_results.LocalizedTextLinesUnit;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.utility.CrossVerificationCriteria;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private TextView captureStatusView;
    private volatile boolean hasPendingResultNavigation;
    private final Object resultLock = new Object();
    private ImageData labelImage;
    private String traceabilityCode;
    private String serialNumber;
    private String partNumber;
    private String lotCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            // Initialize the license.
            // The license string here is a trial license. Note that network connection is required for this license to work.
            // You can request an extension via the following link: https://www.dynamsoft.com/customer/license/trialLicense?product=dbr&utm_source=samples&package=android
            LicenseManager.initLicense("t0105HAEAAGYdhCPTYnYst9ZQ+DfpJwKjZgwUWg6HDpUt8YCLnjXnWTkpA+5BGh2kY6G2oBt8PSX1TLDM2+8ee68MZVdYSZeAaYClb+VavWqHNzpzU+PZ2afjm6dTBP87ZV9njX4LW382Lzsl;t0109HAEAAKS5NGFaG/Y3gCNvMWLRgW7KZCeHh1CwxJAxBBjhDwBGNkJoTx7uOgHly5rsVXarYFWbRvEO02JluzpB/uNcj5UmIE4AywBbdjZbb9rhF10p3+L17OzLcc3ZKYL/nbKrs0XewuoTMTs7Ig==", (isSuccess, error) -> {
                if (!isSuccess) {
                    error.printStackTrace();
                }
            });
        }
        PermissionUtil.requestCameraPermission(this);

        CameraView cameraView = findViewById(R.id.camera_view);
        captureStatusView = findViewById(R.id.tv_capture_status);
        mCamera = new CameraEnhancer(cameraView, this);
        mCamera.setZoomFactor(2.0f);
        try {
            mCamera.setScanRegion(new DSRect(0.1f, 0.4f, 0.9f, 0.6f, true));
        } catch (CameraEnhancerException e) {
            throw new RuntimeException(e);
        }

        mRouter = new CaptureVisionRouter();
        try {
            // Set the camera enhancer as the input.
            mRouter.setInput(mCamera);
            mRouter.initSettingsFromFile("delivery.json");
        } catch (CaptureVisionRouterException e) {
            throw new RuntimeException(e);
        }

        MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
        CrossVerificationCriteria crossVerificationCriteria = new CrossVerificationCriteria();
        crossVerificationCriteria.setFrameWindow(5);
        crossVerificationCriteria.setMinConsistentFrames(2);
        filter.setResultCrossVerificationCriteria(EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE, crossVerificationCriteria);
        mRouter.addResultFilter(filter);

        DrawingLayer ddnLayer = cameraView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID);
//        ddnLayer.setVisible(false);
        DrawingLayer dlrLayer = cameraView.getDrawingLayer(DrawingLayer.DLR_LAYER_ID);
        DrawingLayer dbrLayer = cameraView.getDrawingLayer(DrawingLayer.DBR_LAYER_ID);
        // Add CapturedResultReceiver to receive the result callback when a video frame is processed.
        mRouter.addResultReceiver(new CapturedResultReceiver() {
            @Override
            public void onCapturedResultReceived(@NonNull CapturedResult result) {
                if (hasPendingResultNavigation) {
                    return;
                }

                ProcessedDocumentResult processedDocumentResult = result.getProcessedDocumentResult();
                DecodedBarcodesResult decodedBarcodesResult = result.getDecodedBarcodesResult();
                RecognizedTextLinesResult recognizedTextLinesResult = result.getRecognizedTextLinesResult();
                PendingResultNavigation pendingNavigation = null;
                String statusText;
                synchronized (resultLock) {
                    if (labelImage == null) {
                        labelImage = extractLabelImage(processedDocumentResult);
                    }
                    if (traceabilityCode == null || serialNumber == null) {
                        collectBarcodeResults(decodedBarcodesResult);
                    }
                    if (serialNumber == null || partNumber == null || lotCode == null) {
                        collectTextLineResults(recognizedTextLinesResult);
                    }

                    statusText = buildCaptureStatusText();

                    if (!hasPendingResultNavigation && hasCollectedAllResults()) {
                        hasPendingResultNavigation = true;
                        pendingNavigation = new PendingResultNavigation(
                                labelImage,
                                traceabilityCode,
                                serialNumber,
                                partNumber,
                                lotCode
                        );
                    }
                }

                String finalStatusText = statusText;
                runOnUiThread(() -> updateCaptureStatus(finalStatusText));

                if (pendingNavigation != null) {
                    PendingResultNavigation finalPendingNavigation = pendingNavigation;
                    runOnUiThread(() -> openResultPage(
                            finalPendingNavigation.labelImage,
                            finalPendingNavigation.traceabilityCode,
                            finalPendingNavigation.serialNumber,
                            finalPendingNavigation.partNumber,
                            finalPendingNavigation.lotCode
                    ));
                }
            }
        });
    }

    @Override
    public void onResume() {
        hasPendingResultNavigation = false;
        resetCollectedResults();
        updateCaptureStatus("");
        // Start video barcode reading
        // Open the camera.
        mCamera.open();
        // Start capturing. If success, you will receive results in the CapturedResultReceiver.
        mRouter.startCapturing("ReadDeliveryPaper", new CompletionListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailure(int errorCode, String errorString) {
                runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .setTitle("StartCapturing error")
                        .setMessage(String.format(Locale.getDefault(), "ErrorCode: %d %nErrorMessage: %s", errorCode, errorString))
                        .show());
            }
        });
        super.onResume();
    }

    @Override
    public void onPause() {
        // Stop video barcode reading
        mCamera.close();
        mRouter.stopCapturing();
        super.onPause();
    }
    
    private void openResultPage(
            ImageData labelImage,
            String traceabilityCode,
            String serialNumber,
            String partNumber,
            String lotCode
    ) {
        if (isFinishing() || isDestroyed()) {
            hasPendingResultNavigation = false;
            return;
        }

        ResultPayloadStore.store(labelImage, traceabilityCode, serialNumber, partNumber, lotCode);
        startActivity(new Intent(this, ResultActivity.class));
    }

    private void resetCollectedResults() {
        synchronized (resultLock) {
            labelImage = null;
            traceabilityCode = null;
            serialNumber = null;
            partNumber = null;
            lotCode = null;
        }
    }

    private boolean hasCollectedAllResults() {
        return labelImage != null
                && traceabilityCode != null
                && serialNumber != null
                && partNumber != null
                && lotCode != null;
    }

    private ImageData extractLabelImage(ProcessedDocumentResult processedDocumentResult) {
        if (processedDocumentResult == null) {
            return null;
        }

        DeskewedImageResultItem[] deskewedImageResultItems = processedDocumentResult.getDeskewedImageResultItems();
        if (deskewedImageResultItems == null) {
            return null;
        }

        for (DeskewedImageResultItem item : deskewedImageResultItems) {
            if (item == null) {
                continue;
            }
            if (item.getCrossVerificationStatus() == EnumCrossVerificationStatus.CVS_PASSED) {
                ImageData imageData = item.getImageData();
                if (imageData != null) {
                    return imageData;
                }
            }
        }
        return null;
    }

    private void collectBarcodeResults(DecodedBarcodesResult decodedBarcodesResult) {
        if (decodedBarcodesResult == null) {
            return;
        }

        BarcodeResultItem[] barcodeResultItems = decodedBarcodesResult.getItems();
        if (barcodeResultItems == null) {
            return;
        }

        for (BarcodeResultItem item : barcodeResultItems) {
            if (item == null || item.getText() == null) {
                continue;
            }

            String text = item.getText().trim();
            if (text.isEmpty()) {
                continue;
            }

            if (traceabilityCode == null && item.getFormat() == EnumBarcodeFormat.BF_DATAMATRIX) {
                traceabilityCode = text;
            }

            if (serialNumber == null && item.getFormat() == EnumBarcodeFormat.BF_CODE_93) {
                serialNumber = text;
            }

            if (traceabilityCode != null && serialNumber != null) {
                return;
            }
        }
    }

    private void collectTextLineResults(RecognizedTextLinesResult recognizedTextLinesResult) {
        if (recognizedTextLinesResult == null) {
            return;
        }

        TextLineResultItem[] textLineResultItems = recognizedTextLinesResult.getItems();
        if (textLineResultItems == null) {
            return;
        }

        for (TextLineResultItem item : textLineResultItems) {
            if (item == null || item.getText() == null) {
                continue;
            }

            String text = item.getText().trim();
            if (text.isEmpty()) {
                continue;
            }

            if (serialNumber == null) {
                String extractedSerialNumber = extractPrefixedValue(text, "S/N", "SIN");
                if (extractedSerialNumber != null) {
                    serialNumber = extractedSerialNumber;
                    continue;
                }
            }

            if (partNumber == null) {
                String extractedPartNumber = extractPrefixedValue(text, "P/N", "PIN");
                if (extractedPartNumber != null) {
                    partNumber = extractedPartNumber;
                    continue;
                }
            }

            if (lotCode == null) {
                String extractedLotCode = extractLotCode(text);
                if (extractedLotCode != null) {
                    lotCode = extractedLotCode;
                }
            }
        }
    }

    private String extractPrefixedValue(String text, String expectedPrefix, String fallbackPrefix) {
        String normalized = text.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);

        if (upper.startsWith(expectedPrefix)) {
            return sanitizeExtractedValue(normalized.substring(expectedPrefix.length()));
        }
        if (upper.startsWith(fallbackPrefix)) {
            return sanitizeExtractedValue(normalized.substring(fallbackPrefix.length()));
        }
        return null;
    }

    private String sanitizeExtractedValue(String rawValue) {
        String sanitized = rawValue.trim();
        int start = 0;
        while (start < sanitized.length()) {
            char current = sanitized.charAt(start);
            if (!Character.isWhitespace(current) && current != ':' && current != '：' && current != '-') {
                break;
            }
            start++;
        }

        String value = sanitized.substring(start).trim();
        return value.isEmpty() ? null : value;
    }

    private String extractLotCode(String text) {
        if (traceabilityCode == null || traceabilityCode.length() < 6) {
            return null;
        }

        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.startsWith("S/N") || upper.startsWith("SIN") || upper.startsWith("P/N") || upper.startsWith("PIN")) {
            return null;
        }

        String traceabilityUpper = traceabilityCode.toUpperCase(Locale.ROOT);
        String expectedPrefix = traceabilityUpper.substring(0, 6);
        String expectedSevenChars = traceabilityUpper.length() >= 7 ? traceabilityUpper.substring(0, 7) : null;

        for (int index = 0; index <= upper.length() - 6; index++) {
            if (!upper.regionMatches(index, expectedPrefix, 0, expectedPrefix.length())) {
                continue;
            }

            int matchedLength = 6;
            if (expectedSevenChars != null
                    && index + 7 <= upper.length()
                    && upper.regionMatches(index, expectedSevenChars, 0, expectedSevenChars.length())) {
                matchedLength = 7;
            }

            return normalized.substring(index, index + matchedLength);
        }

        return null;
    }

    private String buildCaptureStatusText() {
        StringBuilder builder = new StringBuilder();

        appendCaptureStatusLine(builder, labelImage != null, "Label Image Captured!");
        appendCaptureStatusLine(builder, traceabilityCode != null, "Traceability Code Captured!");
        appendCaptureStatusLine(builder, serialNumber != null, "Serial Number Captured!");
        appendCaptureStatusLine(builder, partNumber != null, "Part Number Captured!");
        appendCaptureStatusLine(builder, lotCode != null, "Lot Code Captured!");

        return builder.toString();
    }

    private void appendCaptureStatusLine(StringBuilder builder, boolean shouldDisplay, String line) {
        if (!shouldDisplay) {
            return;
        }

        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(line);
    }

    private void updateCaptureStatus(String statusText) {
        if (captureStatusView == null) {
            return;
        }

        captureStatusView.setText(statusText);
        captureStatusView.setVisibility(statusText.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static final class PendingResultNavigation {
        private final ImageData labelImage;
        private final String traceabilityCode;
        private final String serialNumber;
        private final String partNumber;
        private final String lotCode;

        private PendingResultNavigation(
                ImageData labelImage,
                String traceabilityCode,
                String serialNumber,
                String partNumber,
                String lotCode
        ) {
            this.labelImage = labelImage;
            this.traceabilityCode = traceabilityCode;
            this.serialNumber = serialNumber;
            this.partNumber = partNumber;
            this.lotCode = lotCode;
        }
    }

    // This is the method that access all BarcodeResultItem in the DecodedBarcodesResult and extract the content.
    @MainThread
    private void showResult(DecodedBarcodesResult result) {
        if (result != null && result.getItems().length > 0) {
            CustomizedResultsDisplayView resultView = findViewById(R.id.results_view);
            resultView.setVisibility(View.VISIBLE);
            resultView.updateResults(result.getItems());
        }
    }

}