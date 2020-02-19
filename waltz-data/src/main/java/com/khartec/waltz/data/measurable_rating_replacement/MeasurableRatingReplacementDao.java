/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

package com.khartec.waltz.data.measurable_rating_replacement;

import com.khartec.waltz.common.DateTimeUtilities;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.measurable_rating_replacement.ImmutableMeasurableRatingReplacement;
import com.khartec.waltz.model.measurable_rating_replacement.MeasurableRatingReplacement;
import com.khartec.waltz.schema.tables.records.MeasurableRatingReplacementRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.DateTimeUtilities.toLocalDateTime;
import static com.khartec.waltz.common.DateTimeUtilities.toSqlDate;
import static com.khartec.waltz.model.EntityReference.mkRef;
import static com.khartec.waltz.schema.Tables.APPLICATION;
import static com.khartec.waltz.schema.tables.MeasurableRatingPlannedDecommission.MEASURABLE_RATING_PLANNED_DECOMMISSION;
import static com.khartec.waltz.schema.tables.MeasurableRatingReplacement.MEASURABLE_RATING_REPLACEMENT;

@Repository
public class MeasurableRatingReplacementDao {


    private final DSLContext dsl;


    @Autowired
    MeasurableRatingReplacementDao(DSLContext dsl){
        checkNotNull(dsl, "dsl must not be null");
        this.dsl = dsl;
    }


    public static final RecordMapper<? super Record, MeasurableRatingReplacement> TO_DOMAIN_MAPPER =  record -> {

        boolean isApplication = record.get(MEASURABLE_RATING_REPLACEMENT.ENTITY_KIND).equalsIgnoreCase(EntityKind.APPLICATION.name());

        String name = null;

        if (isApplication) {
            name = record.get(APPLICATION.NAME);
        }

        MeasurableRatingReplacementRecord r = record.into(MEASURABLE_RATING_REPLACEMENT);

        EntityReference entityReference = isApplication
                ? mkRef(EntityKind.valueOf(r.getEntityKind()), r.getEntityId(), name)
                : mkRef(EntityKind.valueOf(r.getEntityKind()), r.getEntityId());

        return ImmutableMeasurableRatingReplacement.builder()
                .id(r.getId())
                .entityReference(entityReference)
                .decommissionId(r.getDecommissionId())
                .plannedCommissionDate(toLocalDateTime(r.getPlannedCommissionDate()))
                .createdAt(toLocalDateTime(r.getCreatedAt()))
                .createdBy(r.getCreatedBy())
                .lastUpdatedAt(toLocalDateTime(r.getUpdatedAt()))
                .lastUpdatedBy(r.getUpdatedBy())
                .build();
    };


    public MeasurableRatingReplacement getById(Long id){
        return dsl
                .selectFrom(MEASURABLE_RATING_REPLACEMENT)
                .where(MEASURABLE_RATING_REPLACEMENT.ID.eq(id))
                .fetchOne(TO_DOMAIN_MAPPER);
    }


    public Set<MeasurableRatingReplacement> fetchByDecommissionId(Long decommissionId){
        return dsl
                .select()
                .from(MEASURABLE_RATING_REPLACEMENT)
                .leftJoin(APPLICATION)
                .on(MEASURABLE_RATING_REPLACEMENT.ENTITY_ID.eq(APPLICATION.ID)
                        .and(MEASURABLE_RATING_REPLACEMENT.ENTITY_KIND.eq(EntityKind.APPLICATION.name())))
                .where(MEASURABLE_RATING_REPLACEMENT.DECOMMISSION_ID.eq(decommissionId))
                .fetchSet(TO_DOMAIN_MAPPER);
    }


    public Set<MeasurableRatingReplacement> fetchByEntityRef(EntityReference ref){
        return dsl
                .select()
                .from(MEASURABLE_RATING_REPLACEMENT)
                .innerJoin(MEASURABLE_RATING_PLANNED_DECOMMISSION)
                .on(MEASURABLE_RATING_PLANNED_DECOMMISSION.ID.eq(MEASURABLE_RATING_REPLACEMENT.DECOMMISSION_ID))
                .leftJoin(APPLICATION)
                .on(MEASURABLE_RATING_REPLACEMENT.ENTITY_ID.eq(APPLICATION.ID)
                        .and(MEASURABLE_RATING_REPLACEMENT.ENTITY_KIND.eq(EntityKind.APPLICATION.name())))
                .where(MEASURABLE_RATING_PLANNED_DECOMMISSION.ENTITY_ID.eq(ref.id())
                        .and(MEASURABLE_RATING_PLANNED_DECOMMISSION.ENTITY_KIND.eq(ref.kind().name())))
                .fetchSet(TO_DOMAIN_MAPPER);
    }


    public Boolean save(long decommId, EntityReference entityReference, LocalDate commissionDate, String username) {

        Condition condition = MEASURABLE_RATING_REPLACEMENT.DECOMMISSION_ID.eq(decommId)
                .and(MEASURABLE_RATING_REPLACEMENT.ENTITY_ID.eq(entityReference.id())
                        .and(MEASURABLE_RATING_REPLACEMENT.ENTITY_KIND.eq(entityReference.kind().name())));

        boolean isUpdate = dsl.fetchExists(DSL
                .selectFrom(MEASURABLE_RATING_REPLACEMENT)
                .where(condition));

        if (isUpdate) {
            return dsl
                    .update(MEASURABLE_RATING_REPLACEMENT)
                    .set(MEASURABLE_RATING_REPLACEMENT.PLANNED_COMMISSION_DATE, DateTimeUtilities.toSqlDate(commissionDate))
                    .set(MEASURABLE_RATING_REPLACEMENT.UPDATED_BY, username)
                    .set(MEASURABLE_RATING_REPLACEMENT.UPDATED_AT, DateTimeUtilities.nowUtcTimestamp())
                    .execute() == 1;
        } else {
            MeasurableRatingReplacementRecord replacementRecord = dsl.newRecord(MEASURABLE_RATING_REPLACEMENT);

            replacementRecord.setDecommissionId(decommId);
            replacementRecord.setEntityId(entityReference.id());
            replacementRecord.setEntityKind(entityReference.kind().name());
            replacementRecord.setCreatedAt(DateTimeUtilities.nowUtcTimestamp());
            replacementRecord.setCreatedBy(username);
            replacementRecord.setUpdatedAt(DateTimeUtilities.nowUtcTimestamp());
            replacementRecord.setUpdatedBy(username);
            replacementRecord.setPlannedCommissionDate(toSqlDate(commissionDate));

            return replacementRecord.insert() == 1;
        }
    }


    public boolean remove(long decommId, long replacementId) {
        return dsl
                .deleteFrom(MEASURABLE_RATING_REPLACEMENT)
                .where(MEASURABLE_RATING_REPLACEMENT.DECOMMISSION_ID.eq(decommId))
                .and(MEASURABLE_RATING_REPLACEMENT.ID.eq(replacementId))
                .execute() == 1;

    }
}