package com.fincity.saas.commons.file;

import com.fincity.saas.commons.util.DataFileType;
import com.google.gson.Gson;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DataFileWriter implements AutoCloseable, Flushable {

    protected List<String> header;
    protected OutputStream os;
    protected boolean headerWritten = false;
    protected boolean writeHeader = true;
    private ZipOutputStream zos;
    private List<List<String>> preHeaders;
    private DataFileType fileType;
    private boolean firstLine = true;
    private Gson gson;

    public DataFileWriter(List<String> header, DataFileType fileType, OutputStream stream, boolean writeHeader) {
        this(null, header, fileType, stream, writeHeader);
    }

    public DataFileWriter(
            List<List<String>> preHeaders,
            List<String> header,
            DataFileType fileType,
            OutputStream stream,
            boolean writeHeader) {
        this.header = header;
        this.preHeaders = preHeaders;
        this.os = stream;
        this.writeHeader = writeHeader;
        this.fileType = fileType;
        if (this.fileType == DataFileType.JSON || this.fileType == DataFileType.JSONL) this.gson = new Gson();
    }

    public DataFileWriter(List<String> header, DataFileType fileType, OutputStream stream) {
        this(header, fileType, stream, true);
    }

    public void write(Map<String, Object> map) throws IOException {

        if (this.zos == null) {
            this.createWriter();
        }

        if (!headerWritten) {

            writeStartingOfFile();

            if (this.writeHeader && !this.fileType.isNestedStructure()) {
                if (this.preHeaders != null) for (List<String> line : this.preHeaders) this.writeLine(line);
                this.writeLine(this.header);
            }
            headerWritten = true;
        }

        if (!this.fileType.isNestedStructure()) this.writeLineForFlatFile(map);
        else this.writelineForNestedFile(map);

        this.firstLine = false;
    }

    private void writelineForNestedFile(Map<String, Object> map) throws IOException {
        if (this.fileType != DataFileType.JSON && this.fileType != DataFileType.JSONL) return;

        if (!this.firstLine) this.os.write((this.fileType == DataFileType.JSON ? ",\n" : "\n").getBytes());

        this.os.write(this.gson.toJson(map).getBytes());
    }

    private void createWriter() {

        if (this.fileType == DataFileType.XLSX && this.zos == null) {
            this.zos = new ZipOutputStream(os);
        }
    }

    private void writeStartingOfFile() throws IOException {

        if (this.fileType == DataFileType.XLS) {
            this.os.write(
                    ("""
<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:o="urn:schemas-microsoft-com:office:office"
xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" xmlns:html="http://www.w3.org/TR/REC-html40">
<Worksheet ss:Name="Sheet1">
<Table>""")
                            .getBytes());
            this.os.write(IntStream.range(1, this.header.size() + 1)
                    .mapToObj(e -> "<Column ss:Index=\"" + e + "\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>")
                    .collect(Collectors.joining())
                    .getBytes());
            this.os.flush();
        } else if (this.fileType == DataFileType.XLSX) {
            this.xlsxWritingStart();
        } else if (this.fileType == DataFileType.JSON) {
            this.os.write("[".getBytes());
        }
    }

    private void xlsxWritingStart() throws IOException {

        this.zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/><Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/></Types>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("_rels/.rels"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("docProps/app.xml"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>Prime Unilog Platform</Application><AppVersion>1.0</AppVersion></Properties>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("docProps/core.xml"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dcterms:created xsi:type=\"dcterms:W3CDTF\">2018-08-25T07:28:42.72846Z</dcterms:created><dc:creator>Prime Unilog Platform</dc:creator></cp:coreProperties>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Target=\"sharedStrings.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\"/><Relationship Id=\"rId2\" Target=\"styles.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\"/><Relationship Id=\"rId3\" Target=\"worksheets/sheet1.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"/></Relationships>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"0\" uniqueCount=\"0\"></sst>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("xl/styles.xml"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\" mc:Ignorable=\"x14ac x16r2 xr\" xmlns:x14ac=\"http://schemas.microsoft.com/office/spreadsheetml/2009/9/ac\" xmlns:x16r2=\"http://schemas.microsoft.com/office/spreadsheetml/2015/02/main\" xmlns:xr=\"http://schemas.microsoft.com/office/spreadsheetml/2014/revision\"><fonts count=\"1\" x14ac:knownFonts=\"1\"><font><sz val=\"11\"/><color rgb=\"FF000000\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"1\"><xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles><dxfs count=\"0\"/><tableStyles count=\"0\" defaultTableStyle=\"TableStyleMedium2\" defaultPivotStyle=\"PivotStyleLight16\"/><extLst><ext uri=\"{EB79DEF2-80B8-43e5-95BD-54CBDDF9020C}\" xmlns:x14=\"http://schemas.microsoft.com/office/spreadsheetml/2009/9/main\"><x14:slicerStyles defaultSlicerStyle=\"SlicerStyleLight1\"/></ext><ext uri=\"{9260A510-F301-46a8-8635-F512D64BE5F5}\" xmlns:x15=\"http://schemas.microsoft.com/office/spreadsheetml/2010/11/main\"><x15:timelineStyles defaultTimelineStyle=\"TimeSlicerStyleLight1\"/></ext></extLst></styleSheet>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("xl/workbook.xml"));
        this.zos.write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><workbookPr date1904=\"false\"/><bookViews><workbookView activeTab=\"0\"/></bookViews><sheets><sheet name=\"Sheet1\" r:id=\"rId3\" sheetId=\"1\"/></sheets></workbook>"
                        .getBytes());

        this.zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
        this.zos.write(
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension ref=\"A1\"/><sheetViews><sheetView workbookViewId=\"0\"/></sheetViews><sheetFormatPr defaultRowHeight=\"15.0\"/><sheetData>")
                        .getBytes());
    }

    protected void writeLine(List<String> line) throws IOException {

        if (this.fileType == DataFileType.CSV)
            this.os.write(line.stream()
                    .map(e -> this.singleColumnConvert(e, ','))
                    .collect(Collectors.joining(",", "", "\n"))
                    .getBytes());
        else if (this.fileType == DataFileType.TSV)
            this.os.write(line.stream()
                    .map(e -> this.singleColumnConvert(e, '\t'))
                    .collect(Collectors.joining("\t", "", "\n"))
                    .getBytes());
        else if (this.fileType == DataFileType.XLS)
            this.os.write(line.stream()
                    .collect(Collectors.joining(
                            "</Data></Cell><Cell><Data ss:Type=\"String\">",
                            "<Row><Cell><Data ss:Type=\"String\">",
                            "</Data></Cell></Row>"))
                    .getBytes());
        else if (this.fileType == DataFileType.XLSX)
            this.zos.write(line.stream()
                    .collect(Collectors.joining(
                            "</t></is></c><c t=\"inlineStr\"><is><t>",
                            "<row><c t=\"inlineStr\"><is><t>",
                            "</t></is></c></row>"))
                    .getBytes());
    }

    public String singleColumnConvert(String e, char delim) {

        boolean hasDoubleQuotes = e.indexOf('"') != -1;
        String str = e;
        if (hasDoubleQuotes) str = e.replace("\"", "\"\"");
        boolean surround =
                hasDoubleQuotes || (e.indexOf(delim) != -1) || (e.indexOf('\n') != -1) || (e.indexOf('\r') != -1);
        if (!surround) return str;
        return '"' + str + '"';
    }

    protected void writeLineForFlatFile(Map<String, Object> map) throws IOException {

        this.writeLine(this.header.stream()
                .map(e -> com.fincity.saas.commons.util.StringUtil.safeValueOf(map.get(e), ""))
                .toList());
    }

    @Override
    public void flush() throws IOException {

        if (zos != null) this.zos.flush();
        else this.os.flush();
    }

    @Override
    public void close() throws IOException {

        if (this.fileType == DataFileType.XLS) this.os.write("</Table>\n</Worksheet>\n</Workbook>".getBytes());
        else if (this.fileType == DataFileType.XLSX)
            this.zos.write(
                    "</sheetData><pageMargins bottom=\"0.75\" footer=\"0.3\" header=\"0.3\" left=\"0.7\" right=\"0.7\" top=\"0.75\"/></worksheet>"
                            .getBytes());
        else if (this.fileType == DataFileType.JSON) this.os.write("]".getBytes());

        if (this.fileType == DataFileType.XLSX) {
            this.zos.flush();
            this.zos.close();
        } else {
            this.os.flush();
            this.os.close();
        }
    }
}
