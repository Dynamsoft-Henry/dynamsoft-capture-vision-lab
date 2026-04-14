package com.dynamsoft.dbr.scanbrandlabel;

import com.dynamsoft.core.basic_structures.ImageData;

import java.util.ArrayList;
import java.util.List;

final class ResultPayloadStore {

    private static Payload payload;

    private ResultPayloadStore() {
    }

    static synchronized void store(ImageData deskewedImage, List<String> barcodeTexts, List<String> textLineContents) {
        payload = new Payload(deskewedImage, new ArrayList<>(barcodeTexts), new ArrayList<>(textLineContents));
    }

    static synchronized Payload get() {
        return payload;
    }

    static synchronized void clear() {
        payload = null;
    }

    static final class Payload {
        private final ImageData deskewedImage;
        private final ArrayList<String> barcodeTexts;
        private final ArrayList<String> textLineContents;

        Payload(ImageData deskewedImage, ArrayList<String> barcodeTexts, ArrayList<String> textLineContents) {
            this.deskewedImage = deskewedImage;
            this.barcodeTexts = barcodeTexts;
            this.textLineContents = textLineContents;
        }

        ImageData getDeskewedImage() {
            return deskewedImage;
        }

        ArrayList<String> getBarcodeTexts() {
            return new ArrayList<>(barcodeTexts);
        }

        ArrayList<String> getTextLineContents() {
            return new ArrayList<>(textLineContents);
        }
    }
}