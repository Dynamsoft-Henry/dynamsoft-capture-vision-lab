package com.dynamsoft.scaniddocument;

import androidx.annotation.Nullable;

import com.dynamsoft.core.basic_structures.ImageData;

public final class MrzScanResult {
	MrzData mrzData;
	long mrzPageOriginalImageInstance;
	long mrzPageDocumentImageInstance;
	long anotherPageOriginalImageInstance;
	long anotherPageDocumentImageInstance;
	long portraitImageInstance;
	transient ImageData primaryOriginalImage;
	transient ImageData primaryDocumentImage;
	transient ImageData secondaryOriginalImage;
	transient ImageData secondaryDocumentImage;
	transient ImageData portraitImage;

	public MrzData getData() {
		return mrzData;
	}

	@Nullable
	public ImageData getDocumentImage(EnumDocumentSide documentSide) {
		return documentSide == EnumDocumentSide.DS_MRZ ? getPrimaryDocumentImage() : getSecondaryDocumentImage();
	}

	@Nullable
	public ImageData getOriginalImage(EnumDocumentSide documentSide) {
		return documentSide == EnumDocumentSide.DS_MRZ ? getPrimaryOriginalImage() : getSecondaryOriginalImage();
	}

	@Nullable
	public ImageData getPortraitImage() {
		if (portraitImageInstance == 0) {
			return null;
		}
		if (portraitImage == null) {
			portraitImage = BundleImageBridge.getImageData(portraitImageInstance);
		}
		return portraitImage;
	}

	public void retainAllImageInstances() {
		BundleImageBridge.retainImageData(mrzPageOriginalImageInstance);
		BundleImageBridge.retainImageData(mrzPageDocumentImageInstance);
		BundleImageBridge.retainImageData(anotherPageOriginalImageInstance);
		BundleImageBridge.retainImageData(anotherPageDocumentImageInstance);
		BundleImageBridge.retainImageData(portraitImageInstance);
	}

	private ImageData getPrimaryOriginalImage() {
		if (mrzPageOriginalImageInstance == 0) {
			return null;
		}
		if (primaryOriginalImage == null) {
			primaryOriginalImage = BundleImageBridge.getImageData(mrzPageOriginalImageInstance);
		}
		return primaryOriginalImage;
	}

	private ImageData getPrimaryDocumentImage() {
		if (mrzPageDocumentImageInstance == 0) {
			return null;
		}
		if (primaryDocumentImage == null) {
			primaryDocumentImage = BundleImageBridge.getImageData(mrzPageDocumentImageInstance);
		}
		return primaryDocumentImage;
	}

	private ImageData getSecondaryOriginalImage() {
		if (anotherPageOriginalImageInstance == 0) {
			return null;
		}
		if (secondaryOriginalImage == null) {
			secondaryOriginalImage = BundleImageBridge.getImageData(anotherPageOriginalImageInstance);
		}
		return secondaryOriginalImage;
	}

	private ImageData getSecondaryDocumentImage() {
		if (anotherPageDocumentImageInstance == 0) {
			return null;
		}
		if (secondaryDocumentImage == null) {
			secondaryDocumentImage = BundleImageBridge.getImageData(anotherPageDocumentImageInstance);
		}
		return secondaryDocumentImage;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		BundleImageBridge.releaseImageData(mrzPageOriginalImageInstance);
		BundleImageBridge.releaseImageData(mrzPageDocumentImageInstance);
		BundleImageBridge.releaseImageData(anotherPageOriginalImageInstance);
		BundleImageBridge.releaseImageData(anotherPageDocumentImageInstance);
		BundleImageBridge.releaseImageData(portraitImageInstance);
	}
}