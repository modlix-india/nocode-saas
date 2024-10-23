package com.fincity.saas.files.dao;

import static com.fincity.saas.files.jooq.tables.FilesFileSystem.*;
import static com.fincity.saas.files.service.FileSystemService.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.FileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.jooq.enums.FilesFileSystemFileType;
import com.fincity.saas.files.jooq.enums.FilesFileSystemType;
import com.fincity.saas.files.jooq.tables.records.FilesFileSystemRecord;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.FilesPage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class FileSystemDao {

    private final DSLContext context;

    private static final Map<String, Field<?>> SORTABLE_FIELDS = Map.of(
            "TYPE", FILES_FILE_SYSTEM.FILE_TYPE,
            "NAME", FILES_FILE_SYSTEM.NAME,
            "SIZE", FILES_FILE_SYSTEM.SIZE,
            "LASTMODIFIED", FILES_FILE_SYSTEM.UPDATED_AT,
            "CREATED", FILES_FILE_SYSTEM.CREATED_AT);

    public FileSystemDao(DSLContext context) {
        this.context = context;
    }

    public Mono<Boolean> exists(FilesFileSystemType type, String clientCode, String path) {

        return getId(type, clientCode, path)
                .map(Optional::isPresent)
                .defaultIfEmpty(false);
    }

    private ULong checkIfPathExists(int index, String[] pathParts, ULong parentId,
            MultiValuedMap<String, FilesFileSystemRecord> nameIndex, Map<ULong, FilesFileSystemRecord> idIndex) {

        if (index == pathParts.length)
            return parentId;

        String pathPart = pathParts[index];

        Collection<FilesFileSystemRecord> records = nameIndex.get(pathPart);

        if (records == null || records.isEmpty())
            return null;

        for (FilesFileSystemRecord rec : records) {
            if ((parentId == null && rec.getParentId() == null)
                    || (parentId != null && parentId.equals(rec.getParentId())))
                return checkIfPathExists(index + 1, pathParts, rec.getId(), nameIndex, idIndex);
        }

        return null;
    }

    public Mono<Optional<ULong>> getId(FilesFileSystemType type, String clientCode, String path) {

        return this.getFileRecord(type, clientCode, path, FilesFileSystemRecord::getId).map(Optional::ofNullable)
                .defaultIfEmpty(Optional.empty());
    }

    public Mono<FileDetail> getFileDetail(FilesFileSystemType type, String clientCode, String path) {
        String[] pathParts = (path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path)
                .split(R2_FILE_SEPARATOR_STRING);

        if (pathParts.length == 0)
            return Mono.empty();

        return this.getFileRecord(type, clientCode, pathParts,
                r -> new FileDetail()
                        .setName(r.getName())
                        .setDirectory(r.getFileType() == FilesFileSystemFileType.DIRECTORY)
                        .setSize(r.getSize() == null ? 0l : r.getSize().longValue())
                        .setCreatedDate(r.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                        .setLastModifiedTime(r.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)));
    }

    private <T> Mono<T> getFileRecord(FilesFileSystemType type, String clientCode, String path,
            Function<FilesFileSystemRecord, T> mapper) {

        if (StringUtil.safeIsBlank(path))
            return Mono.empty();

        String[] pathParts = (path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path)
                .split(R2_FILE_SEPARATOR_STRING);

        return this.getFileRecord(type, clientCode, pathParts, mapper);
    }

    private <T> Mono<T> getFileRecord(FilesFileSystemType type, String clientCode, String[] pathParts,
            Function<FilesFileSystemRecord, T> mapper) {

        return Flux.from(this.context.selectFrom(FILES_FILE_SYSTEM)
                .where(DSL.and(FILES_FILE_SYSTEM.CODE.eq(clientCode), FILES_FILE_SYSTEM.NAME.in(pathParts),
                        FILES_FILE_SYSTEM.TYPE.eq(type))))
                .collectList()
                .map(list -> {
                    if (list.isEmpty() || pathParts.length > list.size())
                        return null;

                    MultiValuedMap<String, FilesFileSystemRecord> nameIndex = new ArrayListValuedHashMap<>();
                    Map<ULong, FilesFileSystemRecord> idIndex = new HashMap<>();

                    for (FilesFileSystemRecord rec : list) {
                        nameIndex.put(rec.getName(), rec);
                        idIndex.put(rec.getId(), rec);
                    }

                    return idIndex.get(checkIfPathExists(0, pathParts, null, nameIndex, idIndex));
                })
                .map(mapper);
    }

    public Mono<FilesPage> list(FilesFileSystemType type, String clientCode, String path, FileType[] fileType,
            String filter,
            Pageable page) {

        return FlatMapUtil.flatMapMono(

                () -> this.getId(type, clientCode, path),

                folderId -> this.list(type, clientCode, folderId.orElse(null), fileType, filter, page))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FileSystemDao.list (with path)"));
    }

    public Mono<FilesPage> list(FilesFileSystemType type, String clientCode, ULong folderId, FileType[] fileType,
            String filter,
            Pageable page) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(FILES_FILE_SYSTEM.CODE.eq(clientCode));
        conditions.add(FILES_FILE_SYSTEM.PARENT_ID.eq(folderId));
        conditions.add(FILES_FILE_SYSTEM.TYPE.eq(type));

        if (!StringUtil.safeIsBlank(filter))
            conditions.add(FILES_FILE_SYSTEM.NAME.like("%" + filter + "%"));

        if (fileType != null && fileType.length > 0)
            conditions.add(getConditionForFileTypes(fileType));

        return FlatMapUtil.flatMapMono(
                () -> Flux.from(this.context.selectFrom(FILES_FILE_SYSTEM)
                        .where(FILES_FILE_SYSTEM.PARENT_ID.eq(folderId))
                        .and(DSL.and(conditions))
                        .orderBy(getOrderList(page))
                        .limit(page.getPageSize())
                        .offset(page.getPageNumber() * page.getPageSize()))
                        .map(r -> r.into(FilesFileSystemRecord.class))
                        .map(r -> new FileDetail()
                                .setName(r.getName())
                                .setDirectory(r.getFileType() == FilesFileSystemFileType.DIRECTORY)
                                .setSize(r.getSize() == null ? 0l : r.getSize().longValue())
                                .setCreatedDate(r.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                                .setLastModifiedTime(r.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)))
                        .collectList(),

                files -> Mono.from(this.context.selectCount()
                        .from(FILES_FILE_SYSTEM)
                        .where(DSL.and(conditions)))
                        .map(Record1::value1).map(Long::valueOf),

                (files, count) -> Mono.just(new FilesPage(files, page.getPageNumber(), count)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FileSystemDao.list (with folder Id)"));
    }

    private List<SortField<?>> getOrderList(Pageable page) {

        if (page == null || page.getSort()
                .isEmpty() || page.getSort()
                        .isUnsorted()) {

            return List.of(FILES_FILE_SYSTEM.FILE_TYPE.asc(), FILES_FILE_SYSTEM.NAME.asc());
        }

        return page.getSort().stream().filter(e -> SORTABLE_FIELDS.containsKey(e.getProperty().toUpperCase()))
                .map(e -> {
                    if (e.getDirection().isDescending()) {
                        return SORTABLE_FIELDS.get(e.getProperty().toUpperCase()).desc();
                    }
                    return SORTABLE_FIELDS.get(e.getProperty().toUpperCase()).asc();
                })
                .collect(Collectors.toList());
    }

    private Condition getConditionForFileTypes(FileType[] fileType) {

        if (fileType == null || fileType.length == 0)
            return DSL.trueCondition();

        List<Condition> conditions = new ArrayList<>();

        for (FileType ft : fileType) {
            switch (ft) {
                case DIRECTORIES -> conditions.add(FILES_FILE_SYSTEM.FILE_TYPE.eq(FilesFileSystemFileType.DIRECTORY));
                case FILES -> conditions.add(FILES_FILE_SYSTEM.FILE_TYPE.eq(FilesFileSystemFileType.FILE));
                default -> conditions.add(DSL.or(ft.getAvailableFileExtensions().stream()
                        .map(e -> FILES_FILE_SYSTEM.NAME.like("%." + e)).toList()));
            }
        }

        return DSL.or(conditions);
    }

    public Mono<Boolean> deleteFile(FilesFileSystemType type, String clientCode, String path) {

        return FlatMapUtil.flatMapMono(

                () -> this.getId(type, clientCode, path).flatMap(Mono::justOrEmpty),

                id -> {

                    DeleteQuery<FilesFileSystemRecord> query = this.context.deleteQuery(FILES_FILE_SYSTEM);
                    query.addConditions(FILES_FILE_SYSTEM.ID.eq(id));
                    return Mono.from(query).map(e -> e > 0);
                }

        )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FileSystemDao.deleteFile"));
    }

    public Mono<FileDetail> createOrUpdateFile(FilesFileSystemType fileSystemType, String clientCode, String path,
            String fileName, boolean exists) {

        int index = path.lastIndexOf(R2_FILE_SEPARATOR_STRING);
        String parentPath = index == -1 ? "" : path.substring(0, index);
        String name;

        if (!StringUtil.safeIsBlank(fileName) && index != -1) {
            path = parentPath + R2_FILE_SEPARATOR_STRING + fileName;
            name = fileName;
        } else {
            name = path.substring(index + 1);
        }

        String key = path;

        return FlatMapUtil.flatMapMono(
                () -> this.getId(fileSystemType, clientCode, parentPath),
                parentId -> {

                    if (exists) {
                        return Mono.from(this.context.update(FILES_FILE_SYSTEM)
                                .set(FILES_FILE_SYSTEM.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                                .where(DSL.and(
                                        parentId.isPresent() ? FILES_FILE_SYSTEM.PARENT_ID.eq(parentId.get())
                                                : FILES_FILE_SYSTEM.PARENT_ID.isNull(),
                                        FILES_FILE_SYSTEM.NAME.eq(name))))
                                .map(e -> e > 0);
                    }

                    return Mono.from(this.context.insertInto(FILES_FILE_SYSTEM)
                            .set(FILES_FILE_SYSTEM.CODE, clientCode)
                            .set(FILES_FILE_SYSTEM.PARENT_ID, parentId.orElse(
                                    null))
                            .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.FILE)
                            .set(FILES_FILE_SYSTEM.NAME, name)
                            .set(FILES_FILE_SYSTEM.TYPE, fileSystemType)).map(e -> e > 0);
                },
                (parentId, updatedCreated) -> BooleanUtil.safeValueOf(updatedCreated)
                        ? this.getFileDetail(fileSystemType, clientCode, key)
                        : Mono.empty());
    }
}
