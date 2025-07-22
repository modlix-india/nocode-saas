package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ComponentType;
import java.util.ArrayList;
import java.util.List;


// TODO: review
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ButtonComponent extends Component<ButtonComponent> {

    private List<Button> buttons;


    public ButtonComponent() {
        super(ComponentType.BUTTONS);
    }


    public List<Button> getButtons() {
        return buttons;
    }


    public ButtonComponent setButtons(List<Button> buttons) {
        this.buttons = buttons;
        return this;
    }


    public ButtonComponent addButton(Button button) {
        if (this.buttons == null) this.buttons = new ArrayList<>();
        this.buttons.add(button);
        return this;
    }
}
