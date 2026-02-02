package com.modlix.saas.files.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RangeDownloadUtil {

    private static final Logger logger = LoggerFactory.getLogger(RangeDownloadUtil.class);

    // Byte chunk size used while streaming to the client (20 KB keeps memory
    // overhead modest).
    private static final int STREAM_BUFFER_SIZE_BYTES = 20 * 1024;
    // Default cache horizon for range responses so browsers can reuse results (1
    // week).
    private static final long DEFAULT_CACHE_EXPIRATION_MILLIS = 604800000L;
    // Boundary identifier for multipart range responses (RFC 7233).
    private static final String BOUNDARY_MARKER = "MULTIPART_BYTERANGES";

    public static void serveResource(HttpServletRequest request, HttpServletResponse response, InputStream sourceStream,
            Long contentLength) throws IOException {

        // Represents the entire payload; used when no Range header is supplied.
        ByteRange completeRange = new ByteRange(0, contentLength - 1, contentLength);
        List<ByteRange> requestedRanges = new ArrayList<>();

        // Parse the Range header (when present) and collect every requested byte
        // window.
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null) {

            if (!rangeHeader.matches("^bytes=\\d*-\\d*(,\\d*-\\d*)*$")) {
                response.setHeader("Content-Range", "bytes */" + contentLength);
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return;
            }

            if (requestedRanges.isEmpty()) {
                for (String rangePart : rangeHeader.substring(6).split(",")) {
                    long startInclusive = ByteRange.parseLong(rangePart, 0, rangePart.indexOf("-"));
                    long endInclusive = ByteRange.parseLong(rangePart, rangePart.indexOf("-") + 1, rangePart.length());

                    if (startInclusive == -1) {
                        startInclusive = contentLength - endInclusive;
                        endInclusive = contentLength - 1;
                    } else if (endInclusive == -1 || endInclusive > contentLength - 1) {
                        endInclusive = contentLength - 1;
                    }

                    if (startInclusive > endInclusive) {
                        response.setHeader("Content-Range", "bytes */" + contentLength);
                        response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        return;
                    }

                    requestedRanges.add(new ByteRange(startInclusive, endInclusive, contentLength));
                }
            }
        }

        // Apply baseline response headers before streaming data to the client.
        response.setBufferSize(STREAM_BUFFER_SIZE_BYTES);
        response.setHeader("Accept-Ranges", "bytes");
        response.setDateHeader("Expires", System.currentTimeMillis() + DEFAULT_CACHE_EXPIRATION_MILLIS);

        try (InputStream input = sourceStream;
                OutputStream output = response.getOutputStream()) {

            if (requestedRanges.isEmpty()) {

                logger.info("Range request resolved to full-file download");
                response.setHeader("Content-Range",
                        "bytes " + completeRange.startInclusive + "-" + completeRange.endInclusive + "/"
                                + completeRange.resourceLength);
                response.setHeader("Content-Length", String.valueOf(completeRange.segmentLength));
                ByteRange.writeRange(input, output, contentLength, completeRange.startInclusive,
                        completeRange.segmentLength);

            } else if (requestedRanges.size() == 1) {

                ByteRange requestedRange = requestedRanges.get(0);
                logger.info("Range request resolved to single segment [{}-{}]", requestedRange.startInclusive,
                        requestedRange.endInclusive);
                response.setHeader("Content-Range",
                        "bytes " + requestedRange.startInclusive + "-" + requestedRange.endInclusive + "/"
                                + requestedRange.resourceLength);
                response.setHeader("Content-Length", String.valueOf(requestedRange.segmentLength));
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                ByteRange.writeRange(input, output, contentLength, requestedRange.startInclusive,
                        requestedRange.segmentLength);

            } else {

                response.setContentType("multipart/byteranges; boundary=" + BOUNDARY_MARKER);
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

                for (ByteRange requestedRange : requestedRanges) {
                    logger.info("Range request resolved to multipart segment [{}-{}]", requestedRange.startInclusive,
                            requestedRange.endInclusive);
                    ((ServletOutputStream) output).println();
                    ((ServletOutputStream) output).println("--" + BOUNDARY_MARKER);
                    ((ServletOutputStream) output)
                            .println("Content-Range: bytes " + requestedRange.startInclusive + "-"
                                    + requestedRange.endInclusive + "/"
                                    + requestedRange.resourceLength);
                    ByteRange.writeRange(input, output, contentLength, requestedRange.startInclusive,
                            requestedRange.segmentLength);
                }

                ((ServletOutputStream) output).println();
                ((ServletOutputStream) output).println("--" + BOUNDARY_MARKER + "--");
            }
        }

    }

    // Immutable representation of a requested byte segment with helpers to stream
    // the correct slice.
    private static class ByteRange {
        final long startInclusive;
        final long endInclusive;
        final long segmentLength;
        final long resourceLength;

        ByteRange(long startInclusive, long endInclusive, long resourceLength) {
            this.startInclusive = startInclusive;
            this.endInclusive = endInclusive;
            this.segmentLength = endInclusive - startInclusive + 1;
            this.resourceLength = resourceLength;
        }

        static long parseLong(String value, int beginIndex, int endIndex) {
            String substring = value.substring(beginIndex, endIndex);
            return substring.isEmpty() ? -1 : Long.parseLong(substring);
        }

        static void writeRange(InputStream input, OutputStream output, long resourceLength, long startInclusive,
                long segmentLength)
                throws IOException {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE_BYTES];
            int read;

            if (resourceLength == segmentLength) {
                // Whole-file transfer: stream until EOF with the configured buffer size.
                while ((read = input.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } else {
                // Partial transfer: fast-forward to the requested offset and copy only the
                // desired window.
                input.skip(startInclusive);
                long remainingBytes = segmentLength;

                while ((read = input.read(buffer)) > 0) {
                    remainingBytes -= read;
                    if (remainingBytes > 0) {
                        output.write(buffer, 0, read);
                        output.flush();
                    } else {
                        output.write(buffer, 0, (int) (remainingBytes + read));
                        output.flush();
                        break;
                    }
                }
            }
        }
    }
}
