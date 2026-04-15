package com.dynamsoft.dbr.scanbrandlabel;

import com.dynamsoft.core.basic_structures.ImageData;

final class ResultPayloadStore {

    private static Payload payload;

    private ResultPayloadStore() {
    }

    static synchronized void store(
            ImageData labelImage,
            String traceabilityCode,
            String serialNumber,
            String partNumber,
            String lotCode
    ) {
        payload = new Payload(labelImage, traceabilityCode, serialNumber, partNumber, lotCode);
    }

    static synchronized Payload get() {
        return payload;
    }

    static synchronized void clear() {
        payload = null;
    }

    static final class Payload {
        private final ImageData labelImage;
        private final String traceabilityCode;
        private final String serialNumber;
        private final String partNumber;
        private final String lotCode;

        Payload(
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

        ImageData getLabelImage() {
            return labelImage;
        }

        String getTraceabilityCode() {
            return traceabilityCode;
        }

        String getSerialNumber() {
            return serialNumber;
        }

        String getPartNumber() {
            return partNumber;
        }

        String getLotCode() {
            return lotCode;
        }
    }
}