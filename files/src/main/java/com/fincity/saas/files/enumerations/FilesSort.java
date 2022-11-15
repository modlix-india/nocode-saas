package com.fincity.saas.files.enumerations;

import java.io.File;
import java.util.Comparator;
import java.util.function.Function;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.util.FileExtensionUtil;

public enum FilesSort {

	LASTMODIFIED_ASC(Direction.ASC, FileDetail::getModifiedTime, Comparator.comparingLong(File::lastModified)),
	LASTMODIFIED_DESC(Direction.DESC, FileDetail::getModifiedTime, Comparator.comparingLong(File::lastModified)
	        .reversed()),
	NAME_ASC(Direction.ASC, FileDetail::getName, Comparator.comparing(File::getName,
	        String.CASE_INSENSITIVE_ORDER)),
	NAME_DESC(Direction.DESC, FileDetail::getName, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
	        .reversed()),
	SIZE_ASC(Direction.ASC, FileDetail::getSize, Comparator
	        .comparingLong(File::length)),
	SIZE_DESC(Direction.DESC, FileDetail::getSize, Comparator.comparingLong(File::length)
	        .reversed()),
	TYPE_ASC(Direction.ASC, FileDetail::getFileType,
	        Comparator
	                .<File, String>comparing(
	                        e -> e.isDirectory() ? " "
	                                : FileExtensionUtil.get(e.getName()),
	                        String.CASE_INSENSITIVE_ORDER)),
	TYPE_DESC(Direction.DESC, FileDetail::getFileType,
	        Comparator
	                .<File, String>comparing(e -> e.isDirectory() ? " " : FileExtensionUtil.get(e.getName()),
	                        String.CASE_INSENSITIVE_ORDER)
	                .reversed()),;

	private Sort.Direction sortDirection;
	private Function<FileDetail, Object> fieldSupplier;
	private Comparator<File> comparator;

	FilesSort(Sort.Direction sortDirection, Function<FileDetail, Object> fieldSupplier,
	        Comparator<File> comparator) {
		this.sortDirection = sortDirection;
		this.fieldSupplier = fieldSupplier;
		this.comparator = comparator;
	}

	public Sort.Direction getSortDirection() {
		return sortDirection;
	}

	public Object getFieldValue(FileDetail fd) {
		return fieldSupplier.apply(fd);
	}

	public Comparator<File> getComparator() {
		return this.comparator;
	}
}
