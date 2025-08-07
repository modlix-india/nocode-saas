package com.fincity.saas.notification.model.message.action;

import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.ActionType;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Action implements Serializable {

    private String code = UniqueUtil.shortUUID();
    private boolean isCompleted = Boolean.FALSE;
    private ActionType actionType;
    private String actionUrl;
    private Map<String, Object> actionParams;

    private static Action of(ActionType actionType, String actionUrl, Map<String, Object> actionParams) {
        return new Action()
                .setCode(UniqueUtil.shortUUID())
                .setCompleted(Boolean.FALSE)
                .setActionType(actionType)
                .setActionUrl(actionUrl)
                .setActionParams(actionParams);
    }

    private interface IAction<T extends IAction<T>> {

        T setAction(Action action);

        default T setAction(ActionType actionType, String actionUrl, Map<String, Object> actionParams) {
            return setAction(Action.of(actionType, actionUrl, actionParams));
        }
    }

    public interface UniAction<T extends UniAction<T>> extends IAction<T> {
        Action getAction();
    }

    public interface MultiAction<T extends MultiAction<T>> extends IAction<T> {

        Map<String, Action> getActions();

        T setActions(Map<String, Action> actions);

        default T setActions(Action... actions) {
            return setActions(Arrays.stream(actions).toList());
        }

        default T setActions(List<Action> actions) {
            Map<String, Action> actionMap = getActions();
            if (actionMap == null) actionMap = new HashMap<>();

            for (Action action : actions) actionMap.put(action.getActionUrl(), action);

            return setActions(actionMap);
        }

        default Action getAction(String actionCode) {
            return getActions() != null ? getActions().getOrDefault(actionCode, null) : null;
        }

        @Override
        default T setAction(Action action) {
            Map<String, Action> actionMap = getActions();
            if (actionMap == null) actionMap = new HashMap<>();

            actionMap.put(action.getCode(), action);

            return setActions(actionMap);
        }
    }
}
