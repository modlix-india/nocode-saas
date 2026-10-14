package com.fincity.saas.commons.core.model;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Which of a storage's LIVE rows go into its DRAFT when an app's data is copied.
 *
 * Without a filter every live row is copied. With one, a row is copied when it
 * matches {@link #condition} AND every {@link #follow} holds. That is enough to
 * seed a draft with a slice of an app rather than all of it: pick the root rows by
 * condition (deals created since a date), then let everything that hangs off them
 * follow (their leads, their activities).
 *
 * A follow names a column of this storage and a column of another storage that is
 * copied FIRST: the row is kept only when the value it holds is among the values the
 * other storage's DRAFT rows hold. The default other column is {@code _id}, which
 * reads "this row points at a row that made it into the draft". Pointing it at a
 * relation column instead reads the other way round, "a row in the draft points at
 * this one": a lead follows its deals with {@code {field: _id, storage: deal,
 * refField: ownerId}}.
 *
 * With several follows a row is kept when each of its follow columns is either empty
 * or satisfied, and at least one is filled. So an activity following both its deal
 * and its lead keeps the lead-level activities (no deal) and drops the ones belonging
 * to a deal that was left out, even though their lead came across.
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DraftCopyFilter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AbstractCondition condition;

    private List<Follow> follow;

    public boolean hasFollow() {
        return this.follow != null && !this.follow.isEmpty();
    }

    @Data
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Follow implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public static final String DEFAULT_REF_FIELD = "_id";

        /** The column of the storage being copied. */
        private String field;

        /** The storage whose draft rows decide, copied before this one. */
        private String storage;

        /** The column of {@link #storage} whose values are matched; {@code _id} when blank. */
        private String refField;

        public String refFieldOrDefault() {
            return this.refField == null || this.refField.isBlank() ? DEFAULT_REF_FIELD : this.refField;
        }
    }
}
