package com.fincity.saas.files.dao;

import static com.fincity.saas.files.jooq.tables.FilesFileSystem.FILES_FILE_SYSTEM;
import static com.fincity.saas.files.service.FileSystemService.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

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

    private ULong checkIfPathExists(int index, String[] pathParts, ULong parentId, MultiValuedMap<String, FilesFileSystemRecord> nameIndex) {

        if (index == pathParts.length)
            return parentId;

        String pathPart = pathParts[index];

        Collection<FilesFileSystemRecord> records = nameIndex.get(pathPart);

        if (records == null || records.isEmpty())
            return null;

        for (FilesFileSystemRecord rec : records) {
            if ((parentId == null && rec.getParentId() == null)
                || (parentId != null && parentId.equals(rec.getParentId())))
                return checkIfPathExists(index + 1, pathParts, rec.getId(), nameIndex);
        }

        return null;
    }

    public Mono<Optional<ULong>> getId(FilesFileSystemType type, String clientCode, String path) {

        return this.getFileRecord(type, clientCode, path, FilesFileSystemRecord::getId).map(Optional::ofNullable)
            .defaultIfEmpty(Optional.empty());
    }

    public Mono<Optional<ULong>> getFolderId(FilesFileSystemType type, String clientCode, String path) {

        if (StringUtil.safeIsBlank(path))
            return Mono.just(Optional.empty());

        String[] pathParts = path.split(R2_FILE_SEPARATOR_STRING);
        List<String> parts = new ArrayList<>();

        for (String part : pathParts) {
            if (StringUtil.safeIsBlank(part))
                continue;

            parts.add(parts.isEmpty() ? part : (parts.getLast() + R2_FILE_SEPARATOR_STRING + part));
        }

        return Flux.fromIterable(parts)
            .flatMap(p -> Flux.from(this.getId(type, clientCode, p).map(e -> Tuples.of(p, e)))).collectList()
            .flatMap(e -> {
                if (e.isEmpty())
                    return Mono.just(Optional.empty());

                Mono<Optional<ULong>> folderId = Mono.just(Optional.empty());
                int i = 0;
                for (; i < e.size(); i++) {
                    folderId = Mono.just(e.get(i).getT2());
                    if (e.get(i).getT2().isEmpty())
                        break;
                }

                for (; i < e.size(); i++) {
                    final int currentIteration = i;
                    folderId = folderId.flatMap(parentId -> this
                        .createFolder(type, clientCode, e.get(currentIteration).getT1()).map(Optional::of));
                }

                return folderId;
            });
    }

    public Mono<FileDetail> getFileDetail(FilesFileSystemType type, String clientCode, String path) {
        String[] pathParts = (path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path)
            .split(R2_FILE_SEPARATOR_STRING);

        if (pathParts.length == 0)
            return Mono.empty();

        return this.getFileRecord(type, clientCode, pathParts,
            r -> new FileDetail()
                .setId(r.getId())
                .setName(r.getName())
                .setDirectory(r.getFileType() == FilesFileSystemFileType.DIRECTORY)
                .setSize(r.getSize() == null ? 0L : r.getSize().longValue())
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
            .flatMap(list -> {
                if (list.isEmpty() || pathParts.length > list.size())
                    return Mono.empty();

                MultiValuedMap<String, FilesFileSystemRecord> nameIndex = new ArrayListValuedHashMap<>();
                Map<ULong, FilesFileSystemRecord> idIndex = new HashMap<>();

                for (FilesFileSystemRecord rec : list) {
                    nameIndex.put(rec.getName(), rec);
                    idIndex.put(rec.getId(), rec);
                }

                ULong id = checkIfPathExists(0, pathParts, null, nameIndex);

                if (id == null)
                    return Mono.empty();

                return Mono.just(idIndex.get(id));
            })
            .map(mapper);
    }

    public Mono<FilesPage> list(FilesFileSystemType type, String clientCode, String path, FileType[] fileType,
                                String filter,
                                Pageable page) {

        return FlatMapUtil.flatMapMono(

                () -> this.getId(type, clientCode, path),

                folderId -> {

                    if (folderId.isEmpty() && !StringUtil.safeIsBlank(path))
                        return Mono.just(new FilesPage(new ArrayList<>(), page.getPageNumber(), 0L));

                    return this.listInternal(type, clientCode, folderId.orElse(null),
                        fileType, filter, page);
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "FileSystemDao.list (with path)"));
    }

    private Mono<FilesPage> listInternal(FilesFileSystemType type, String clientCode, ULong folderId,
                                         FileType[] fileType,
                                         String filter, Pageable page) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(FILES_FILE_SYSTEM.CODE.eq(clientCode));
        if (folderId == null)
            conditions.add(FILES_FILE_SYSTEM.PARENT_ID.isNull());
        else
            conditions.add(FILES_FILE_SYSTEM.PARENT_ID.eq(folderId));

        conditions.add(FILES_FILE_SYSTEM.TYPE.eq(type));

        if (!StringUtil.safeIsBlank(filter))
            conditions.add(FILES_FILE_SYSTEM.NAME.like("%" + filter + "%"));

        if (fileType != null && fileType.length > 0)
            conditions.add(getConditionForFileTypes(fileType));

        return FlatMapUtil.flatMapMono(
                () -> Flux.from(this.context.selectFrom(FILES_FILE_SYSTEM)
                        .where(DSL.and(conditions))
                        .orderBy(getOrderList(page))
                        .limit(page.getPageSize())
                        .offset(page.getPageNumber() * page.getPageSize()))
                    .map(r -> r.into(FilesFileSystemRecord.class))
                    .map(r -> new FileDetail()
                        .setName(r.getName())
                        .setDirectory(r.getFileType() == FilesFileSystemFileType.DIRECTORY)
                        .setSize(r.getSize() == null ? 0L : r.getSize().longValue())
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

            return List.of(FILES_FILE_SYSTEM.FILE_TYPE.desc(), FILES_FILE_SYSTEM.NAME.asc());
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
                                               String fileName, ULong fileLength, boolean exists) {

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
                () -> this.getFolderId(fileSystemType, clientCode, parentPath),
                parentId -> {

                    if (exists) {
                        return Mono.from(this.context.update(FILES_FILE_SYSTEM)
                                .set(FILES_FILE_SYSTEM.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                                .where(DSL.and(
                                    parentId.map(FILES_FILE_SYSTEM.PARENT_ID::eq).orElseGet(FILES_FILE_SYSTEM.PARENT_ID::isNull),
                                    FILES_FILE_SYSTEM.NAME.eq(name))))
                            .map(e -> e > 0);
                    }

                    return Mono.from(this.context.insertInto(FILES_FILE_SYSTEM)
                        .set(FILES_FILE_SYSTEM.CODE, clientCode)
                        .set(FILES_FILE_SYSTEM.PARENT_ID, parentId.orElse(
                            null))
                        .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.FILE)
                        .set(FILES_FILE_SYSTEM.NAME, name)
                        .set(FILES_FILE_SYSTEM.SIZE, fileLength)
                        .set(FILES_FILE_SYSTEM.TYPE, fileSystemType)).map(e -> e > 0);
                },
                (parentId, updatedCreated) -> BooleanUtil.safeValueOf(updatedCreated)
                    ? this.getFileDetail(fileSystemType, clientCode, key)
                    : Mono.empty())

            .contextWrite(Context.of(LogUtil.METHOD_NAME, "FileSystemDao.createOrUpdateFile"));
    }

    public Mono<Boolean> createOrUpdateFileForZipUpload(FilesFileSystemType fileSystemType, String clientCode,
                                                        ULong folderId, String path, String fileName, ULong fileLength) {

        int index = path.lastIndexOf(R2_FILE_SEPARATOR_STRING);
        String parentPath = index == -1 ? "" : path.substring(0, index);
        String name;

        if (!StringUtil.safeIsBlank(fileName) && index != -1) {
            path = parentPath + R2_FILE_SEPARATOR_STRING + fileName;
            name = fileName;
        } else {
            name = path.substring(index + 1);
        }

        String finPath = path;

        return FlatMapUtil.flatMapMono(
                () -> Mono.just(Optional.ofNullable(folderId)),

                parentId -> this.getId(fileSystemType, clientCode, finPath),

                (parentId, existingId) -> {

                    if (existingId.isPresent()) {
                        return Mono.<Integer>from(this.context.update(FILES_FILE_SYSTEM)
                                .set(FILES_FILE_SYSTEM.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                                .set(FILES_FILE_SYSTEM.SIZE, fileLength)
                                .where(DSL.and(
                                    parentId.map(FILES_FILE_SYSTEM.PARENT_ID::eq).orElseGet(FILES_FILE_SYSTEM.PARENT_ID::isNull),
                                    FILES_FILE_SYSTEM.NAME.eq(name))))
                            .<Boolean>map(e -> e > 0);
                    }

                    return Mono.<Integer>from(this.context.insertInto(FILES_FILE_SYSTEM)
                        .set(FILES_FILE_SYSTEM.CODE, clientCode)
                        .set(FILES_FILE_SYSTEM.PARENT_ID, parentId.orElse(
                            null))
                        .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.FILE)
                        .set(FILES_FILE_SYSTEM.NAME, name)
                        .set(FILES_FILE_SYSTEM.SIZE, fileLength)
                        .set(FILES_FILE_SYSTEM.TYPE, fileSystemType)).<Boolean>map(e -> e > 0);
                })
            .contextWrite(Context.of(LogUtil.METHOD_NAME, "FileSystemDao.createOrUpdateFileForZipUpload"));
    }

    public Mono<ULong> createFolder(FilesFileSystemType fileSystemType, String clientCode, String path) {

        String resourcePath = path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path;

        int lastIndex = resourcePath.lastIndexOf(R2_FILE_SEPARATOR_STRING);
        String parentPath = lastIndex == -1 ? null : resourcePath.substring(0, lastIndex);
        String name = lastIndex == -1 ? resourcePath : resourcePath.substring(lastIndex + 1);

        return FlatMapUtil.flatMapMono(

            () -> {

                if (parentPath == null)
                    return Mono.just(Optional.empty());

                return this.getId(fileSystemType, clientCode, parentPath);
            },
            parentId -> Mono.from(this.context.transactionPublisher(tp -> Mono.from(tp.dsl().insertInto(FILES_FILE_SYSTEM)
                .set(FILES_FILE_SYSTEM.CODE, clientCode)
                .set(FILES_FILE_SYSTEM.PARENT_ID, parentId.orElse(null))
                .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.DIRECTORY)
                .set(FILES_FILE_SYSTEM.NAME, name)
                .set(FILES_FILE_SYSTEM.TYPE, fileSystemType).returningResult(FILES_FILE_SYSTEM.ID))
            )).delayElement(Duration.ofMillis(1000L)).map(Record1::value1)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "FileSystemDao.createFolder"));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    @ToString
    private static class Node {
        private ULong id;
        private String name;
        private String path;
        private Node parent;
    }

    public Mono<Map<String, ULong>> createFolders(FilesFileSystemType fileSystemType, String clientCode,
                                                  List<String> paths) {

        Map<String, Node> nodeMap = new HashMap<>();

        for (String path : paths) {
            String[] pathParts = path.split(R2_FILE_SEPARATOR_STRING);
            StringBuilder sb = new StringBuilder();
            for (String part : pathParts) {
                Node parentNode = null;
                if (!sb.isEmpty()) {
                    parentNode = nodeMap.get(sb.toString());
                    sb.append(R2_FILE_SEPARATOR_STRING);
                }

                sb.append(part);
                if (!nodeMap.containsKey(sb.toString())) {
                    nodeMap.put(sb.toString(),
                        new Node(null, part, sb.toString(), parentNode));
                }
            }
        }

        List<Node> nodes = new ArrayList<>();

        for (Node node : nodeMap.values()) {
            if (node.getParent() != null)
                continue;
            nodes.add(node);
        }

        var nodeFlux = Flux.fromIterable(nodes)
            .expandDeep(x -> Flux.fromStream(nodeMap.values().stream().filter(y -> y.getParent() == x)))
            .mapNotNull(n -> {

                if (n.getId() != null)
                    return n;

                return Mono.defer(() -> this.selectFolderId(fileSystemType, clientCode, n))
                    .switchIfEmpty(Mono.defer(() -> this.insertFolder(fileSystemType, clientCode, n)))
                    .map(n::setId).block();
            })
            .subscribeOn(Schedulers.boundedElastic());

        return nodeFlux.parallel(1)
            .collectSortedList(Comparator.comparing(Node::getPath))
            .map(ns -> {
                Map<String, ULong> map = new HashMap<>();
                for (String p : paths) {
                    Node n = nodeMap.get(p);
                    map.put(p, n.getId());
                }
                return map;
            });
    }

    private Mono<ULong> selectFolderId(FilesFileSystemType fileSystemType, String clientCode, Node node) {

        return Mono.from(this.context.select(FILES_FILE_SYSTEM.ID).from(FILES_FILE_SYSTEM).where(DSL.and(
            FILES_FILE_SYSTEM.CODE.eq(clientCode),
            FILES_FILE_SYSTEM.NAME.eq(node.getName()),
            FILES_FILE_SYSTEM.TYPE.eq(fileSystemType),
            node.getParent() == null ? FILES_FILE_SYSTEM.PARENT_ID.isNull()
                : FILES_FILE_SYSTEM.PARENT_ID.eq(node.getParent().getId()),
            FILES_FILE_SYSTEM.FILE_TYPE.eq(FilesFileSystemFileType.DIRECTORY)))).map(Record1::value1);
    }

    private Mono<ULong> insertFolder(FilesFileSystemType fileSystemType, String clientCode, Node node) {

        return Mono.from(this.context.insertInto(FILES_FILE_SYSTEM)
                .set(FILES_FILE_SYSTEM.CODE, clientCode)
                .set(FILES_FILE_SYSTEM.PARENT_ID, node.getParent() == null ? null : node.getParent().getId())
                .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.DIRECTORY)
                .set(FILES_FILE_SYSTEM.NAME, node.getName())
                .set(FILES_FILE_SYSTEM.TYPE, fileSystemType).returningResult(FILES_FILE_SYSTEM.ID))
            .map(Record1::value1);
    }
}
