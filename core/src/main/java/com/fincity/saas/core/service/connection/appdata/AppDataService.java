package com.fincity.saas.core.service.connection.appdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.SchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.flattener.JsonUnflattener;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.FlatFileType;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.StorageService;
import com.google.gson.JsonObject;
import com.monitorjbl.xlsx.StreamingReader;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;
import com.opencsv.RFC4180ParserBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AppDataService {

	private static final ConnectionSubType DEFAULT_APP_DATA_SERVICE = ConnectionSubType.MONGO;

	@Autowired
	private ConnectionService connectionService;

	@Autowired
	private StorageService storageService;

	@Autowired
	private MongoAppDataService mongoAppDataService;

	@Autowired
	private CoreSchemaService schemaService;

	@Autowired
	private CoreMessageResourceService msgService;

	private EnumMap<ConnectionSubType, IAppDataService> services = new EnumMap<>(ConnectionSubType.class);

	private static final String FLATTEN_KEY = "flattened ";

	private static final String NUMBER_REGEX = "(\\d)+$|((\\d)+[.]\\d+)+$";

	private static final String TRUE_REGEX = "^([Tt][Rr][Uu][Ee])$";

	private static final String FALSE_REGEX = "^([Ff][Aa][Ll][Ss][Ee])$";

	@PostConstruct
	public void init() {

		this.services.putAll(Map.of(ConnectionSubType.MONGO, (IAppDataService) mongoAppDataService));
	}

	public Mono<Map<String, Object>> create(String appCode, String clientCode, String storageName,
	        DataObject dataObject) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.create(conn, storage, dataObject), Storage::getCreateAuth,
		                CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));
	}

	public Mono<Map<String, Object>> update(String appCode, String clientCode, String storageName,
	        DataObject dataObject, Boolean override) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.update(conn, storage, dataObject, override),
		                Storage::getUpdateAuth, CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE));
	}

	public Mono<Map<String, Object>> read(String appCode, String clientCode, String storageName, String id) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.read(conn, storage, id), Storage::getReadAuth,
		                CoreMessageResourceService.FORBIDDEN_READ_STORAGE));
	}

	public Mono<Page<Map<String, Object>>> readPage(String appCode, String clientCode, String storageName,
	        Pageable page, Boolean count, AbstractCondition condition) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.readPage(conn, storage, page, count, condition),
		                Storage::getReadAuth, CoreMessageResourceService.FORBIDDEN_READ_STORAGE));
	}

	public Mono<Boolean> delete(String appCode, String clientCode, String storageName, String id) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.delete(conn, storage, id), Storage::getDeleteAuth,
		                CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));
	}

	public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName, FlatFileType fileType,
	        ServerHttpRequest request, ServerHttpResponse response) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this
		                .genericOperation(storage, (ca, hasAccess) -> downloadTemplate(storage, fileType),
		                        Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)
		                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
		                        CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR))));
	}

	public Mono<Boolean> uploadTemplate(String appCode, String clientCode, String storageName, FlatFileType fileType,
	        FilePart file) {

		return FlatMapUtil.flatMapMonoWithNull(
		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.uploadTemplate(storage, fileType, file),

		        (conn, dataService, storage, jsonObjectList) ->
				{
			        if (jsonObjectList.isEmpty())
				        return Mono.just(false);

			        return Flux.fromIterable(jsonObjectList)
			                .flatMap(job -> dataService.bulkCreate(conn, storage, job))
			                .collectList()
			                .map(e -> e.size())
			                .map(e -> true)
			                .defaultIfEmpty(false);

		        }

		);

	}

	// upload template method schema implement here .

	private Mono<List<JsonObject>> uploadTemplate(Storage storage, FlatFileType fileType, FilePart mpFile) {
		return uploadTemplateWithoutAuth(storage, fileType, mpFile);
	}

	private <T> Mono<T> genericOperation(Storage storage, BiFunction<ContextAuthentication, Boolean, Mono<T>> bifun,
	        Function<Storage, String> authFun, String msgString) {

		if (storage == null)
			return msgService.throwMessage(HttpStatus.NOT_FOUND, CoreMessageResourceService.STORAGE_NOT_FOUND);

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.justOrEmpty(SecurityContextUtil.hasAuthority(authFun.apply(storage), ca.getUser()
		                .getAuthorities()) ? true : null),

		        bifun)

		        .switchIfEmpty(Mono
		                .defer(() -> this.msgService.throwMessage(HttpStatus.FORBIDDEN, msgString, storage.getName())));
	}

	private Mono<byte[]> downloadTemplate(Storage storage, FlatFileType type) {

		try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();) {

			return FlatMapUtil.flatMapMono(() -> storageService.getSchema(storage),

			        storageSchema -> Mono.fromCallable(() -> this.getHeaders(null, storage, storageSchema))
			                .subscribeOn(Schedulers.boundedElastic()),

			        (storageSchema, acutalHeaders) ->
					{

				        List<String> headers = acutalHeaders;

				        if (type == FlatFileType.XLSX) {
					        try (XSSFWorkbook excelWorkbook = new XSSFWorkbook();) {

						        XSSFSheet sheet = excelWorkbook.createSheet(storage.getName());
						        Row headRow = sheet.createRow(0); // writing for header
						        int headColumn = 0;
						        for (String header : headers) {
							        Cell cell = headRow.createCell(headColumn++);
							        cell.setCellValue(header);
						        }
						        excelWorkbook.write(byteStream);
						        return Mono.just(byteStream.toByteArray());

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }

				        } else if (type == FlatFileType.CSV) {

					        try (OutputStreamWriter outputStream = new OutputStreamWriter(byteStream);
					                CSVWriter csvWorkBook = new CSVWriter(outputStream);) {

						        csvWorkBook.writeNext(headers.toArray(new String[0]));
						        outputStream.flush();
						        return Mono.just(byteStream.toByteArray());

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }

				        } else if (type == FlatFileType.TSV) {

					        try (OutputStreamWriter outputStream = new OutputStreamWriter(byteStream);
					                CSVWriter tsvWorkBook = new CSVWriter(outputStream, '\t',
					                        ICSVWriter.DEFAULT_QUOTE_CHARACTER, ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
					                        ICSVWriter.DEFAULT_LINE_END);) {

						        tsvWorkBook.writeNext(headers.toArray(new String[0]));
						        outputStream.flush();
						        return Mono.just(byteStream.toByteArray());

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }
				        }
				        return Mono.empty();
			        });

		} catch (Exception e) {
			return Mono.empty();
		}

	}

	// need to go through the nesting layers of schema. Hence it requires recursive
	// method to obtain headers
	private List<String> getHeaders(String prefix, Storage storage, Schema schema) { // NOSONAR

		if (schema.getRef() != null) {

			schema = SchemaUtil.getSchemaFromRef(schema,
			        this.schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
			        schema.getRef());
		}

		if (schema.getType()
		        .contains(SchemaType.OBJECT)) {

			return schema.getProperties()
			        .entrySet()
			        .stream()
			        .flatMap(e -> this
			                .getHeaders(prefix == null ? e.getKey() : prefix + "." + e.getKey(), storage, e.getValue())
			                .stream())
			        .toList();
		} else if (schema.getType()
		        .contains(SchemaType.ARRAY)) {

			ArraySchemaType aType = schema.getItems();

			if (aType.getSingleSchema() != null) {

				return IntStream.range(0, 2)
				        .mapToObj(e -> prefix == null ? "[" + e + "]" : prefix + "[" + e + "]")
				        .flatMap(e -> this.getHeaders(e, storage, aType.getSingleSchema())
				                .stream())
				        .toList();
			} else {

				List<String> list = new ArrayList<>();

				for (int i = 0; i < aType.getTupleSchema()
				        .size(); i++) {

					String pre = prefix == null ? "[" + i + "]" : prefix + "[" + i + "]";

					list.addAll(this.getHeaders(pre, storage, aType.getTupleSchema()
					        .get(i)));
				}

				return list;
			}
		}

		return List.of(prefix);
	}

	public Mono<List<JsonObject>> uploadTemplateWithoutAuth(Storage storage, FlatFileType fileType, FilePart filePart) {

		return FlatMapUtil.flatMapMonoLog(

		        () -> storageService.getSchema(storage),

		        storageSchema ->
				{

			        if (fileType.equals(FlatFileType.XLSX)) {

				        return this.readExcel(filePart)
				                .subscribeOn(Schedulers.boundedElastic());
			        } else if (fileType.equals(FlatFileType.CSV)) {

				        return this.readCsv(filePart)
				                .subscribeOn(Schedulers.boundedElastic());

			        } else if (fileType.equals(FlatFileType.TSV)) {

				        return this.readTsv(filePart)
				                .subscribeOn(Schedulers.boundedElastic());
			        }
			        return Mono.empty();

		        }

		);

	}

	private InputStream getInputStreamFromFluxDataBuffer(Flux<DataBuffer> data) throws IOException {
		PipedOutputStream osPipe = new PipedOutputStream();
		PipedInputStream isPipe = new PipedInputStream(osPipe);

		DataBufferUtils.write(data, osPipe)
		        .subscribeOn(Schedulers.boundedElastic())
		        .doOnComplete(() ->
				{
			        try {
				        osPipe.close();
			        } catch (IOException ignored) {
				        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
				                "Error while reading data from file");
			        }
		        })
		        .subscribe(DataBufferUtils.releaseConsumer());

		return isPipe;
	}

	private Mono<List<JsonObject>> readExcel(FilePart filePart) {

		try (Workbook excelWorkbook = StreamingReader.builder()
		        .rowCacheSize(100)
		        .bufferSize(4096)
		        .open(getInputStreamFromFluxDataBuffer(filePart.content()));) {

			List<Object> receivedHeaders = new ArrayList<>();

			List<List<Object>> excelRecords = new ArrayList<>();

			for (Sheet sheet : excelWorkbook) {
				for (Row r : sheet) {
					List<Object> excelRecord = new ArrayList<>();
					for (Cell c : r) {
						switch (c.getCellType()) {
						case BOOLEAN:
							excelRecord.add(c.getBooleanCellValue());
							break;

						case NUMERIC: {

							if (DateUtil.isCellDateFormatted(c)) {
								SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

								excelRecord.add(sdf.format(c.getDateCellValue()));
							} else {
								excelRecord.add(c.getNumericCellValue());
							}
							break;
						}

						case STRING:
							excelRecord.add(c.getStringCellValue());
							break;

						default:

							DataFormatter dataFormatter = new DataFormatter();
							excelRecord.add(dataFormatter.formatCellValue(c));

						}

					}
					excelRecords.add(excelRecord);
				}
			}

			receivedHeaders.addAll(excelRecords.get(0));

			Map<String, Map<String, Object>> excelMapRecords = new TreeMap<>();
			for (int i = 1; i < excelRecords.size() - 1; i++) {
				Map<String, Object> rowMap = new HashMap<>();
				List<Object> excelRecord = excelRecords.get(i);

				for (int j = 0; j < excelRecord.size() - 1; j++) {
					rowMap.put((String) receivedHeaders.get(j), excelRecord.get(j));
				}
				excelMapRecords.put(FLATTEN_KEY + i, rowMap);
			}

			List<JsonObject> unflattenedExcelList = excelMapRecords.keySet()
			        .stream()
			        .map(excelKey -> JsonUnflattener.unflatten(excelMapRecords.get(excelKey)))
			        .toList();
			return Mono.just(unflattenedExcelList);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Mono.empty();

	}

	private Mono<List<JsonObject>> readCsv(FilePart filePart) {

		try (

		        CSVReader csvWorkbook = new CSVReaderBuilder(new InputStreamReader(
		                getInputStreamFromFluxDataBuffer(filePart.content()), StandardCharsets.UTF_8))
		                .withCSVParser(new RFC4180ParserBuilder().withSeparator(',')
		                        .build())
		                .build();) {

			List<String[]> records = new ArrayList<>();
			String[] eachRecord;
			while ((eachRecord = csvWorkbook.readNext()) != null) {

				records.add(eachRecord);
			}

			if (records.size() > 1) {

				String[] receivedHeaders = records.get(0);
				Map<String, Map<String, Object>> mappedRecords = new TreeMap<>();

				for (int i = 1; i < records.size(); i++) {
					String[] rowValues = records.get(i);
					Map<String, Object> mappedRecord = new TreeMap<>();
					for (int j = 0; j < rowValues.length; j++) {
						String columnValue = rowValues[j];
						if (!StringUtil.safeIsBlank(columnValue)) {
							if (columnValue.matches(NUMBER_REGEX))
								mappedRecord.put(receivedHeaders[j], Double.parseDouble(columnValue));
							else if (columnValue.matches(TRUE_REGEX))
								mappedRecord.put(receivedHeaders[j], true);
							else if (columnValue.matches(FALSE_REGEX))
								mappedRecord.put(receivedHeaders[j], false);
							else
								mappedRecord.put(receivedHeaders[j], columnValue);

						} else
							mappedRecord.put(receivedHeaders[j], "");
					}
					mappedRecords.put(FLATTEN_KEY + i, mappedRecord);
				}

				List<JsonObject> unflattenedCSVList = mappedRecords.keySet()
				        .stream()
				        .map(csvKey -> JsonUnflattener.unflatten(mappedRecords.get(csvKey)))
				        .toList();

				return Mono.just(unflattenedCSVList);

			}

		} catch (Exception e) {

			e.printStackTrace();

		}
		return Mono.empty();

	}

	private Mono<List<JsonObject>> readTsv(FilePart filePart) {

		try (

		        CSVReader csvWorkbook = new CSVReaderBuilder(new InputStreamReader(
		                getInputStreamFromFluxDataBuffer(filePart.content()), StandardCharsets.UTF_8))
		                .withCSVParser(new RFC4180ParserBuilder().withSeparator('\t')
		                        .build())
		                .build();) {

			List<String[]> records = new ArrayList<>();
			String[] eachRecord;
			while ((eachRecord = csvWorkbook.readNext()) != null) {

				records.add(eachRecord);
			}

			if (records.size() > 1) {

				String[] receivedHeaders = records.get(0);
				Map<String, Map<String, Object>> mappedRecords = new TreeMap<>();

				for (int i = 1; i < records.size(); i++) {
					String[] rowValues = records.get(i);
					Map<String, Object> mappedRecord = new TreeMap<>();
					for (int j = 0; j < rowValues.length; j++) {
						String columnValue = rowValues[j];
						if (!StringUtil.safeIsBlank(columnValue)) {
							if (columnValue.matches(NUMBER_REGEX))
								mappedRecord.put(receivedHeaders[j], Double.parseDouble(columnValue));
							else if (columnValue.matches(TRUE_REGEX))
								mappedRecord.put(receivedHeaders[j], true);
							else if (columnValue.matches(FALSE_REGEX))
								mappedRecord.put(receivedHeaders[j], false);
							else
								mappedRecord.put(receivedHeaders[j], columnValue);

						} else
							mappedRecord.put(receivedHeaders[j], "");
					}
					mappedRecords.put(FLATTEN_KEY + i, mappedRecord);
				}

				List<JsonObject> unflattenedCSVList = mappedRecords.keySet()
				        .stream()
				        .map(csvKey -> JsonUnflattener.unflatten(mappedRecords.get(csvKey)))
				        .toList();

				return Mono.just(unflattenedCSVList);

			}

		} catch (Exception e) {

			e.printStackTrace();

		}

		return Mono.empty();

	}

}
