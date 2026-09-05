package com.modlix.saas.files.util;

import java.util.Set;

import org.springframework.http.HttpStatus;

import com.modlix.saas.commons2.exception.GenericException;

/**
 * One gate for every name that becomes a row in {@code files_file_system}.
 *
 * The file tree is a database table, not a filesystem, so it will happily store
 * whatever segment a URL hands it -- and every files/assets page builds its
 * create-directory URL as {@code .../directory{{path ?? ''}}/{{Page.folderName}}},
 * with the PATH guarded and the NAME not. A `{{path}}` that resolves to nothing
 * renders as the four characters {@code undefined}, so clicking Create on an
 * empty New-folder box created a folder genuinely NAMED "undefined". Dev had 115
 * of them across dozens of clients before this existed.
 *
 * Guarding the nine pages that share that shape is whack-a-mole; the names have
 * to be refused where they are written. Rejecting rather than silently skipping
 * is deliberate: a 400 is what makes the remaining callers findable.
 */
public class FileNameUtil {

    private FileNameUtil() {
    }

    /**
     * What a value stringifies to in JavaScript when it is not there. None of
     * these is a name anybody types on purpose, and every one of them is
     * evidence of an unguarded interpolation upstream. Compared case
     * insensitively, which costs a folder honestly called "Null" -- a trade
     * worth making against corrupting the tree.
     */
    private static final Set<String> STRINGIFIED_NOTHING = Set.of(
            "undefined", "null", "nan", "[object object]", "[object undefined]");

    /** Segments a path may not contain, whatever the storage underneath. */
    private static final Set<String> RESERVED = Set.of(".", "..");

    public static void validate(String name) {

        if (name == null || name.isBlank())
            throw new GenericException(HttpStatus.BAD_REQUEST,
                    "A file or folder name cannot be blank.");

        String trimmed = name.trim();

        if (RESERVED.contains(trimmed))
            throw new GenericException(HttpStatus.BAD_REQUEST,
                    "'" + trimmed + "' is not a usable file or folder name.");

        if (STRINGIFIED_NOTHING.contains(trimmed.toLowerCase()))
            throw new GenericException(HttpStatus.BAD_REQUEST,
                    "'" + trimmed + "' is not a usable name. It is what an empty value looks like"
                            + " once it has been put into a URL, so the caller most likely sent a"
                            + " path with a missing part in it.");

        // A separator inside a single segment would silently deepen the tree,
        // and a control character is never intended.
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '/' || c == '\\' || c < 0x20 || c == 0x7F)
                throw new GenericException(HttpStatus.BAD_REQUEST,
                        "A file or folder name cannot contain a path separator or a control"
                                + " character.");
        }
    }

    /** True when the name would be refused, for callers that filter rather than fail. */
    public static boolean isUsable(String name) {
        try {
            validate(name);
            return true;
        } catch (GenericException e) {
            return false;
        }
    }
}
