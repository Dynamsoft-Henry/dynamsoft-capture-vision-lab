package com.dynamsoft.scaniddocument;

import androidx.annotation.Nullable;

final class PendingMrzResultStore {
	private static MrzScanResult pendingResult;

	private PendingMrzResultStore() {
	}

	static synchronized void set(MrzScanResult result) {
		pendingResult = result;
	}

	@Nullable
	static synchronized MrzScanResult get() {
		return pendingResult;
	}

	static synchronized void clear() {
		pendingResult = null;
	}
}