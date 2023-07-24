package com.fincity.saas.core.service.connection.appdata;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.expression.ParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.util.primitive.PrimitiveUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.file.DataFileReader;
import com.fincity.saas.commons.file.DataFileWriter;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.DataFileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.MapUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.StorageService;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
	
	private static final Logger logger = LoggerFactory.getLogger(AppDataService.class);

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

			Query query) {

		Mono<Page<Map<String, Object>>> mono = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> connectionService.find(ac, cc, ConnectionType.APP_DATA),

				(ca, ac, cc, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, conn, dataService) -> storageService.read(storageName, ac, cc),

				(ca, ac, cc, conn, dataService, storage) -> this.genericOperation(storage,
						(cona, hasAccess) -> dataService.readPage(conn, storage, query), Storage::getReadAuth,
						CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

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

	public Mono<Boolean> downloadData(String appCode, String clientCode, String storageName, Query query,
			DataFileType fileType) {

		Mono<List<Map<String, Object>>> dataList = FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> storageService.read(storageName, ac, cc),

				(ca, ac, cc, storage) -> storageService.getSchema(storage)
						.map(Schema.class::cast),

				(ca, ac, cc, storage, storageSchema) -> connectionService.find(ac, cc,
						ConnectionType.APP_DATA),

				(ca, ac, cc, storage, storageSchema, conn) -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(ca, ac, cc, storage, storageSchema, conn, dataService) -> this.genericOperation(storage,
						(cona, hasAccess) -> dataService.readCompleteData(conn, storage, query), Storage::getReadAuth,
						CoreMessageResourceService.FORBIDDEN_READ_STORAGE));

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(appCode == null ? ca.getUrlAppCode() : appCode),

				(ca, ac) -> Mono.just(clientCode == null ? ca.getUrlClientCode() : clientCode),

				(ca, ac, cc) -> storageService.read(storageName, ac, cc),

				(ca, ac, cc, storage) -> dataList,

				(ca, ac, cc, storage, receivedData) -> this.writeDataIntoFile(storage, fileType, receivedData),

				(ca, ac, cc, storage, receivedData, output) -> Mono.just(true));

	}

	public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName, DataFileType fileType) {

		return FlatMapUtil.flatMapMonoWithNull(

				() -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

				conn -> Mono
						.just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

				(conn, dataService) -> storageService.read(storageName, appCode, clientCode),

				(conn, dataService, storage) -> this
						.genericOperation(storage, (ca, hasAccess) -> downloadTemplate(storage, fileType, "notghjin"),
								Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)

						.switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
								CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR))))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));
	}

	public Mono<Boolean> uploadData(String appCode, String clientCode, String storageName, DataFileType fileType,
			FilePart file) {

		return FlatMapUtil
				.flatMapMonoWithNull(() -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

						conn -> Mono.just(this.services
								.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

						(conn, dataService) -> storageService.read(storageName, appCode, clientCode),

						(conn, dataService, storage) -> this
								.genericOperation(storage,
										(ca, hasAccess) -> uploadDataInternal(conn, storage, fileType,
												file, dataService),
										Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)
								.switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
										CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR))))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadData"));

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

	private Mono<byte[]> writeDataIntoFile(Storage storage, DataFileType type, List<Map<String, Object>> receivedData) {
		return Mono.just(new byte[0]);
	}

	// private Mono<byte[]> writeDataIntoFile(Storage storage, DataFileType type,
	// List<Map<String, Object>> receivedData) {

	// try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();) {

	// return FlatMapUtil.flatMapMono(() -> storageService.getSchema(storage),

	// storageSchema -> storageSchema.getType() != null && storageSchema.getType()
	// .getAllowedSchemaTypes()
	// .size() == 1 && storageSchema.getType()
	// .getAllowedSchemaTypes()
	// .contains(SchemaType.OBJECT)
	// ? this.getHeadersSchemaType(null, storage, storageSchema, 0)
	// : Mono.empty(),

	// (storageSchema, acutalHeaders) -> {

	// List<String> headers = new ArrayList<>();

	// if (type == DataFileType.XLSX) {
	// try (XSSFWorkbook excelWorkbook = new XSSFWorkbook();) {

	// int rowCount = 0;
	// XSSFSheet sheet = excelWorkbook.createSheet(storage.getName());
	// Row headRow = sheet.createRow(rowCount++); // writing for header
	// int headColumn = 0;
	// for (String header : headers) {
	// Cell cell = headRow.createCell(headColumn++);
	// cell.setCellValue(header);
	// }

	// for (Map<String, Object> rowData : receivedData) {

	// Row interRow = sheet.createRow(rowCount++);
	// headColumn = 0;

	// // data will be json format and flatten it to use in other records

	// }

	// excelWorkbook.write(byteStream);
	// return Mono.just(byteStream.toByteArray());

	// } catch (Exception e) {
	// return Mono.defer(() ->
	// this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
	// CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
	// }

	// } else if (type == DataFileType.CSV) {

	// try (OutputStreamWriter outputStream = new OutputStreamWriter(byteStream);
	// CSVWriter csvWorkBook = new CSVWriter(outputStream);) {

	// return csvFileWriter(byteStream, headers, outputStream, csvWorkBook);

	// } catch (Exception e) {
	// return Mono.defer(() ->
	// this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
	// CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
	// }

	// } else if (type == DataFileType.TSV) {

	// try (OutputStreamWriter outputStream = new OutputStreamWriter(byteStream);
	// CSVWriter tsvWorkBook = new CSVWriter(outputStream, '\t',
	// ICSVWriter.DEFAULT_QUOTE_CHARACTER, ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
	// ICSVWriter.DEFAULT_LINE_END);) {

	// return csvFileWriter(byteStream, headers, outputStream, tsvWorkBook);

	// } catch (Exception e) {
	// return Mono.defer(() ->
	// this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
	// CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
	// }
	// }
	// return Mono.empty();
	// })
	// .contextWrite(Context.of(LogUtil.METHOD_NAME,
	// "AppDataService.downloadTemplate"));

	// } catch (Exception e) {
	// return Mono.empty();
	// }

	// }

	private Mono<byte[]> downloadTemplate(Storage storage, DataFileType type, String temp) { // NOSONAR

		if (type.isNestedStructure())
			return Mono.just(new byte[0]);

		return FlatMapUtil.flatMapMonoWithNull(() -> storageService.getSchema(storage),

				storageSchema -> {

					return (storageSchema.getRef() != null)
							|| (storageSchema.getType() != null && storageSchema.getType()
									.getAllowedSchemaTypes()
									.size() == 1 && storageSchema.getType()
											.getAllowedSchemaTypes()
											.contains(SchemaType.OBJECT))
													? this.getHeaders(null, storage, storageSchema)

													: Mono.empty();

				},

				(storageSchema, acutalHeaders) -> {
					try {
						ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
						DataFileWriter writer = new DataFileWriter(acutalHeaders, type, byteStream);
						writer.write(Map.of());
						writer.flush();
						writer.close();
						byteStream.flush();
						byteStream.close();
						byte[] bytes = byteStream.toByteArray();
						return Mono.just(bytes);
					} catch (Exception e) {
						return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
								CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					}

				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.downloadTemplate"));

	}

	private Mono<Map<String, Set<SchemaType>>> getHeadersSchemaType(String prefix, Storage storage, Schema schema,
			int level) {

		return FlatMapUtil.flatMapMono(

				() -> {

					if (schema.getRef() == null)
						return Mono.just(schema);

					return ReactiveSchemaUtil.getSchemaFromRef(schema,
							new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(),
									new CoreSchemaRepository(), this.schemaService
											.getSchemaRepository(storage.getAppCode(), storage.getClientCode())),
							schema.getRef());

				},

				rSchema -> {

					if (rSchema.getType()
							.contains(SchemaType.OBJECT)) {

						return getSchemaHeadersIfObject(prefix, storage, level, rSchema);

					} else if (rSchema.getType()
							.contains(SchemaType.ARRAY)) {

						return getSchemaHeadersIfArray(prefix, storage, level, rSchema);
					}

					return Mono.just(Map.of(prefix, rSchema.getType()
							.getAllowedSchemaTypes()));
				});
	}

	private Mono<Map<String, Set<SchemaType>>> getSchemaHeadersIfArray(String prefix, Storage storage, int level,
			Schema rSchema) {

		if (level > 2 || rSchema.getItems() == null)
			return Mono.just(Map.of());

		ArraySchemaType aType = rSchema.getItems();

		if (aType.getSingleSchema() != null) {

			return Flux.range(0, 2)
					.map(e -> getPrefixArrayName(prefix, e))
					.flatMap(e -> this.getHeadersSchemaType(e, storage, aType.getSingleSchema(), level + 1)
							.map(Map::entrySet)
							.flatMapMany(Flux::fromIterable))
					.collectMap(Map.Entry::getKey, Map.Entry::getValue);

		} else if (aType.getTupleSchema() != null) {

			return Flux.<Tuple2<Integer, Schema>>create(sink -> {
				for (int i = 0; i < aType.getTupleSchema()
						.size(); i++)
					sink.next(Tuples.of(Integer.valueOf(i), aType.getTupleSchema()
							.get(i)));

				sink.complete();
			})
					.flatMap(tup -> this
							.getHeadersSchemaType(getPrefixArrayName(prefix, tup.getT1()), storage, tup.getT2(),
									level + 1)
							.map(Map::entrySet)
							.flatMapMany(Flux::fromIterable))
					.collectMap(Map.Entry::getKey, Map.Entry::getValue);
		}

		return Mono.just(Map.of());
	}

	private Mono<Map<String, Set<SchemaType>>> getSchemaHeadersIfObject(String prefix, Storage storage, int level,
			Schema rSchema) {

		if (level >= 2 || rSchema.getProperties() == null)
			return Mono.just(Map.of());

		return Flux.fromIterable(rSchema.getProperties()
				.entrySet())
				.flatMap(e -> this
						.getHeadersSchemaType(getFlattenedObjectName(prefix, e), storage, e.getValue(), level + 1)
						.map(Map<String, Set<SchemaType>>::entrySet)
						.flatMapMany(Flux::fromIterable))
				.collectMap(Map.Entry::getKey, Map.Entry::getValue);
	}

	private Mono<List<String>> getHeaders(String prefix, Storage storage, Schema sch) { // NOSONAR

		return this.getHeadersSchemaType(prefix, storage, sch, 0)
				.flatMapMany(e -> Flux.fromIterable(e.keySet()))
				.sort((a, b) -> {
					int aCount = StringUtils.countMatches(a, '.');
					int bCount = StringUtils.countMatches(b, '.');
					if (aCount == bCount)
						return a.compareToIgnoreCase(b);

					return aCount - bCount;
				})
				.collectList();
	}

	private String getPrefixArrayName(String prefix, int e) {
		return prefix == null ? "[" + e + "]" : prefix + "[" + e + "]";
	}

	private String getFlattenedObjectName(String prefix, Entry<String, Schema> e) {
		return prefix == null ? e.getKey() : prefix + "." + e.getKey();
	}

	// add a check for storage schema is only object
	private Mono<Boolean> uploadDataInternal(Connection conn, Storage storage, DataFileType fileType, FilePart filePart,
			IAppDataService dataService) {

		return FlatMapUtil.flatMapMono(

				() -> storageService.getSchema(storage),

				storageSchema -> fileType.isNestedStructure() ? Mono.just(Map.of())
						: this.getHeadersSchemaType(null, storage, storageSchema, 0),

				(storageSchema, headers) -> {
					
					List<Mono<Boolean>> monoList = new ArrayList<>();

					if (fileType == DataFileType.JSON || fileType == DataFileType.JSONL) {

						Map<String, Object> job;
						try (DataFileReader reader = new DataFileReader(filePart, fileType)) {
							while ((job = reader.readObject()) != null)
								monoList.add(dataService.create(conn, storage, new DataObject().setData(job)).map(v -> true));
						} catch (Exception ex) {
							logger.debug("Error while reading upload file. ",ex);
						}
					} else {
						try (DataFileReader reader = new DataFileReader(filePart, fileType)) {
							List<String> row;

							do {
								row = reader.readRow();
								if (row != null && !row.isEmpty()) {
									Map<String, Object> rowMap = new HashMap<>();
									for (int i = 0; i < reader.getHeaders().size() && i < row.size(); i++) {
										if (StringUtil.safeIsBlank(row.get(i)))
											continue;
										MapUtil.setValueInMap(rowMap, reader.getHeaders().get(i),
												getElementBySchemaType(
														headers.get(reader.getHeaders().get(i)), row.get(i)));
									}
									monoList.add(dataService.create(conn, storage, new DataObject().setData(rowMap)).map(v -> true));
								}
							} while (row != null && !row.isEmpty());
						} catch (Exception ex) {
							logger.debug("Error while reading upload file. ",ex);
						}
					}

					return Flux.concat(monoList).collectList().map(e -> true);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AppDataService.uploadDataInternal"))
				.switchIfEmpty(Mono.defer(() -> msgService.throwMessage(HttpStatus.BAD_REQUEST,
						CoreMessageResourceService.NOT_ABLE_TO_READ_FILE_FORMAT, fileType)));
	}

	private static Object getElementBySchemaType(Set<SchemaType> schemaTypes, String value) {

		if (StringUtil.safeIsBlank(value) || "null".equalsIgnoreCase(value))
			return null;
		
		if (schemaTypes == null)
			return value;

		if (schemaTypes.contains(SchemaType.STRING))
			return value;

		if (schemaTypes.contains(SchemaType.BOOLEAN)) {

			try {
				return BooleanUtil.parse(value);
			} catch (ParseException pe) {
				return PrimitiveUtil.findPrimitiveNumberType(new JsonPrimitive(value)).getT2();
			}

		}

		return PrimitiveUtil.findPrimitiveNumberType(new JsonPrimitive(value)).getT2();
	}

}
