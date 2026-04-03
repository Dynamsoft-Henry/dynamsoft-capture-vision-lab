package com.dynamsoft.scaniddocument;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dynamsoft.core.basic_structures.ImageData;
import com.dynamsoft.cvr.intermediate_results.IntermediateResultManager;

import java.lang.reflect.Method;

final class BundleImageBridge {
	private static final String TAG = "BundleImageBridge";
	private static final Method WRAP_IMAGE_METHOD;
	private static final Method DESKEWED_WRAP_IMAGE_METHOD;
	private static final Method GET_IMAGE_DATA_METHOD;
	private static final Method RETAIN_IMAGE_DATA_METHOD;
	private static final Method RELEASE_IMAGE_DATA_METHOD;

	static {
		Method wrapImageMethod = null;
		Method deskewedWrapImageMethod = null;
		Method getImageDataMethod = null;
		Method retainImageDataMethod = null;
		Method releaseImageDataMethod = null;
		try {
			Class<?> scannerActivityClass = Class.forName("com.dynamsoft.mrzscannerbundle.ui.MRZScannerActivity");
			wrapImageMethod = scannerActivityClass.getDeclaredMethod(
					"nativeGetWrapImageDataInstance",
					IntermediateResultManager.class,
					String.class);
			wrapImageMethod.setAccessible(true);

			deskewedWrapImageMethod = scannerActivityClass.getDeclaredMethod(
					"nativeGetDeskewedWrapImageDataInstance",
					IntermediateResultManager.class,
					String.class,
					int[].class);
			deskewedWrapImageMethod.setAccessible(true);

			Class<?> scanResultClass = Class.forName("com.dynamsoft.mrzscannerbundle.ui.MRZScanResult");
			getImageDataMethod = scanResultClass.getDeclaredMethod("nativeGetImageData", long.class);
			getImageDataMethod.setAccessible(true);
			retainImageDataMethod = scanResultClass.getDeclaredMethod("nativeRetainImageData", long.class);
			retainImageDataMethod.setAccessible(true);
			releaseImageDataMethod = scanResultClass.getDeclaredMethod("nativeReleaseImageData", long.class);
			releaseImageDataMethod.setAccessible(true);
		} catch (Throwable t) {
			Log.e(TAG, "Failed to initialize bundle image bridge.", t);
		}
		WRAP_IMAGE_METHOD = wrapImageMethod;
		DESKEWED_WRAP_IMAGE_METHOD = deskewedWrapImageMethod;
		GET_IMAGE_DATA_METHOD = getImageDataMethod;
		RETAIN_IMAGE_DATA_METHOD = retainImageDataMethod;
		RELEASE_IMAGE_DATA_METHOD = releaseImageDataMethod;
	}

	private BundleImageBridge() {
	}

	static long getWrapImageDataInstance(@NonNull IntermediateResultManager intermediateResultManager, @Nullable String imageHashId) {
		if (WRAP_IMAGE_METHOD == null || imageHashId == null || imageHashId.isEmpty()) {
			return 0L;
		}
		try {
			Object value = WRAP_IMAGE_METHOD.invoke(null, intermediateResultManager, imageHashId);
			return value instanceof Long ? (Long) value : 0L;
		} catch (Throwable t) {
			Log.e(TAG, "Failed to wrap original image instance.", t);
			return 0L;
		}
	}

	static long getDeskewedWrapImageDataInstance(@NonNull IntermediateResultManager intermediateResultManager,
									 @Nullable String imageHashId,
									 @NonNull int[] points) {
		if (DESKEWED_WRAP_IMAGE_METHOD == null || imageHashId == null || imageHashId.isEmpty()) {
			return 0L;
		}
		try {
			Object value = DESKEWED_WRAP_IMAGE_METHOD.invoke(null, intermediateResultManager, imageHashId, points);
			return value instanceof Long ? (Long) value : 0L;
		} catch (Throwable t) {
			Log.e(TAG, "Failed to wrap deskewed image instance.", t);
			return 0L;
		}
	}

	@Nullable
	static ImageData getImageData(long instance) {
		if (GET_IMAGE_DATA_METHOD == null || instance == 0) {
			return null;
		}
		try {
			Object value = GET_IMAGE_DATA_METHOD.invoke(null, instance);
			return value instanceof ImageData ? (ImageData) value : null;
		} catch (Throwable t) {
			Log.e(TAG, "Failed to obtain ImageData from instance.", t);
			return null;
		}
	}

	static void retainImageData(long instance) {
		invokeVoid(RETAIN_IMAGE_DATA_METHOD, instance, "retain image instance");
	}

	static void releaseImageData(long instance) {
		invokeVoid(RELEASE_IMAGE_DATA_METHOD, instance, "release image instance");
	}

	private static void invokeVoid(@Nullable Method method, long instance, @NonNull String action) {
		if (method == null || instance == 0) {
			return;
		}
		try {
			method.invoke(null, instance);
		} catch (Throwable t) {
			Log.e(TAG, "Failed to " + action + ".", t);
		}
	}
}