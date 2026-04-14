package com.dynamsoft.dbr.scanbrandlabel;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.core.basic_structures.EnumCrossVerificationStatus;
import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.core.intermediate_results.IntermediateResultExtraInfo;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.cvr.EnumPresetTemplate;
import com.dynamsoft.cvr.intermediate_results.IntermediateResultReceiver;
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
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private CameraEnhancer mCamera;
    private CaptureVisionRouter mRouter;
    private volatile boolean hasPendingResultNavigation;
    private boolean crossVerificationPassed = false;

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
                if (processedDocumentResult == null || decodedBarcodesResult == null || recognizedTextLinesResult == null) {
                    return;
                }

                DeskewedImageResultItem[] deskewedImageResultItems = processedDocumentResult.getDeskewedImageResultItems();
                if (deskewedImageResultItems!=null)
                {
                    for (DeskewedImageResultItem item: deskewedImageResultItems)
                    {
                        if (item.getCrossVerificationStatus() == EnumCrossVerificationStatus.CVS_PASSED)
                        {
                            crossVerificationPassed = true;
                        }
                    }
                }

                BarcodeResultItem[] barcodeResultItems = decodedBarcodesResult.getItems();
                TextLineResultItem[] textLineResultItems = recognizedTextLinesResult.getItems();

                if (deskewedImageResultItems != null && barcodeResultItems != null && textLineResultItems != null
                        && deskewedImageResultItems.length != 0 && barcodeResultItems.length >1 && textLineResultItems.length > 2 && crossVerificationPassed) {
                    crossVerificationPassed = false;
                    ImageData deskewedImage = deskewedImageResultItems[0].getImageData();
                    if (deskewedImage == null) {
                        return;
                    }

                    ArrayList<String> barcodeTexts = new ArrayList<>();
                    for (BarcodeResultItem item : barcodeResultItems) {
                        if (item != null && item.getText() != null && !item.getText().trim().isEmpty()) {
                            barcodeTexts.add(item.getText());
                        }
                    }

                    ArrayList<String> textLineContents = new ArrayList<>();
                    for (TextLineResultItem item : textLineResultItems) {
                        if (item != null && item.getText() != null && !item.getText().trim().isEmpty()) {
                            textLineContents.add(item.getText());
                        }
                    }

                    if (barcodeTexts.isEmpty() || textLineContents.isEmpty()) {
                        return;
                    }

                    hasPendingResultNavigation = true;
                    runOnUiThread(() -> openResultPage(deskewedImage, barcodeTexts, textLineContents));
                }
            }
        });


//        mRouter.getIntermediateResultManager().addResultReceiver(new IntermediateResultReceiver() {
//            @Override
//            public void onLocalizedTextLinesReceived(@NonNull LocalizedTextLinesUnit unit, IntermediateResultExtraInfo info) {
//                ArrayList<DrawingItem> drawingItemArrayList = new ArrayList<>();
//                if (unit.getCount() != 0)
//                {
//                    for (LocalizedTextLineElement element: unit.getLocalizedTextLines())
//                    {
//                        QuadDrawingItem quadDrawingItem = new QuadDrawingItem(element.getLocation());
//                        drawingItemArrayList.add(quadDrawingItem);
//                    }
//                }
//                dbrLayer.setDrawingItems(drawingItemArrayList);
//            }
//        });


    }

    @Override
    public void onResume() {
        hasPendingResultNavigation = false;
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

    private void openResultPage(ImageData deskewedImage, ArrayList<String> barcodeTexts, ArrayList<String> textLineContents) {
        if (isFinishing() || isDestroyed()) {
            hasPendingResultNavigation = false;
            return;
        }

        ResultPayloadStore.store(deskewedImage, barcodeTexts, textLineContents);
        startActivity(new Intent(this, ResultActivity.class));
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