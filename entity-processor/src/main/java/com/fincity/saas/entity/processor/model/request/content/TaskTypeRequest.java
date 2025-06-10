package com.fincity.saas.entity.processor.model.request.content;

import com.fincity.saas.entity.processor.model.base.BaseRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TaskTypeRequest extends BaseRequest<TaskTypeRequest> {}
