package com.fincity.saas.commons.file;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.DataFileType;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.ibm.icu.text.SimpleDateFormat;
import com.monitorjbl.xlsx.StreamingReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class DataFileReader implements Closeable {

    private FilePart file;
    private DataFileType fileType;

    private List<String> headers = new ArrayList<>();

    private InputStreamReader reader;
    private int currentRow = 0;
    private Iterator<Row> sheet;
    private Workbook workbook;

    private static final Logger logger = LoggerFactory.getLogger(DataFileReader.class);

    public DataFileReader(FilePart file, DataFileType fileType) {
        this.file = file;
        this.fileType = fileType;
    }

    public List<String> getHeaders() {
        return this.headers;
    }

    @Nullable public List<String> readRow() {

        if (this.currentRow == 0 && !this.fileType.isNestedStructure()) this.readHeaderRow();

        if (!this.fileType.isNestedStructure()) {
            if (this.fileType == DataFileType.CSV || this.fileType == DataFileType.TSV) {
                List<String> row = this.readLineFromReaderForCSVTSV();
                this.currentRow++;
                return row;
            } else if (this.sheet.hasNext()) {

                List<String> row = this.processEachRecord(this.sheet.next());
                this.currentRow++;
                return row;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable public Map<String, Object> readObject() throws IOException { // NOSONAR
        // It doesn't make sense to break this to read objects from the JSON or JSONL

        if (!this.fileType.isNestedStructure()) return null;

        if (currentRow == 0) this.readHeaderRow();

        if (this.currentRow == 0 && this.fileType == DataFileType.JSON) {

            char c = (char) this.reader.read();
            if (c == -1) return null;
            if (c != '[') throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid JSON file");
        }

        int c;
        while ((c = this.reader.read()) != -1 && c != '{')
            ;

        if (c == -1) return null;

        if (c != '{') throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid JSON file");
        int count = 1;
        boolean inDoubleQuotes = false;
        StringBuilder str = new StringBuilder();
        str.append((char) c);
        while (count != 0) {
            c = this.reader.read();
            if (c != -1) {
                str.append((char) c);
                if (c == '"') inDoubleQuotes = !inDoubleQuotes;
                if (inDoubleQuotes) continue;
                if (c == '{') count++;
                else if (c == '}') count--;
            }
        }

        this.currentRow++;
        return new Gson().fromJson(str.toString(), HashMap.class);
    }

    public void readHeaderRow() {

        try {

            if (this.fileType.isNestedStructure()
                    || this.fileType == DataFileType.CSV
                    || this.fileType == DataFileType.TSV) {

                reader = new InputStreamReader(this.getInputStreamFromFluxDataBuffer(this.file.content()));
                if (this.fileType == DataFileType.CSV || this.fileType == DataFileType.TSV)
                    this.headers = this.readLineFromReaderForCSVTSV();

            } else if (this.fileType == DataFileType.XLSX || this.fileType == DataFileType.XLS) {

                workbook = StreamingReader.builder()
                        .rowCacheSize(100)
                        .bufferSize(4096)
                        .open(getInputStreamFromFluxDataBuffer(this.file.content()));

                this.sheet = workbook.getSheetAt(0).iterator();

                this.headers = this.sheet.hasNext() ? this.processEachRecord(this.sheet.next()) : List.of();

                this.currentRow++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> processEachRecord(Row row) {

        List<String> excelRecord = new ArrayList<>();

        for (int c = 0; c < row.getLastCellNum(); c++) {

            Cell cell = row.getCell(c, MissingCellPolicy.RETURN_BLANK_AS_NULL);

            if (cell != null) {
                if (cell.getCellType().equals(CellType.NUMERIC) && DateUtil.isCellDateFormatted(cell)) {

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    excelRecord.add(sdf.format(cell.getDateCellValue()));
                } else excelRecord.add(cell.getStringCellValue());
            } else excelRecord.add("");
        }

        return excelRecord;
    }

    @Nullable public List<String> readLineFromReaderForCSVTSV() { // NOSONAR
        // Breaking this won't make sense

        StringBuilder str = new StringBuilder();

        try {

            int ch;
            boolean inDoubleQuotes = false;
            while ((ch = reader.read()) != -1) {

                if (ch == '"') inDoubleQuotes = !inDoubleQuotes;

                if (!inDoubleQuotes && ch == '\n') break;

                str.append((char) ch);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int length = str.length();
        if (length == 0) return null;

        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean withIn = false;
        int i = 0;
        while ((i + 1) < length) {
            char ch = str.charAt(i);
            if (ch == (this.fileType == DataFileType.CSV ? ',' : '\t') && !withIn) {
                list.add(StringUtil.removeLineFeedOrNewLineChars(sb.toString()));
                sb = new StringBuilder();
            } else if (ch == '"') {
                int j = i;
                while ((str.charAt(j) == '"') && (j + 1 < length)) j++;
                if ((j - i) % 2 == 1) withIn = !withIn;
                if (j - i != 1) {
                    for (int k = 0; k < ((j - i) / 2); k++) sb.append('"');
                    i = j - 1;
                }
            } else sb.append(ch);
            i++;
        }

        sb.append(str.charAt(i) != '\n' ? str.charAt(i) : "");
        if (sb.length() > 0) list.add(StringUtil.removeLineFeedOrNewLineChars(sb.toString()));

        if (list.isEmpty()) return null;

        return list;
    }

    @Override
    public void close() throws IOException {

        if (this.workbook != null) this.workbook.close();
    }

    private InputStream getInputStreamFromFluxDataBuffer(Flux<DataBuffer> data) throws IOException {

        PipedOutputStream osPipe = new PipedOutputStream(); // NOSONAR
        // Cannot be used in try-with-resource as this has to be part of Reactor and
        // don't know when this can be closed.
        // Since doOnComplete is used we are closing the resource after writing the
        // data.
        PipedInputStream isPipe = new PipedInputStream(osPipe);

        DataBufferUtils.write(data, osPipe)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnComplete(() -> {
                    try {
                        osPipe.close();
                    } catch (IOException ignored) {
                        logger.debug("Issues with accessing buffer.", ignored);
                    }
                })
                .subscribe(DataBufferUtils.releaseConsumer());
        return isPipe;
    }
}
