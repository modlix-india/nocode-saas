package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityDepartment.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.jooq.tables.records.SecurityDepartmentRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class DepartmentDAO extends AbstractClientCheckDAO<SecurityDepartmentRecord, ULong, Department> {

    protected DepartmentDAO() {
        super(Department.class, SECURITY_DEPARTMENT, SECURITY_DEPARTMENT.ID);
    }

    @Override
    protected Field<ULong> getClientIDField() {
        return SECURITY_DEPARTMENT.CLIENT_ID;
    }

    public Mono<Boolean> checkSameClient(ULong clientId, ULong departmentId) {
        return Mono.from(this.dslContext.selectCount()
                .from(SECURITY_DEPARTMENT)
                .where(SECURITY_DEPARTMENT.ID.eq(departmentId)).and(SECURITY_DEPARTMENT.CLIENT_ID.eq(clientId)))
                .map(r -> r.value1() == 1);
    }

    private Mono<Boolean> createRelations(List<Tuple2<ULong, ULong>> relations) {
        return Flux.fromIterable(relations).flatMap(tup -> Mono.from(
                this.dslContext.update(SECURITY_DEPARTMENT).set(SECURITY_DEPARTMENT.PARENT_DEPARTMENT_ID, tup.getT2())
                        .where(SECURITY_DEPARTMENT.ID.eq(tup.getT1()))))
                .collectList().map(list -> true);
    }

    public Mono<Map<ULong, Tuple2<AppRegistrationDepartment, Department>>> createForRegistration(Client client,
            List<AppRegistrationDepartment> departments) {

        return FlatMapUtil.flatMapMono(
                () -> Flux.fromIterable(departments)
                        .flatMap(e -> this.create(new Department().setClientId(client.getId())
                                .setName(e.getName())
                                .setDescription(e.getDescription())).map(d -> Tuples.of(e, d)))
                        .collectList(),

                list -> {
                    Map<ULong, Tuple2<AppRegistrationDepartment, Department>> index = list.stream()
                            .collect(Collectors.toMap(
                                    e -> e.getT1().getId(), Function.identity()));

                    List<Tuple2<ULong, ULong>> relations = new ArrayList<>();
                    for (Tuple2<AppRegistrationDepartment, Department> tuple : list) {
                        if (tuple.getT1().getParentDepartmentId() == null
                                || !index.containsKey(tuple.getT1().getParentDepartmentId()))
                            continue;

                        // id, parentId tuple
                        relations.add(Tuples.of(tuple.getT2().getId(),
                                index.get(tuple.getT1().getParentDepartmentId()).getT2().getId()));
                    }

                    if (relations.isEmpty())
                        return Mono.just(index);

                    return this.createRelations(relations)
                            .map(x -> index);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "DepartmentDAO.createForRegistration"));
    }
}
