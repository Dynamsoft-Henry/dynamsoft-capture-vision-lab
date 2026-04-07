package com.dynamsoft.scaniddocument;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.dynamsoft.core.basic_structures.CompletionListener;
import com.dynamsoft.core.basic_structures.DSRect;
import com.dynamsoft.core.basic_structures.EnumCapturedResultItemType;
import com.dynamsoft.core.basic_structures.EnumColourChannelUsageType;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CaptureVisionRouterException;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.CapturedResultReceiver;
import com.dynamsoft.dce.CameraEnhancer;
import com.dynamsoft.dce.CameraEnhancerException;
import com.dynamsoft.dce.CameraView;
import com.dynamsoft.dce.DrawingLayer;
import com.dynamsoft.dce.EnumCameraPosition;

import com.dynamsoft.dce.EnumEnhancerFeatures;
import com.dynamsoft.dce.Feedback;
import com.dynamsoft.dce.utils.PermissionUtil;
import com.dynamsoft.dcp.EnumValidationStatus;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.dlr.RecognizedTextLinesResult;
import com.dynamsoft.dlr.TextLineResultItem;
import com.dynamsoft.license.LicenseManager;
import com.dynamsoft.utility.CrossVerificationCriteria;
import com.dynamsoft.utility.MultiFrameResultCrossFilter;

import java.util.Calendar;
import java.util.HashMap;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * @author: dynamsoft
 * Time: 2024/6/13
 * Description:
 */
public class ScanActivity extends AppCompatActivity {
	private static final String TAG = "ScanActivity";
	private static final String TEMPLATE_ID_DOCUMENTS = "ReadIdDocuments";
	private static final long RESULT_ANIMATION_DELAY_MS = 300L;

