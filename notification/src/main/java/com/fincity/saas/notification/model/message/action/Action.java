package com.fincity.saas.notification.model.message.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.ActionType;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class Action {

	private String code = UniqueUtil.shortUUID();
	private boolean isCompleted = Boolean.FALSE;
	private String actionType;
	private String actionUrl;
	private Map<String, Object> actionParams;

	private static Action of(ActionType actionType, String actionUrl, Map<String, Object> actionParams) {
		return new Action()
				.setCode(UniqueUtil.shortUUID())
				.setCompleted(Boolean.FALSE)
				.setActionType(actionType.getLiteral())
				.setActionUrl(actionUrl)
				.setActionParams(actionParams);
	}

	public interface IAction<T extends IAction<T>> {

		T setAction(Action action);

		default T setAction(ActionType actionType, String actionUrl, Map<String, Object> actionParams) {
			return setAction(Action.of(actionType, actionUrl, actionParams));
		}
	}

	public interface UniAction<T extends UniAction<T>> extends IAction<T> {
		Action getAction();
	}

	public interface MultiAction<T extends MultiAction<T>> extends IAction<T> {

		List<Action> getActions();

		T setActions(List<Action> actions);

		default T setActions(Action... actions) {
			return setActions(Arrays.stream(actions).toList());
		}

		@Override
		default T setAction(Action action) {
			List<Action> actions = getActions();
			if (actions == null)
				actions = new ArrayList<>();

			actions.add(action);
			return setActions(actions);
		}
	}
}
