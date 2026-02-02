package com.modlix.saas.files.dao;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.util.FileType;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.files.jooq.enums.FilesFileSystemFileType;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;

import static com.modlix.saas.files.jooq.tables.FilesFileSystem.FILES_FILE_SYSTEM;

import com.modlix.saas.files.jooq.tables.records.FilesFileSystemRecord;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.model.FilesPage;

import static com.modlix.saas.files.service.FileSystemService.R2_FILE_SEPARATOR_STRING;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

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

    public boolean exists(FilesFileSystemType type, String clientCode, String path) {

        return getId(type, clientCode, path).isPresent();
    }

    private ULong checkIfPathExists(int index, String[] pathParts, ULong parentId,
                                    MultiValuedMap<String, FilesFileSystemRecord> nameIndex) {

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

    public Optional<ULong> getId(FilesFileSystemType type, String clientCode, String path) {

        return Optional.ofNullable(this.getFileRecord(type, clientCode, path, FilesFileSystemRecord::getId));
    }

    public ULong getId(FilesFileSystemType type, String clientCode, ULong folderId, String path) {

        String fileName = path.substring(path.lastIndexOf(R2_FILE_SEPARATOR_STRING) + 1);

        return this.context.select(FILES_FILE_SYSTEM.ID).from(FILES_FILE_SYSTEM)
                .where(DSL.and(FILES_FILE_SYSTEM.TYPE.eq(type),
                        folderId == null ? FILES_FILE_SYSTEM.PARENT_ID.isNull()
                                : FILES_FILE_SYSTEM.PARENT_ID.eq(folderId),
                        FILES_FILE_SYSTEM.NAME.eq(fileName),
                        FILES_FILE_SYSTEM.CODE.eq(clientCode)))
                .limit(1).fetchOneInto(ULong.class);
    }

    public Optional<ULong> getFolderId(FilesFileSystemType type, String clientCode, String path) {

        if (StringUtil.safeIsBlank(path))
            return Optional.empty();

        String[] pathParts = path.split(R2_FILE_SEPARATOR_STRING);
        List<String> parts = new ArrayList<>();

        for (String part : pathParts) {
            if (StringUtil.safeIsBlank(part))
                continue;

            parts.add(parts.isEmpty() ? part : (parts.getLast() + R2_FILE_SEPARATOR_STRING + part));
        }

        if (parts.isEmpty())
            return Optional.empty();

        ULong folderId = null;

        for (String currentPath : parts) {
            Optional<ULong> existingId = this.getId(type, clientCode, currentPath);

            if (existingId.isPresent()) {
                folderId = existingId.get();
                continue;
            }

            folderId = this.createFolder(type, clientCode, currentPath);
        }

        return Optional.ofNullable(folderId);
    }

    public FileDetail getFileDetail(FilesFileSystemType type, String clientCode, String path) {
        String[] pathParts = (path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path)
                .split(R2_FILE_SEPARATOR_STRING);

        if (pathParts.length == 0)
            return null;

        return this.getFileRecord(type, clientCode, pathParts,
                r -> new FileDetail()
                        .setId(r.getId())
                        .setName(r.getName())
                        .setDirectory(r.getFileType() == FilesFileSystemFileType.DIRECTORY)
                        .setSize(r.getSize() == null ? 0L : r.getSize().longValue())
                        .setCreatedDate(r.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                        .setLastModifiedTime(r.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)));
    }

    private <T> T getFileRecord(FilesFileSystemType type, String clientCode, String path,
                                Function<FilesFileSystemRecord, T> mapper) {

        if (StringUtil.safeIsBlank(path))
            return null;

        String[] pathParts = (path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path)
                .split(R2_FILE_SEPARATOR_STRING);

        return this.getFileRecord(type, clientCode, pathParts, mapper);
    }

    private <T> T getFileRecord(FilesFileSystemType type, String clientCode, String[] pathParts,
                                Function<FilesFileSystemRecord, T> mapper) {

        List<FilesFileSystemRecord> list = this.context.selectFrom(FILES_FILE_SYSTEM)
                .where(DSL.and(FILES_FILE_SYSTEM.CODE.eq(clientCode), FILES_FILE_SYSTEM.NAME.in(pathParts),
                        FILES_FILE_SYSTEM.TYPE.eq(type)))
                .fetchInto(FilesFileSystemRecord.class);

        if (list.isEmpty() || pathParts.length > list.size())
            return null;

        MultiValuedMap<String, FilesFileSystemRecord> nameIndex = new ArrayListValuedHashMap<>();
        Map<ULong, FilesFileSystemRecord> idIndex = new HashMap<>();

        for (FilesFileSystemRecord rec : list) {
            nameIndex.put(rec.getName(), rec);
            idIndex.put(rec.getId(), rec);
        }

        ULong id = checkIfPathExists(0, pathParts, null, nameIndex);

        if (id == null)
            return null;

        return mapper.apply(idIndex.get(id));
    }

    public FilesPage list(FilesFileSystemType type, String clientCode, String path, FileType[] fileType,
                          String filter,
                          Pageable page) {

        Optional<ULong> folderId = this.getId(type, clientCode, path);

        if (folderId.isEmpty() && !StringUtil.safeIsBlank(path))
            return new FilesPage(new ArrayList<>(), page.getPageNumber(), 0L);

        return this.listInternal(type, clientCode, folderId.orElse(null),
                fileType, filter, page);
    }

    private FilesPage listInternal(FilesFileSystemType type, String clientCode, ULong folderId,
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

        var files = this.context.selectFrom(FILES_FILE_SYSTEM)
                .where(DSL.and(conditions))
                .orderBy(getOrderList(page))
                .limit(page.getPageSize())
                .offset(page.getPageNumber() * page.getPageSize())
                .fetchStreamInto(FilesFileSystemRecord.class)
                .map(r -> new FileDetail()
                        .setName(r.getName())
                        .setDirectory(r.getFileType() == FilesFileSystemFileType.DIRECTORY)
                        .setSize(r.getSize() == null ? 0L : r.getSize().longValue())
                        .setCreatedDate(r.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                        .setLastModifiedTime(r.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)))
                .toList();

        var count = this.context.selectCount()
                .from(FILES_FILE_SYSTEM)
                .where(DSL.and(conditions)).limit(1).fetchOneInto(Long.class);

        return new FilesPage(files, page.getPageNumber(), count);
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

    public boolean deleteFile(FilesFileSystemType type, String clientCode, String path) {
        Optional<ULong> id = this.getId(type, clientCode, path);
        if (id.isEmpty())
            return false;

        DeleteQuery<FilesFileSystemRecord> query = this.context.deleteQuery(FILES_FILE_SYSTEM);
        query.addConditions(FILES_FILE_SYSTEM.ID.eq(id.get()));
        return query.execute() > 0;
    }

    public FileDetail createOrUpdateFile(FilesFileSystemType fileSystemType, String clientCode, String path,
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

        var parentId = this.getFolderId(fileSystemType, clientCode, parentPath);
        var updatedCreated = false;

        if (exists) {
            updatedCreated = this.context.update(FILES_FILE_SYSTEM)
                    .set(FILES_FILE_SYSTEM.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .where(parentId.map(FILES_FILE_SYSTEM.PARENT_ID::eq)
                            .orElseGet(FILES_FILE_SYSTEM.PARENT_ID::isNull).and(FILES_FILE_SYSTEM.NAME.eq(name)))
                    .execute() > 0;
        } else {

            updatedCreated = this.context.insertInto(FILES_FILE_SYSTEM)
                    .set(FILES_FILE_SYSTEM.CODE, clientCode)
                    .set(FILES_FILE_SYSTEM.PARENT_ID, parentId.get())
                    .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.FILE)
                    .set(FILES_FILE_SYSTEM.NAME, name)
                    .set(FILES_FILE_SYSTEM.SIZE, fileLength)
                    .set(FILES_FILE_SYSTEM.TYPE, fileSystemType)
                    .execute() > 0;
        }
        if (!updatedCreated)
            return null;

        return this.getFileDetail(fileSystemType, clientCode, path);
    }

    public boolean createOrUpdateFileForZipUpload(ULong existingId, FilesFileSystemType fileSystemType,
                                                  String clientCode,
                                                  ULong folderId, String path, String fileName, ULong fileLength) {

        int index = path.lastIndexOf(R2_FILE_SEPARATOR_STRING);
        String name;

        if (!StringUtil.safeIsBlank(fileName) && index != -1) {
            name = fileName;
        } else {
            name = path.substring(index + 1);
        }

        if (existingId != null) {
            return this.context.update(FILES_FILE_SYSTEM)
                    .set(FILES_FILE_SYSTEM.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .set(FILES_FILE_SYSTEM.SIZE, fileLength)
                    .where(DSL.and(
                            folderId == null ? FILES_FILE_SYSTEM.PARENT_ID.isNull()
                                    : FILES_FILE_SYSTEM.PARENT_ID.eq(folderId),
                            FILES_FILE_SYSTEM.NAME.eq(name)))
                    .execute() > 0;
        }

        return this.context.insertInto(FILES_FILE_SYSTEM)
                .set(FILES_FILE_SYSTEM.CODE, clientCode)
                .set(FILES_FILE_SYSTEM.PARENT_ID, folderId)
                .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.FILE)
                .set(FILES_FILE_SYSTEM.NAME, name)
                .set(FILES_FILE_SYSTEM.SIZE, fileLength)
                .set(FILES_FILE_SYSTEM.TYPE, fileSystemType)
                .execute() > 0;
    }

    public ULong createFolder(FilesFileSystemType fileSystemType, String clientCode, String path) {

        String resourcePath = path.startsWith(R2_FILE_SEPARATOR_STRING) ? path.substring(1) : path;

        int lastIndex = resourcePath.lastIndexOf(R2_FILE_SEPARATOR_STRING);
        String parentPath = lastIndex == -1 ? null : resourcePath.substring(0, lastIndex);
        String name = lastIndex == -1 ? resourcePath : resourcePath.substring(lastIndex + 1);

        var parentId = parentPath == null ? Optional.<ULong>empty()
                : this.getFolderId(fileSystemType, clientCode, parentPath);

        return this.context.insertInto(FILES_FILE_SYSTEM)
                .set(FILES_FILE_SYSTEM.CODE, clientCode)
                .set(FILES_FILE_SYSTEM.PARENT_ID, parentId.orElse(null))
                .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.DIRECTORY)
                .set(FILES_FILE_SYSTEM.NAME, name)
                .set(FILES_FILE_SYSTEM.TYPE, fileSystemType).returningResult(FILES_FILE_SYSTEM.ID)
                .fetchOneInto(ULong.class);
    }

    public void clearFileSystem(FilesFileSystemType fileSystemType, String clientCode, ULong folderId) {
        this.context.deleteFrom(FILES_FILE_SYSTEM).where(DSL.and(
                FILES_FILE_SYSTEM.TYPE.eq(fileSystemType),
                clientCode == null ? DSL.noCondition() : FILES_FILE_SYSTEM.CODE.eq(clientCode),
                folderId == null ? DSL.noCondition() : FILES_FILE_SYSTEM.PARENT_ID.eq(folderId))).execute();
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

    public Map<String, ULong> createFolders(FilesFileSystemType fileSystemType, String clientCode,
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

        List<Node> sortedNodes = new ArrayList<>(nodeMap.values());
        sortedNodes.sort(Comparator.comparingInt(this::getDepth).thenComparing(Node::getPath));

        for (Node node : sortedNodes) {
            ensureNodeId(node, fileSystemType, clientCode);
        }

        Map<String, ULong> map = new HashMap<>();
        for (String p : paths) {
            Node node = nodeMap.get(p);
            if (node != null) {
                map.put(p, node.getId());
            }
        }
        return map;
    }

    private void ensureNodeId(Node node, FilesFileSystemType fileSystemType, String clientCode) {

        if (node.getId() != null)
            return;

        if (node.getParent() != null)
            ensureNodeId(node.getParent(), fileSystemType, clientCode);

        ULong folderId = this.selectFolderId(fileSystemType, clientCode, node);

        if (folderId == null) {
            folderId = this.insertFolder(fileSystemType, clientCode, node);
        }

        node.setId(folderId);
    }

    private int getDepth(Node node) {

        int depth = 0;
        Node current = node.getParent();

        while (current != null) {
            depth++;
            current = current.getParent();
        }

        return depth;
    }

    private ULong selectFolderId(FilesFileSystemType fileSystemType, String clientCode, Node node) {

        return this.context.select(FILES_FILE_SYSTEM.ID).from(FILES_FILE_SYSTEM).where(DSL.and(
                        FILES_FILE_SYSTEM.CODE.eq(clientCode),
                        FILES_FILE_SYSTEM.NAME.eq(node.getName()),
                        FILES_FILE_SYSTEM.TYPE.eq(fileSystemType),
                        node.getParent() == null ? FILES_FILE_SYSTEM.PARENT_ID.isNull()
                                : FILES_FILE_SYSTEM.PARENT_ID.eq(node.getParent().getId()),
                        FILES_FILE_SYSTEM.FILE_TYPE.eq(FilesFileSystemFileType.DIRECTORY)))
                .limit(1)
                .fetchOneInto(ULong.class);
    }

    private ULong insertFolder(FilesFileSystemType fileSystemType, String clientCode, Node node) {

        return this.context.insertInto(FILES_FILE_SYSTEM)
                .set(FILES_FILE_SYSTEM.CODE, clientCode)
                .set(FILES_FILE_SYSTEM.PARENT_ID, node.getParent() == null ? null : node.getParent().getId())
                .set(FILES_FILE_SYSTEM.FILE_TYPE, FilesFileSystemFileType.DIRECTORY)
                .set(FILES_FILE_SYSTEM.NAME, node.getName())
                .set(FILES_FILE_SYSTEM.TYPE, fileSystemType).returningResult(FILES_FILE_SYSTEM.ID)
                .fetchOneInto(ULong.class);
    }

    public int[] batchInsert(List<FilesFileSystemRecord> records) {
        return this.context.batchInsert(records).execute();
    }
}