	private CameraEnhancer mCamera;
	private CameraView mCameraView;
	private CaptureVisionRouter mRouter;
	private String mText = "";
	private boolean succeed = false;
	private boolean mBeepStatus;
	private boolean mIsResumed;
	private boolean mTorchEnabled;
	private TextView mInitError;
	private TextView mStartError;
	private ViewGroup mTipView;
	private ViewGroup mGuideFrameView;
	private ImageView mAudioToggleView;
	private ImageView mFlashlightToggleView;
	private ImageView mCameraToggleView;
	private ScanResultCollector mScanResultCollector;
	private int mBirthYear;
	private final String mCurrentTemplate = TEMPLATE_ID_DOCUMENTS;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_scan);
		PermissionUtil.requestCameraPermission(this);
		configSystemBars();
		bindViews();
		bindBackPress();
		initTopBar();
		initCamera();
		initRouter();
		showInitialScanningUi();

		// Initialize the license.
		// The license string here is a trial license. Note that network connection is required for this license to work.
		// You can request an extension via the following link: https://www.dynamsoft.com/customer/license/trialLicense?product=mrz&utm_source=samples&package=android
		LicenseManager.initLicense("DLS2eyJvcmdhbml6YXRpb25JRCI6IjIwMDAwMSJ9",
				this,
				(isSuccess, error) -> {
					if (!isSuccess) {
						runOnUiThread(() -> {
							mInitError.setVisibility(View.VISIBLE);
							mInitError.setText("License initialization failed: " + (error == null ? "Unknown error" : error.getMessage()));
						});
						if (error != null) {
							error.printStackTrace();
						}
					}
				});
	}

	private void configSystemBars() {
		Window window = getWindow();
		if (window != null) {
			WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
			controller.setAppearanceLightStatusBars(false);
			controller.setAppearanceLightNavigationBars(false);
		}
		View mainView = findViewById(R.id.main);
		ViewCompat.setOnApplyWindowInsetsListener(mainView, (view, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
			View statusBarBackground = findViewById(R.id.status_bar_background);
			statusBarBackground.getLayoutParams().height = systemBars.top;
			statusBarBackground.requestLayout();
			return WindowInsetsCompat.CONSUMED;
		});
	}

	private void bindViews() {
		mCameraView = findViewById(R.id.dce_camera_view);
		mInitError = findViewById(R.id.tv_init_license_error);
		mStartError = findViewById(R.id.tv_start_license_error);
		mTipView = findViewById(R.id.tv_tip);
		mGuideFrameView = findViewById(R.id.iv_guide_frame);
	}

	private void bindBackPress() {
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				finish();
			}
		});
	}

	private void initTopBar() {
		View topBar = findViewById(R.id.top_bar);
		ImageView closeButton = topBar.findViewById(R.id.iv_close);
		mFlashlightToggleView = topBar.findViewById(R.id.iv_toggle_flashlight);
		mCameraToggleView = topBar.findViewById(R.id.iv_toggle_camera);
		mAudioToggleView = topBar.findViewById(R.id.iv_toggle_audio);
		ImageView vibrateToggle = topBar.findViewById(R.id.iv_toggle_vibrate);
		View divider = topBar.findViewById(R.id.divider);

		closeButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
		vibrateToggle.setVisibility(View.GONE);
		divider.setVisibility(View.VISIBLE);

		toggleBeepButton();
		mAudioToggleView.setSelected(mBeepStatus);
		mAudioToggleView.setOnClickListener(v -> {
			mBeepStatus = !mBeepStatus;
			mAudioToggleView.setSelected(mBeepStatus);
			saveBeepStatus();
		});

		mFlashlightToggleView.setSelected(false);
		mFlashlightToggleView.setOnClickListener(v -> {
			mTorchEnabled = !mTorchEnabled;
			if (mTorchEnabled) {
				mCamera.turnOnTorch();
			} else {
				mCamera.turnOffTorch();
			}
			mFlashlightToggleView.setSelected(mTorchEnabled);
		});

		mCameraToggleView.setSelected(false);
		mCameraToggleView.setOnClickListener(v -> {
			boolean useFrontCamera = !mCameraToggleView.isSelected();
			mCamera.selectCamera(useFrontCamera ? EnumCameraPosition.CP_FRONT : EnumCameraPosition.CP_BACK);
			mCameraToggleView.setSelected(useFrontCamera);
			if (useFrontCamera) {
				mTorchEnabled = false;
				mCamera.turnOffTorch();
			}
			mFlashlightToggleView.setSelected(mTorchEnabled);
			mFlashlightToggleView.setVisibility(useFrontCamera ? View.INVISIBLE : View.VISIBLE);
		});
	}

	private void initCamera() {
		mCameraView.getDrawingLayer(DrawingLayer.DLR_LAYER_ID).setVisible(false);
		mCameraView.getDrawingLayer(DrawingLayer.DDN_LAYER_ID).setVisible(false);
		mCamera = new CameraEnhancer(mCameraView, this);
		mCamera.setColourChannelUsageType(EnumColourChannelUsageType.CCUT_FULL_CHANNEL);
		try {
			mCamera.enableEnhancedFeatures(EnumEnhancerFeatures.EF_FRAME_FILTER);
//			mCamera.setScanRegion(new DSRect(0.1f, 0.25f, 0.9f, 0.72f, true));
		} catch (CameraEnhancerException e) {
			throw new RuntimeException(e);
		}
//		int whiteColor = ResourcesCompat.getColor(getResources(), R.color.white, null);
//		int transparentColor = ResourcesCompat.getColor(getResources(), R.color.transparent, null);
//		mCameraView.setScanRegionMaskStyle(whiteColor, transparentColor, 2.0f);
	}

	private void initRouter() {
		mRouter = new CaptureVisionRouter();
		mScanResultCollector = new ScanResultCollector();
		MultiFrameResultCrossFilter filter = new MultiFrameResultCrossFilter();
		filter.enableResultCrossVerification(
				EnumCapturedResultItemType.CRIT_TEXT_LINE
						| EnumCapturedResultItemType.CRIT_DETECTED_QUAD
						| EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE,
				true);
		filter.setResultCrossVerificationCriteria(
				EnumCapturedResultItemType.CRIT_DESKEWED_IMAGE | EnumCapturedResultItemType.CRIT_DETECTED_QUAD,
				new CrossVerificationCriteria(5, 2));
		mRouter.addResultFilter(filter);
		try {
			mRouter.initSettingsFromFile("id-scan.json");
//            mRouter.initSettingsFromFile("original.json");
			mRouter.setInput(mCamera);
			mRouter.getIntermediateResultManager().addResultReceiver(mScanResultCollector);
		} catch (CaptureVisionRouterException e) {
			throw new RuntimeException(e);
		}
		mRouter.addResultReceiver(new CapturedResultReceiver() {
			@Override
			public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesResult result) {
				onLabelTextReceived(result);
			}

			@Override
			public void onParsedResultsReceived(@NonNull ParsedResult result) {
				if (result.getItems() != null && result.getItems().length > 0) {
					Log.d(TAG, "Parsed code type: " + result.getItems()[0].getCodeType());
				}
			}

			@Override
			public void onCapturedResultReceived(@NonNull CapturedResult result) {
                result.getDecodedBarcodesResult();
				if (succeed) {
					return;
				}
				ScanActivity.this.onCapturedResultReceived(result);
			}
		});
	}

	private void saveBeepStatus() {
		SharedPreferences sp = getSharedPreferences("beep", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sp.edit();
		editor.putBoolean("status", mBeepStatus);
		editor.apply();
	}

	private boolean loadBeepStatus() {
		SharedPreferences sp = getSharedPreferences("beep", Context.MODE_PRIVATE);
		return sp.getBoolean("status", true);
	}

	private void toggleBeepButton() {
		mBeepStatus = loadBeepStatus();
	}

	// The implementation of restartCapture(). 
	private void restartCapture(String template) {
		ScanUiAnimator.clearPendingActions();
		mStartError.setVisibility(View.GONE);
		try {
			mRouter.stopCapturing();
		} catch (Exception ignored) {
		}
		// Start capturing.
		// The template name is a string specified in the template file. 
		// In this sample we can use "ReadPassportAndId", "ReadId" and "ReadPassport".
		// Here the template name is what the user selected on the UI.
		// The completion listener is implemented below. It calls back when the capturing is successful or failed.
		mRouter.startCapturing(template, new CompletionListener() {
			@Override
			public void onSuccess() {
			}

			// If failed, it shows an error message that describes the reasons.
			// License error can be one of the reason of a failure. Besure that you have a valid license when starting capturing.
			@Override
			public void onFailure(int errorCode, String errorString) {
				runOnUiThread(() -> {
					mStartError.setVisibility(View.VISIBLE);
					mStartError.setText(errorString);
				});
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		mIsResumed = true;
		showInitialScanningUi();
		mCamera.open();
		restartCapture(mCurrentTemplate);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mIsResumed = false;
		succeed = false;
		mTorchEnabled = false;
		ScanUiAnimator.clearPendingActions();
		mCamera.close();
		mRouter.stopCapturing();
	}

	@Override
	protected void onStop() {
		// DrawingItem in this sample is the green quadrilateral that highlights the recognized text.
		// Clear the DrawingItem before you leave the camera page. 
		mCameraView.getDrawingLayer(DrawingLayer.DLR_LAYER_ID).clearDrawingItems();
		super.onStop();
	}

	private void onLabelTextReceived(RecognizedTextLinesResult result) {
		// The following code shows how to obtain the recognized MRZ text.
		if (result.getItems() == null) {
			mText = "";
			return;
		}
		// RecognizedTextLinesResult contains an array of TextLineResultItem. Each TextLineResultItem contains a single recognized text.
		TextLineResultItem[] results = result.getItems();
		StringBuilder resultBuilder = new StringBuilder();
		if (results != null) {
			for (TextLineResultItem item : results) {
				resultBuilder.append(item.getText()).append("\n\n");
			}
		}
		mText = resultBuilder.toString();
	}

	private void onCapturedResultReceived(CapturedResult result) {
		ParsedResult parsedResult = result.getParsedResult();
		if (parsedResult == null || parsedResult.getItems() == null || parsedResult.getItems().length == 0) {
			return;
		}
		ParsedResultItem item = parsedResult.getItems()[0];
		String codeType = item.getCodeType();
		if (isDriversLicenseCodeType(codeType)) {
			HashMap<String, String> labelMap = assembleMap(item);
			if (labelMap != null && !labelMap.isEmpty()) {
				runOnUiThread(() -> handleSuccessfulDriversLicenseScan(labelMap));
			}
			return;
		}
		if (!isMrzCodeType(codeType)) {
			return;
		}

		MrzScanResult mrzScanResult = mScanResultCollector.buildMrzResult(mRouter, result);
		if (mrzScanResult != null) {
			runOnUiThread(() -> handleSuccessfulMrzScan(mrzScanResult));
		}
	}

	private void handleSuccessfulDriversLicenseScan(@NonNull HashMap<String, String> labelMap) {
		if (succeed) {
			return;
		}
		if (mBeepStatus) {
			Feedback.beep();
		}
		succeed = true;
		ScanUiAnimator.sequence()
				.then(next -> ScanUiAnimator.showGuideTextZoneAnimate(mGuideFrameView, true, next::run))
				.then(next -> ScanUiAnimator.showTipText(mTipView, R.string.tip2_2_drivers_license, RESULT_ANIMATION_DELAY_MS, next::run))
				.then(() -> openDriversLicenseResultPage(labelMap))
				.start();
	}

	private void handleSuccessfulMrzScan(@NonNull MrzScanResult mrzScanResult) {
		if (succeed) {
			return;
		}
		if (mBeepStatus) {
			Feedback.beep();
		}
		succeed = true;
		ScanUiAnimator.sequence()
				.then(next -> ScanUiAnimator.showGuideTextZoneAnimate(mGuideFrameView, true, next::run))
				.then(next -> ScanUiAnimator.showTip(mTipView, 22, RESULT_ANIMATION_DELAY_MS, next::run))
				.then(() -> openMrzResultPage(mrzScanResult))
				.start();
	}

	private void openDriversLicenseResultPage(@NonNull HashMap<String, String> labelMap) {
		if (isFinishing() || isDestroyed()) {
			return;
		}
		Intent intent = new Intent(this, ResultActivity.class);
		intent.putExtra("labelMap", labelMap);
		startActivity(intent);
	}

	private void openMrzResultPage(@NonNull MrzScanResult mrzScanResult) {
		if (isFinishing() || isDestroyed()) {
			return;
		}
		PendingMrzResultStore.set(mrzScanResult);
		Intent intent = new Intent(this, MrzResultActivity.class);
		startActivity(intent);
	}

	private void showInitialScanningUi() {
		ScanUiAnimator.clearPendingActions();
		mFlashlightToggleView.setSelected(mTorchEnabled);
		mGuideFrameView.findViewById(R.id.guide_frame_container)
				.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.rounded_white_border, null));
		mGuideFrameView.findViewById(R.id.guide_frame_text).setVisibility(View.VISIBLE);
		ScanUiAnimator.showTip(mTipView, 1, 0, null);
	}

	// Assemble the parsed info from the ParsedResultItem.
	private HashMap<String, String> assembleMap(ParsedResultItem item) {
		// Parsed fields are stored in a HashMap with field name as the key and field value as the value.
		// The following code shows how to get the parsed field values.
		HashMap<String, String> entry = item.getParsedFields();
		if (isDriversLicenseCodeType(item.getCodeType())) {
			return assembleDriversLicenseMap(item, entry);
		}

		String mDocumentType = "";
		if (item.getCodeType().equals("MRTD_TD1_ID") || item.getCodeType().equals("MRTD_TD2_ID") || item.getCodeType().equals("MRTD_TD2_FRENCH_ID")) {
			mDocumentType = "ID";
		} else if (item.getCodeType().equals("MRTD_TD3_PASSPORT")) {
			mDocumentType = "PASSPORT";
		}

		String number = entry.get("passportNumber") == null ? entry.get("documentNumber") == null
				? "" : entry.get("documentNumber") : entry.get("passportNumber");
		String mFirstName = entry.get("secondaryIdentifier") == null ? "" : entry.get("secondaryIdentifier");
		String mLastName = entry.get("primaryIdentifier") == null ? "" : " " + entry.get("primaryIdentifier");
		String mName = mFirstName + mLastName;
		if (number == null ||
				entry.get("sex") == null ||
				entry.get("issuingState") == null ||
				entry.get("nationality") == null ||
				entry.get("secondaryIdentifier") == null ||
				entry.get("primaryIdentifier") == null ||
				entry.get("dateOfBirth") == null ||
				entry.get("dateOfExpiry") == null) {
			return null;
		}

		if (item.getCodeType().equals("MRTD_TD1_ID")) {
			if (item.getFieldValidationStatus("line1") == EnumValidationStatus.VS_FAILED
					|| item.getFieldValidationStatus("line2") == EnumValidationStatus.VS_FAILED
					|| item.getFieldValidationStatus("line3") == EnumValidationStatus.VS_FAILED) {
				return null;
			}
		} else {
			if (item.getFieldValidationStatus("line1") == EnumValidationStatus.VS_FAILED
					|| item.getFieldValidationStatus("line2") == EnumValidationStatus.VS_FAILED) {
				return null;
			}
		}

		int age = -1;
		int expiryYear = 0;
		try {
			int year = Integer.parseInt(entry.get("birthYear"));
			int month = Integer.parseInt(entry.get("birthMonth"));
			int day = Integer.parseInt(entry.get("birthDay"));
			expiryYear = Integer.parseInt(entry.get("expiryYear")) + 2000;
			age = calculateAge(year, month, day);
		} catch (Exception e) {
			e.printStackTrace();
		}
		HashMap<String, String> properties = new HashMap<>(11);
		properties.put("Name", mName);
		properties.put("Sex", entry.get("sex"));
		properties.put("Age", age == -1 ? "Unknown" : age + "");
		properties.put("Document Number", number);
		properties.put("Issuing State", entry.get("issuingState"));
		properties.put("Nationality", entry.get("nationality"));
		properties.put("Date of Birth(YY-MM-DD)", mBirthYear + "-" +
				entry.get("birthMonth") + "-" + entry.get("birthDay"));
		properties.put("Date of Expiry(YY-MM-DD)", expiryYear + "-" +
				entry.get("expiryMonth") + "-" + entry.get("expiryDay"));
		properties.put("Personal Number", entry.get("personalNumber"));
		properties.put("Primary Identifier(s)", entry.get("primaryIdentifier"));
		properties.put("Secondary Identifier(s)", entry.get("secondaryIdentifier"));
		properties.put("Document Type", mDocumentType);
		return properties;
	}

	private boolean isDriversLicenseCodeType(String codeType) {
		return "AAMVA_DL_ID".equals(codeType)
				|| "AAMVA_DL_ID_WITH_MAG_STRIPE".equals(codeType)
				|| "SOUTH_AFRICA_DL".equals(codeType);
	}

	private boolean isMrzCodeType(String codeType) {
		return "MRTD_TD1_ID".equals(codeType)
				|| "MRTD_TD2_ID".equals(codeType)
				|| "MRTD_TD2_FRENCH_ID".equals(codeType)
				|| "MRTD_TD3_PASSPORT".equals(codeType);
	}

	private HashMap<String, String> assembleDriversLicenseMap(ParsedResultItem item, HashMap<String, String> entry) {
		String firstName = firstNonEmpty(entry.get("firstName"), entry.get("givenName"), entry.get("secondaryIdentifier"), entry.get("initials"));
		String lastName = firstNonEmpty(entry.get("lastName"), entry.get("surname"), entry.get("primaryIdentifier"));
		String fullName = firstNonEmpty(entry.get("fullName"), entry.get("name"));
		String name = fullName;
		if (name == null || name.isEmpty()) {
			name = joinName(firstName, lastName);
		}

		String licenseNumber = firstNonEmpty(entry.get("licenseNumber"), entry.get("DLorID_Number"), entry.get("idNumber"));
		if (name == null || name.isEmpty() || licenseNumber == null || licenseNumber.isEmpty()) {
			return null;
		}

		String sex = firstNonEmpty(entry.get("sex"), entry.get("gender"));
		if (sex == null || sex.isEmpty()) {
			sex = "Unknown";
		}

		String issuingState = firstNonEmpty(entry.get("jurisdictionCode"), entry.get("stateOrProvince"), entry.get("issuingCountry"), entry.get("idIssuedCountry"));
		String nationality = firstNonEmpty(entry.get("issuingCountry"), entry.get("idIssuedCountry"));
		String birthDateText = formatDate(firstNonEmpty(entry.get("birthDate"), entry.get("dateOfBirth")));
		String expiryDateText = formatDate(firstNonEmpty(entry.get("expirationDate"), entry.get("licenseValidityTo"), entry.get("dateOfExpiry")));

		int age = -1;
		String birthDateRaw = firstNonEmpty(entry.get("birthDate"), entry.get("dateOfBirth"));
		int[] parts = parseDateParts(birthDateRaw);
		if (parts != null) {
			age = calculateAgeFromFullYear(parts[0], parts[1], parts[2]);
		}

		HashMap<String, String> properties = new HashMap<>(11);
		properties.put("Name", name);
		properties.put("Sex", sex);
		properties.put("Age", age == -1 ? "Unknown" : String.valueOf(age));
		properties.put("Document Number", licenseNumber);
		properties.put("Issuing State", issuingState == null ? "Unknown" : issuingState);
		properties.put("Nationality", nationality == null ? "Unknown" : nationality);
		properties.put("Date of Birth(YY-MM-DD)", birthDateText == null ? "Unknown" : birthDateText);
		properties.put("Date of Expiry(YY-MM-DD)", expiryDateText == null ? "Unknown" : expiryDateText);
		properties.put("Personal Number", firstNonEmpty(entry.get("vehicleClass"), entry.get("idNumberType")));
		properties.put("Primary Identifier(s)", lastName == null ? "" : lastName);
		properties.put("Secondary Identifier(s)", firstName == null ? "" : firstName);
		properties.put("Document Type", "DriversLicense");
		return properties;
	}

	private String firstNonEmpty(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (value != null) {
				String trimmed = value.trim();
				if (!trimmed.isEmpty()) {
					return trimmed;
				}
			}
		}
		return null;
	}

	private String joinName(String firstName, String lastName) {
		if (firstName == null && lastName == null) {
			return null;
		}
		if (firstName == null) {
			return lastName;
		}
		if (lastName == null) {
			return firstName;
		}
		return firstName + " " + lastName;
	}

	private String formatDate(String rawDate) {
		int[] parts = parseDateParts(rawDate);
		if (parts == null) {
			return rawDate;
		}
		return parts[0] + "-" + twoDigit(parts[1]) + "-" + twoDigit(parts[2]);
	}

	private int[] parseDateParts(String rawDate) {
		if (rawDate == null) {
			return null;
		}
		String digits = rawDate.replaceAll("[^0-9]", "");
		if (digits.length() == 8) {
			try {
				int year = Integer.parseInt(digits.substring(0, 4));
				int month = Integer.parseInt(digits.substring(4, 6));
				int day = Integer.parseInt(digits.substring(6, 8));
				return new int[]{year, month, day};
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		if (digits.length() == 6) {
			try {
				int shortYear = Integer.parseInt(digits.substring(0, 2));
				int month = Integer.parseInt(digits.substring(2, 4));
				int day = Integer.parseInt(digits.substring(4, 6));
				int year = shortYear >= 50 ? 1900 + shortYear : 2000 + shortYear;
				return new int[]{year, month, day};
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private String twoDigit(int value) {
		return value < 10 ? "0" + value : String.valueOf(value);
	}

	private int calculateAgeFromFullYear(int year, int month, int day) {
		Calendar calendar = Calendar.getInstance();
		int cYear = calendar.get(Calendar.YEAR);
		int cMonth = calendar.get(Calendar.MONTH) + 1;
		int cDay = calendar.get(Calendar.DAY_OF_MONTH);
		int age = cYear - year;
		if (cMonth < month || (cMonth == month && cDay < day)) {
			age = age - 1;
		}
		return Math.max(age, 0);
	}

    // Age information is not directly obtained from the MRZ but you can calculate it based on the date of birth.
	// The following 2 methods are used to calculate the age.
	private int calculateAge(int year, int month, int day) {
		Calendar calendar = Calendar.getInstance();
		int cYear = calendar.get(Calendar.YEAR);
		int cMonth = calendar.get(Calendar.MONTH) + 1;
		int cDay = calendar.get(Calendar.DAY_OF_MONTH);
		mBirthYear = 1900 + year;
		int diffYear = cYear - mBirthYear;
		int diffMonth = cMonth - month;
		int diffDay = cDay - day;
		int age = minusYear(diffYear, diffMonth, diffDay);
		if (age > 100) {
			mBirthYear = 2000 + year;
			diffYear = cYear - mBirthYear;
			age = minusYear(diffYear, diffMonth, diffDay);
		} else if (age < 0) {
			age = 0;
		}
		return age;
	}


	private int minusYear(int diffYear, int diffMonth, int diffDay) {
		int age = Math.max(diffYear, 0);
		if (diffMonth < 0) {
			age = age - 1;

		} else if (diffMonth == 0) {
			if (diffDay < 0) {
				age = age - 1;
			}
		}
		return age;
	}
}
