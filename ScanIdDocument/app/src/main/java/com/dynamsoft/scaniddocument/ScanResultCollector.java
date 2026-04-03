package com.dynamsoft.scaniddocument;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dynamsoft.core.basic_structures.EnumCrossVerificationStatus;
import com.dynamsoft.core.basic_structures.Quadrilateral;
import com.dynamsoft.core.intermediate_results.IntermediateResultExtraInfo;
import com.dynamsoft.core.intermediate_results.ScaledColourImageUnit;
import com.dynamsoft.cvr.CaptureVisionRouter;
import com.dynamsoft.cvr.CapturedResult;
import com.dynamsoft.cvr.intermediate_results.IntermediateResultReceiver;
import com.dynamsoft.dcp.ParsedResult;
import com.dynamsoft.dcp.ParsedResultItem;
import com.dynamsoft.ddn.DetectedQuadResultItem;
import com.dynamsoft.ddn.ProcessedDocumentResult;
import com.dynamsoft.ddn.intermediate_results.DeskewedImageUnit;
import com.dynamsoft.ddn.intermediate_results.DetectedQuadsUnit;
import com.dynamsoft.diu.IdentityProcessor;
import com.dynamsoft.dlr.intermediate_results.LocalizedTextLinesUnit;
import com.dynamsoft.dlr.intermediate_results.RecognizedTextLinesUnit;

final class ScanResultCollector implements IntermediateResultReceiver {
	private final IdentityProcessor idProcessor = new IdentityProcessor();

	private ScaledColourImageUnit scaledColourImageUnit;
	private LocalizedTextLinesUnit localizedTextLinesUnit;
	private RecognizedTextLinesUnit recognizedTextLinesUnit;
	private DetectedQuadsUnit detectedQuadsUnit;
	private DeskewedImageUnit deskewedImageUnit;

	@Nullable
	MrzScanResult buildMrzResult(@NonNull CaptureVisionRouter router, @NonNull CapturedResult capturedResult) {
		DetectedQuadResultItem quadItem = getDetectedQuad(capturedResult);
		if (quadItem == null) {
			return null;
		}

		Quadrilateral precisePortraitLocation = findPortraitLocation(quadItem);
		if (precisePortraitLocation != null) {
			Quadrilateral documentRegion = quadItem.getLocation();
			boolean isValid = documentRegion.isPointInQuadrilateral(precisePortraitLocation.points[0])
					&& documentRegion.isPointInQuadrilateral(precisePortraitLocation.points[1])
					&& documentRegion.isPointInQuadrilateral(precisePortraitLocation.points[2])
					&& documentRegion.isPointInQuadrilateral(precisePortraitLocation.points[3])
					&& documentRegion.getArea() / precisePortraitLocation.getArea() >= 3;
			if (!isValid) {
				return null;
			}
		}

		ParsedResult parsedResult = capturedResult.getParsedResult();
		ParsedResultItem parsedResultItem = parsedResult == null || parsedResult.getItems() == null || parsedResult.getItems().length == 0
				? null
				: parsedResult.getItems()[0];
		MrzData mrzData = MrzData.fromParsedResultItem(parsedResultItem);

		MrzScanResult scanResult = new MrzScanResult();
		scanResult.mrzData = mrzData;

		long originalInstance = BundleImageBridge.getWrapImageDataInstance(
				router.getIntermediateResultManager(),
				capturedResult.getOriginalImageHashId());
		if (mrzData != null) {
			scanResult.mrzPageOriginalImageInstance = originalInstance;
		} else {
			scanResult.anotherPageOriginalImageInstance = originalInstance;
		}

		long documentInstance = BundleImageBridge.getDeskewedWrapImageDataInstance(
				router.getIntermediateResultManager(),
				capturedResult.getOriginalImageHashId(),
				toPointsArray(quadItem.getLocation()));
		if (mrzData != null) {
			scanResult.mrzPageDocumentImageInstance = documentInstance;
		} else {
			scanResult.anotherPageDocumentImageInstance = documentInstance;
		}

		if (precisePortraitLocation != null) {
			scanResult.portraitImageInstance = BundleImageBridge.getDeskewedWrapImageDataInstance(
					router.getIntermediateResultManager(),
					capturedResult.getOriginalImageHashId(),
					toPointsArray(precisePortraitLocation));
		}
		return scanResult;
	}

	@Nullable
	private DetectedQuadResultItem getDetectedQuad(@NonNull CapturedResult capturedResult) {
		ProcessedDocumentResult documentResult = capturedResult.getProcessedDocumentResult();
		if (documentResult == null) {
			return null;
		}
		DetectedQuadResultItem[] detectedQuadItems = documentResult.getDetectedQuadResultItems();
		if (detectedQuadItems == null || detectedQuadItems.length == 0) {
			return null;
		}
		DetectedQuadResultItem quadItem = detectedQuadItems[0];
		if (quadItem.getCrossVerificationStatus() == EnumCrossVerificationStatus.CVS_FAILED) {
			return null;
		}
		return quadItem;
	}

	@Nullable
	private Quadrilateral findPortraitLocation(@Nullable DetectedQuadResultItem quadItem) {
		if (quadItem == null
				|| localizedTextLinesUnit == null
				|| recognizedTextLinesUnit == null
				|| detectedQuadsUnit == null
				|| deskewedImageUnit == null
				|| scaledColourImageUnit == null) {
			return null;
		}

		boolean hasPortraitZone = false;
		for (int index = 0; index < localizedTextLinesUnit.getAuxiliaryRegionElementsCount(); index++) {
			if ("PortraitZone".equals(localizedTextLinesUnit.getAuxiliaryRegionElement(index).getName())
					&& localizedTextLinesUnit.getAuxiliaryRegionElement(index).getConfidence() > 60) {
				hasPortraitZone = true;
				break;
			}
		}
		if (!hasPortraitZone || detectedQuadsUnit.getCount() == 0) {
			return null;
		}

		Quadrilateral portraitLocation = idProcessor.findPortraitZone(
				scaledColourImageUnit,
				localizedTextLinesUnit,
				recognizedTextLinesUnit,
				detectedQuadsUnit,
				deskewedImageUnit);
		if (portraitLocation == null) {
			return null;
		}
		return portraitLocation;
	}

	@NonNull
	private int[] toPointsArray(@NonNull Quadrilateral quadrilateral) {
		int[] points = new int[quadrilateral.points.length * 2];
		for (int index = 0; index < quadrilateral.points.length; index++) {
			points[index * 2] = quadrilateral.points[index].x;
			points[index * 2 + 1] = quadrilateral.points[index].y;
		}
		return points;
	}

	@Override
	public void onScaledColourImageUnitReceived(@NonNull ScaledColourImageUnit unit, IntermediateResultExtraInfo info) {
		scaledColourImageUnit = unit;
	}

	@Override
	public void onLocalizedTextLinesReceived(@NonNull LocalizedTextLinesUnit unit, IntermediateResultExtraInfo info) {
		localizedTextLinesUnit = unit;
	}

	@Override
	public void onRecognizedTextLinesReceived(@NonNull RecognizedTextLinesUnit unit, IntermediateResultExtraInfo info) {
		recognizedTextLinesUnit = unit;
	}

	@Override
	public void onDetectedQuadsReceived(@NonNull DetectedQuadsUnit unit, IntermediateResultExtraInfo info) {
		detectedQuadsUnit = unit;
	}

	@Override
	public void onDeskewedImageReceived(@NonNull DeskewedImageUnit unit, IntermediateResultExtraInfo info) {
		deskewedImageUnit = unit;
	}
}