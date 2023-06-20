package com.fincity.saas.core.service.connection.appdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.PostConstruct;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
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
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.flattener.JsonUnflattener;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.FlatFileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.StorageService;
import com.google.gson.Gson;
import com.ibm.icu.text.SimpleDateFormat;
import com.monitorjbl.xlsx.StreamingReader;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;
import com.opencsv.RFC4180ParserBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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

	@PostConstruct
	public void init() {

		this.services.putAll(Map.of(ConnectionSubType.MONGO, (IAppDataService) mongoAppDataService));
	}

	public Mono<Map<String, Object>> create(String appCode, String clientCode, String storageName,
	        DataObject dataObject) {

		Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

		        (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

		        (ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

		        (ca, ac, cc, conn) -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc),

		        (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
		                (cona, hasAccess) -> dataService.create(conn, storage, dataObject), Storage::getCreateAuth,
		                CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.create"));
	}

	public Mono<Map<String, Object>> update(String appCode, String clientCode, String storageName,
	        DataObject dataObject, Boolean override) {

		Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

		        (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

		        (ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

		        (ca, ac, cc, conn) -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc),

		        (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
		                (cona, hasAccess) -> dataService.update(conn, storage, dataObject, override),
		                Storage::getUpdateAuth, CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.update"));
	}

	public Mono<Map<String, Object>> read(String appCode, String clientCode, String storageName, String id) {

		Mono<Map<String, Object>> mono = FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

		        (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

		        (ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

		        (ca, ac, cc, conn) -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc),

		        (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
		                (cona, hasAccess) -> dataService.read(conn, storage, id), Storage::getReadAuth,
		                CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.read"));
	}

	public Mono<Page<Map<String, Object>>> readPage(String appCode, String clientCode, String storageName,
	        Pageable page, Boolean count, AbstractCondition condition) {
		Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

		        (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

		        (ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

		        (ca, ac, cc, conn) -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc),

		        (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
		                (cona, hasAccess) -> dataService.readPage(conn, storage, page, count, condition),
		                Storage::getReadAuth, CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.readPage"));
	}

	public Mono<Boolean> delete(String appCode, String clientCode, String storageName, String id) {
		Mono<Boolean> mono = FlatMapUtil.flatMapMonoWithNull(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

		        (ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

		        (ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

		        (ca, ac, cc, conn) -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc),

		        (ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
		                (cona, hasAccess) -> dataService.delete(conn, storage, id), Storage::getDeleteAuth,
		                CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));

		return mono.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.delete"));
	}

	public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName, FlatFileType fileType) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this
		                .genericOperation(storage, (ca, hasAccess) -> downloadTemplate(storage, fileType),
		                        Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)
		                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
		                        CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR))))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));
	}

	public Mono<Boolean> uploadTemplate(String appCode, String clientCode, String storageName, FlatFileType fileType,
	        FilePart file) {

		return FlatMapUtil
		        .flatMapMonoWithNull(() -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		                conn -> Mono.just(this.services
		                        .get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		                (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		                (conn, dataService, storage) -> this
		                        .genericOperation(storage,
		                                (ca, hasAccess) -> uploadTemplate(storage, fileType, file, dataService),
		                                Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)
		                        .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
		                                CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR))))
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadTemplate"));

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
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.genericOperation"))
		        .switchIfEmpty(Mono
		                .defer(() -> this.msgService.throwMessage(HttpStatus.FORBIDDEN, msgString, storage.getName())));
	}

	private Mono<byte[]> downloadTemplate(Storage storage, FlatFileType type) { // NOSONAR

		try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();) {

			return FlatMapUtil.flatMapMono(() -> storageService.getSchema(storage),

			        storageSchema -> this.getHeaders(null, storage, storageSchema),

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

						        return csvFileWriter(byteStream, headers, outputStream, csvWorkBook);

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }

				        } else if (type == FlatFileType.TSV) {

					        try (OutputStreamWriter outputStream = new OutputStreamWriter(byteStream);
					                CSVWriter tsvWorkBook = new CSVWriter(outputStream, '\t',
					                        ICSVWriter.DEFAULT_QUOTE_CHARACTER, ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
					                        ICSVWriter.DEFAULT_LINE_END);) {

						        return csvFileWriter(byteStream, headers, outputStream, tsvWorkBook);

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }
				        }
				        return Mono.empty();
			        })
			        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));

		} catch (Exception e) {
			return Mono.empty();
		}

	}

	private Mono<byte[]> csvFileWriter(ByteArrayOutputStream byteStream, List<String> headers,
	        OutputStreamWriter outputStream, CSVWriter tsvWorkBook) throws IOException {
		tsvWorkBook.writeNext(headers.toArray(new String[0]));
		outputStream.flush();
		return Mono.just(byteStream.toByteArray());
	}

	private Mono<List<String>> getHeaders(String prefix, Storage storage, Schema sch) {

		return FlatMapUtil.flatMapMono(

		        () ->
				{
			        if (sch.getRef() == null)
				        return Mono.just(sch);
			        return ReactiveSchemaUtil.getSchemaFromRef(sch,
			                new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(),
			                        new CoreSchemaRepository(), this.schemaService
			                                .getSchemaRepository(storage.getAppCode(), storage.getClientCode())),
			                sch.getRef());
		        },

		        schema ->
				{
			        if (schema.getType()
			                .contains(SchemaType.OBJECT)) {

				        return Flux.fromIterable(schema.getProperties()
				                .entrySet())
				                .flatMap(e -> this.getHeaders(getFlattenedObjectName(prefix, e), storage, e.getValue())
				                        .flatMapMany(Flux::fromIterable))
				                .collectList();

			        } else if (schema.getType()
			                .contains(SchemaType.ARRAY)) {

				        ArraySchemaType aType = schema.getItems();

				        if (aType.getSingleSchema() != null) {

					        return Flux.range(0, 2)
					                .map(e -> getPrefixArrayName(prefix, e))
					                .flatMap(e -> this.getHeaders(e, storage, aType.getSingleSchema())
					                        .flatMapMany(Flux::fromIterable))
					                .collectList();
				        } else {

					        return Flux.<Tuple2<Integer, Schema>>create(sink -> {
						        for (int i = 0; i < aType.getTupleSchema()
						                .size(); i++)
							        sink.next(Tuples.of(Integer.valueOf(i), aType.getTupleSchema()
							                .get(i)));

						        sink.complete();
					        })
					                .flatMap(tup -> this
					                        .getHeaders(getPrefixArrayName(prefix, tup.getT1()), storage, tup.getT2())
					                        .flatMapMany(Flux::fromIterable))
					                .collectList();

				        }
			        }

			        return Mono.just(List.of(prefix));
		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.getHeaders"));

	}

	private Mono<Map<String, Set<SchemaType>>> getHeadersSchemaType(String prefix, Storage storage, Schema sch) { // NOSONAR

		return FlatMapUtil.flatMapFlux(

		        () ->
				{
			        if (sch.getRef() == null)
				        return Flux.just(sch);

			        return Flux.from(ReactiveSchemaUtil.getSchemaFromRef(sch,
			                new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(),
			                        new CoreSchemaRepository(), this.schemaService
			                                .getSchemaRepository(storage.getAppCode(), storage.getClientCode())),
			                sch.getRef()));
		        },

		        schema ->
				{
			        Flux<Tuple2<String, Set<SchemaType>>> initial = prefix == null ? Flux.empty()
			                : Flux.just(Tuples.of(prefix, schema.getType()
			                        .getAllowedSchemaTypes()));

			        Flux<Tuple2<String, Set<SchemaType>>> objectKeys;

			        if (schema.getType()
			                .contains(SchemaType.OBJECT)) {

				        objectKeys = Flux.fromIterable(schema.getProperties()
				                .entrySet())
				                .flatMap(e -> this
				                        .getHeadersSchemaType(getFlattenedObjectName(prefix, e), storage, e.getValue())
				                        .map(Map::entrySet)
				                        .flatMapMany(Flux::fromIterable))
				                .map(e -> Tuples.of(e.getKey(), e.getValue()));

			        } else if (schema.getType()
			                .contains(SchemaType.ARRAY)) {

				        ArraySchemaType aType = schema.getItems();

				        if (aType.getSingleSchema() != null) {

					        objectKeys = Flux.range(0, 2)
					                .map(e -> getPrefixArrayName(prefix, e))
					                .flatMap(e -> this.getHeadersSchemaType(e, storage, aType.getSingleSchema())
					                        .map(Map::entrySet)
					                        .flatMapMany(Flux::fromIterable))
					                .map(e -> Tuples.of(e.getKey(), e.getValue()));

				        } else {
					        objectKeys = Flux.<Tuple2<Integer, Schema>>create(sink -> {
						        for (int i = 0; i < aType.getTupleSchema()
						                .size(); i++)
							        sink.next(Tuples.of(Integer.valueOf(i), aType.getTupleSchema()
							                .get(i)));

						        sink.complete();
					        })
					                .flatMap(tup -> this
					                        .getHeadersSchemaType(getPrefixArrayName(prefix, tup.getT1()), storage,
					                                tup.getT2())
					                        .map(Map::entrySet)
					                        .flatMapMany(Flux::fromIterable))
					                .map(e -> Tuples.of(e.getKey(), e.getValue()));

				        }
			        } else {

				        objectKeys = Flux.empty();
			        }

			        return Flux.merge(initial, objectKeys);
		        }

		)
		        .collectMap(Tuple2::getT1, Tuple2::getT2);

	}

	private String getPrefixArrayName(String prefix, int e) {
		return prefix == null ? "[" + e + "]" : prefix + "[" + e + "]";
	}

	private String getFlattenedObjectName(String prefix, Entry<String, Schema> e) {
		return prefix == null ? e.getKey() : prefix + "." + e.getKey();
	}

	private Mono<Boolean> uploadTemplate(Storage storage, FlatFileType fileType, FilePart filePart,
	        IAppDataService dataService) {

		return FlatMapUtil.flatMapMono(

		        () -> storageService.getSchema(storage),

		        storageSchema -> this.getHeadersSchemaType(null, storage, storageSchema),

		        (storageSchema, flattenedSchemaType) ->
				{
			        if (fileType.equals(FlatFileType.XLSX)) {

				        return this.readExcel(filePart, flattenedSchemaType, fileType.toString())
				                .subscribeOn(Schedulers.boundedElastic());
			        } else if (fileType.equals(FlatFileType.CSV)) {

				        return this.readCsvOrTsv(filePart, flattenedSchemaType, fileType.toString())
				                .subscribeOn(Schedulers.boundedElastic());

			        } else if (fileType.equals(FlatFileType.TSV)) {

				        return this.readCsvOrTsv(filePart, flattenedSchemaType, fileType.toString())
				                .subscribeOn(Schedulers.boundedElastic());
			        }
			        return Mono.empty();

		        },

		        (storageSchema, headers, dataObjectList) ->
				{
			        if (dataObjectList.isEmpty())
				        return Mono.just(false);

			        return Flux.fromIterable(dataObjectList)
			                .flatMap(dataObject -> dataService.create(null, storage, dataObject)
			                        .onErrorResume(err -> Mono.empty()))
			                .collectList()
			                .map(e -> !e.isEmpty())
			                .defaultIfEmpty(false);
		        })
		        .contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadTemplate"))
		        .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.BAD_REQUEST,
		                CoreMessageResourceService.NOT_ABLE_TO_READ_FILE_FORMAT, fileType)));
	}

	private InputStream getInputStreamFromFluxDataBuffer(Flux<DataBuffer> data) throws IOException {

		PipedOutputStream osPipe = new PipedOutputStream();// NOSONAR
		// Cannot be used in try-with-resource as this has to be part of Reactor and
		// don't know when this can be closed.
		// Since doOnComplete is used we are closing the resource after writing the
		// data.
		PipedInputStream isPipe = new PipedInputStream(osPipe);

		DataBufferUtils.write(data, osPipe)
		        .subscribeOn(Schedulers.boundedElastic())
		        .doOnComplete(() ->
				{
			        try {
				        osPipe.close();
			        } catch (IOException ignored) {
				        throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
				                CoreMessageResourceService.NOT_ABLE_TO_READ_FROM_FILE);
			        }
		        })
		        .subscribe(DataBufferUtils.releaseConsumer());
		return isPipe;

	}

	@SuppressWarnings("unchecked")
	private List<DataObject> convertToJsonObject(Map<String, Map<String, String>> unflattenRecords,
	        Map<String, Set<SchemaType>> flattenedSchemaType, String fileType) {

		Gson gson = new Gson();

		return unflattenRecords.keySet()
		        .stream()
		        .map(recordKey ->
				{
			        try {
				        return Optional
				                .of(JsonUnflattener.unflatten(unflattenRecords.get(recordKey), flattenedSchemaType));
			        } catch (Exception internalExpection) {
				        return Optional.empty();
			        }

		        })
		        .filter(Optional::isPresent)
		        .map(recordObtained ->
				{
			        Map<String, Object> obtainedMap = gson.fromJson(gson.toJsonTree(recordObtained.get()), Map.class);
			        DataObject dataObject = new DataObject();
			        dataObject.setMessage(msgService
			                .getDefaultLocaleMessage(CoreMessageResourceService.BULK_UPLOAD_MESSAGE, fileType));
			        dataObject.setData(obtainedMap);
			        return dataObject;
		        })
		        .toList();

	}

	private Mono<List<DataObject>> readExcel(FilePart filePart, Map<String, Set<SchemaType>> flattenedSchemaType,
	        String fileType) {

		try (Workbook excelWorkbook = StreamingReader.builder()
		        .rowCacheSize(100)
		        .bufferSize(4096)
		        .open(getInputStreamFromFluxDataBuffer(filePart.content()));) {

			List<String> receivedHeaders = new ArrayList<>();

			List<List<String>> excelRecords = new ArrayList<>();

			for (Sheet sheet : excelWorkbook) {
				for (Row r : sheet) {
					excelRecords.add(processEachRecord(r, new ArrayList<>()));
				}
			}

			receivedHeaders.addAll(excelRecords.get(0));
			Map<String, Map<String, String>> excelMapRecords = createExcelMapRecords(receivedHeaders, excelRecords,
			        new HashMap<>());

			return Mono.just(convertToJsonObject(excelMapRecords, flattenedSchemaType, fileType));
		} catch (Exception e) {
			return Mono.empty();
		}

	}

	private List<String> processEachRecord(Row row, List<String> excelRecord) {

		for (int c = 0; c < row.getLastCellNum(); c++) {

			Cell cell = row.getCell(c, MissingCellPolicy.RETURN_BLANK_AS_NULL);

			if (cell != null) {
				if (cell.getCellType()
				        .equals(CellType.NUMERIC) && DateUtil.isCellDateFormatted(cell)) {

					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
					excelRecord.add(sdf.format(cell.getDateCellValue()));
				} else
					excelRecord.add(cell.getStringCellValue());
			} else
				excelRecord.add("");
		}

		return excelRecord;
	}

	private Map<String, Map<String, String>> createExcelMapRecords(List<String> receivedHeaders,
	        List<List<String>> excelRecords, Map<String, Map<String, String>> excelMapRecords) {
		for (int i = 1; i < excelRecords.size(); i++) {
			Map<String, String> rowMap = new HashMap<>();
			List<String> excelRecord = excelRecords.get(i);

			for (int j = 0; j < excelRecord.size(); j++) {
				String value = excelRecord.get(j);
				if (!StringUtil.safeIsBlank(value))
					rowMap.put(receivedHeaders.get(j), value);
			}
			excelMapRecords.put(FLATTEN_KEY + i, rowMap);
		}
		return excelMapRecords;
	}

	private Mono<List<DataObject>> readCsvOrTsv(FilePart filePart, Map<String, Set<SchemaType>> flattenedSchemaType,
	        String fileType) {

		try (

		        CSVReader csvWorkbook = new CSVReaderBuilder(new InputStreamReader(
		                getInputStreamFromFluxDataBuffer(filePart.content()), StandardCharsets.UTF_8))
		                .withCSVParser(new RFC4180ParserBuilder().withSeparator(fileType.equals("CSV") ? ',' : '\t')
		                        .build())
		                .build();) {

			List<String[]> records = new ArrayList<>();
			String[] eachRecord;
			while ((eachRecord = csvWorkbook.readNext()) != null) {

				records.add(eachRecord);
			}

			if (records.size() > 1) {

				String[] receivedHeaders = records.get(0);
				Map<String, Map<String, String>> mappedRecords = new HashMap<>();

				for (int i = 1; i < records.size(); i++) {
					String[] rowValues = records.get(i);
					Map<String, String> mappedRecord = new HashMap<>();
					for (int j = 0; j < rowValues.length; j++) {

						String columnValue = rowValues[j];
						if (!StringUtil.safeIsBlank(columnValue) && columnValue.length() > 0)
							mappedRecord.put(receivedHeaders[j], columnValue);
						else
							mappedRecord.put(receivedHeaders[j], "");
					}
					mappedRecords.put(FLATTEN_KEY + i, mappedRecord);
				}

				return Mono.just(convertToJsonObject(mappedRecords, flattenedSchemaType, fileType));

			}
			return Mono.empty();

		} catch (Exception e) {

			return Mono.empty();

		}

	}

}
