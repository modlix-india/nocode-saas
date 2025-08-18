package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ButtonComponent extends Component<ButtonComponent> {

    @Serial
    private static final long serialVersionUID = -3377486597127427215L;

    private List<Button> buttons;

    public ButtonComponent() {
        super(ComponentType.BUTTONS);
    }

    public ButtonComponent addButton(Button button) {
        if (this.buttons == null) this.buttons = new ArrayList<>();
        this.buttons.add(button);
        return this;
    }
}
