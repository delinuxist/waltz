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

package org.finos.waltz.service.survey;


import org.finos.waltz.common.DateTimeUtilities;
import org.finos.waltz.data.person.PersonDao;
import org.finos.waltz.data.survey.*;
import org.finos.waltz.model.*;
import org.finos.waltz.model.changelog.ImmutableChangeLog;
import org.finos.waltz.model.person.Person;
import org.finos.waltz.model.survey.*;
import org.finos.waltz.model.user.SystemRole;
import org.finos.waltz.model.utils.IdUtilities;
import org.finos.waltz.service.changelog.ChangeLogService;
import org.finos.waltz.service.user.UserRoleService;
import org.jooq.Record1;
import org.jooq.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.Checks.checkTrue;
import static org.finos.waltz.common.CollectionUtilities.find;
import static org.finos.waltz.model.survey.SurveyInstanceStateMachineFactory.simple;

@Service
public class SurveyInstanceService {

    private final ChangeLogService changeLogService;
    private final PersonDao personDao;
    private final SurveyInstanceDao surveyInstanceDao;
    private final SurveyInstanceRecipientDao surveyInstanceRecipientDao;
    private final SurveyInstanceOwnerDao surveyInstanceOwnerDao;
    private final SurveyQuestionResponseDao surveyQuestionResponseDao;
    private final SurveyInstanceIdSelectorFactory surveyInstanceIdSelectorFactory = new SurveyInstanceIdSelectorFactory();
    private final SurveyRunDao surveyRunDao;
    private final UserRoleService userRoleService;
    private final SurveyQuestionService surveyQuestionService;


    @Autowired
    public SurveyInstanceService(ChangeLogService changeLogService,
                                 PersonDao personDao,
                                 SurveyInstanceDao surveyInstanceDao,
                                 SurveyInstanceRecipientDao surveyInstanceRecipientDao,
                                 SurveyInstanceOwnerDao surveyInstanceOwnerDao,
                                 SurveyQuestionResponseDao surveyQuestionResponseDao,
                                 SurveyRunDao surveyRunDao,
                                 UserRoleService userRoleService,
                                 SurveyQuestionService surveyQuestionService) {

        checkNotNull(changeLogService, "changeLogService cannot be null");
        checkNotNull(personDao, "personDao cannot be null");
        checkNotNull(surveyInstanceDao, "surveyInstanceDao cannot be null");
        checkNotNull(surveyInstanceRecipientDao, "surveyInstanceRecipientDao cannot be null");
        checkNotNull(surveyInstanceOwnerDao, "surveyInstanceOwnerDao cannot be null");
        checkNotNull(surveyQuestionResponseDao, "surveyQuestionResponseDao cannot be null");
        checkNotNull(surveyRunDao, "surveyRunDao cannot be null");
        checkNotNull(userRoleService, "userRoleService cannot be null");
        checkNotNull(surveyQuestionService, "surveyQuestionService cannot be null");

        this.changeLogService = changeLogService;
        this.personDao = personDao;
        this.surveyInstanceDao = surveyInstanceDao;
        this.surveyInstanceRecipientDao = surveyInstanceRecipientDao;
        this.surveyInstanceOwnerDao = surveyInstanceOwnerDao;
        this.surveyQuestionResponseDao = surveyQuestionResponseDao;
        this.surveyRunDao = surveyRunDao;
        this.userRoleService = userRoleService;
        this.surveyQuestionService = surveyQuestionService;
    }


    public SurveyInstance getById(long instanceId) {
        return surveyInstanceDao.getById(instanceId);
    }


    public Set<SurveyInstance> findForRecipient(String userName) {
        checkNotNull(userName, "userName cannot be null");

        Person person = getPersonByUsername(userName);

        return surveyInstanceDao.findForRecipient(person.id().get());
    }


    public Set<SurveyInstance> findForRecipient(Long personId) {
        checkNotNull(personId, "personId cannot be null");

        return surveyInstanceDao.findForRecipient(personId);
    }


    public Set<SurveyInstance> findForSurveyRun(long surveyRunId) {
        return surveyInstanceDao.findForSurveyRun(surveyRunId);
    }


    public List<SurveyInstanceQuestionResponse> findResponses(long instanceId) {
        return surveyQuestionResponseDao.findForInstance(instanceId);
    }


    public List<SurveyInstanceRecipient> findRecipients(long instanceId) {
        return surveyInstanceRecipientDao.findForSurveyInstance(instanceId);
    }


    public List<SurveyInstanceOwner> findOwners(long instanceId) {
        return surveyInstanceOwnerDao.findForSurveyInstance(instanceId);
    }


    public boolean saveResponse(String userName,
                                long instanceId,
                                SurveyQuestionResponse questionResponse) {

        checkNotNull(userName, "userName cannot be null");
        checkNotNull(questionResponse, "questionResponse cannot be null");

        Person person = checkPersonIsRecipient(userName, instanceId);

        SurveyInstance surveyInstance = surveyInstanceDao.getById(instanceId);
        checkTrue(surveyInstance.status() == SurveyInstanceStatus.NOT_STARTED
                        || surveyInstance.status() == SurveyInstanceStatus.IN_PROGRESS
                        || surveyInstance.status() == SurveyInstanceStatus.REJECTED,
                "Survey instance cannot be updated, current status: " + surveyInstance.status());

        SurveyInstanceQuestionResponse instanceQuestionResponse = ImmutableSurveyInstanceQuestionResponse.builder()
                .surveyInstanceId(instanceId)
                .personId(person.id().get())
                .lastUpdatedAt(DateTimeUtilities.nowUtc())
                .questionResponse(questionResponse)
                .build();

        surveyQuestionResponseDao.saveResponse(instanceQuestionResponse);

        return true;
    }


    public Person checkPersonIsOwnerOrAdmin(String userName, long instanceId) {
        Person person = getPersonByUsername(userName);
        checkTrue(
                isAdmin(userName) || isOwner(instanceId, person) || hasOwningRole(instanceId, person),
                "Permission denied");
        return person;
    }


    public Person checkPersonIsRecipient(String userName, long instanceId) {
        Person person = getPersonByUsername(userName);
        boolean isPersonInstanceRecipient = surveyInstanceRecipientDao.isPersonInstanceRecipient(
                person.id().get(),
                instanceId);
        checkTrue(isPersonInstanceRecipient, "Permission denied");
        return person;
    }


    public SurveyInstanceStatus updateStatus(String userName, long instanceId, SurveyInstanceStatusChangeCommand command) {
        checkNotNull(command, "command cannot be null");

        SurveyInstance surveyInstance = surveyInstanceDao.getById(instanceId);
        checkTrue(surveyInstance.originalInstanceId() == null, "You cannot change the status of Approved/Rejected surveys");

        SurveyInstancePermissions permissions = getPermissions(userName, instanceId);
        SurveyInstanceStatus newStatus = simple(surveyInstance.status()).process(command.action(), permissions, surveyInstance);

        int nbupdates = 0;
        switch (command.action()) {
            case APPROVING:
                nbupdates = surveyInstanceDao.markApproved(instanceId, userName);
                break;
            case REOPENING:
                // if survey is being sent back, store current responses as a version
                long versionedInstanceId = surveyInstanceDao.createPreviousVersion(surveyInstance);
                surveyQuestionResponseDao.cloneResponses(surveyInstance.id().get(), versionedInstanceId);
                surveyInstanceDao.clearApproved(instanceId);
                // intended drop thru'
            default:
                nbupdates = surveyInstanceDao.updateStatus(instanceId, newStatus);
        }

        if (nbupdates > 0) {
            if (newStatus == SurveyInstanceStatus.COMPLETED) {
                surveyInstanceDao.updateSubmitted(instanceId, userName);
                removeUnnecessaryResponses(instanceId);
            }

            changeLogService.write(
                    ImmutableChangeLog.builder()
                            .operation(Operation.UPDATE)
                            .userId(userName)
                            .parentReference(EntityReference.mkRef(EntityKind.SURVEY_INSTANCE, instanceId))
                            .message("Survey Instance: status changed to " + newStatus + " with action " + command.action()
                                    + command.reason().map(r -> ", [Reason]: " + r).orElse(""))
                            .build());
        }

        return newStatus;
    }


    protected int removeUnnecessaryResponses(long instanceId) {
        List<SurveyQuestion> availableQuestions = surveyQuestionService.findForSurveyInstance(instanceId);
        List<SurveyInstanceQuestionResponse> questionResponses = surveyQuestionResponseDao.findForInstance(instanceId);
        Set<Long> availableQuestionIds = IdUtilities.toIds(availableQuestions);

        List<SurveyInstanceQuestionResponse> toRemove = new ArrayList<>();
        for (SurveyInstanceQuestionResponse qr : questionResponses) {
            if (!availableQuestionIds.contains(qr.questionResponse().questionId())) {
                toRemove.add(qr);
            }
        }

        if (!toRemove.isEmpty()) {
            return surveyQuestionResponseDao.deletePreviousResponse(toRemove);
        } else {
            return 0;
        }
    }


    public int updateDueDate(String userName, long instanceId, DateChangeCommand command) {
        checkNotNull(userName, "userName cannot be null");
        checkNotNull(command, "command cannot be null");

        checkPersonIsOwnerOrAdmin(userName, instanceId);
        LocalDate newDueDate = command.newDateVal().orElse(null);

        checkNotNull(newDueDate, "newDueDate cannot be null");

        int result = surveyInstanceDao.updateDueDate(instanceId, newDueDate);

        changeLogService.write(
                ImmutableChangeLog.builder()
                        .operation(Operation.UPDATE)
                        .userId(userName)
                        .parentReference(EntityReference.mkRef(EntityKind.SURVEY_INSTANCE, instanceId))
                        .message("Survey Instance: due date changed to " + newDueDate)
                        .build());

        return result;
    }


    public List<SurveyInstance> findBySurveyInstanceIdSelector(IdSelectionOptions idSelectionOptions) {
        checkNotNull(idSelectionOptions, "idSelectionOptions cannot be null");

        Select<Record1<Long>> selector = surveyInstanceIdSelectorFactory.apply(idSelectionOptions);

        return surveyInstanceDao.findBySurveyInstanceIdSelector(selector);
    }


    public List<SurveyInstance> findPreviousVersionsForInstance(long instanceId) {
        return surveyInstanceDao.findPreviousVersionsForInstance(instanceId);
    }


    public long addRecipient(String username, SurveyInstanceRecipientCreateCommand command) {
        checkNotNull(command, "command cannot be null");
        checkPersonIsOwnerOrAdmin(username, command.surveyInstanceId());
        long rc = surveyInstanceRecipientDao.create(command);

        logRecipientChange(
                username,
                command.surveyInstanceId(),
                command.personId(),
                Operation.ADD,
                "Survey Instance: Added %s as a recipient");

        return rc;
    }


    public boolean updateRecipient(String username, SurveyInstanceRecipientUpdateCommand command) {
        checkNotNull(command, "command cannot be null");
        checkPersonIsOwnerOrAdmin(username, command.surveyInstanceId());

        boolean delete = surveyInstanceRecipientDao.delete(command.instanceRecipientId());
        long id = surveyInstanceRecipientDao.create(ImmutableSurveyInstanceRecipientCreateCommand
                .builder()
                .personId(command.personId())
                .surveyInstanceId(command.surveyInstanceId())
                .build());

        logRecipientChange(
                username,
                command.surveyInstanceId(),
                command.personId(),
                Operation.UPDATE,
                "Survey Instance: Set %s as a recipient");

        return delete && id > 0;
    }


    public boolean deleteRecipient(String username, long surveyInstanceId, long recipientId) {
        checkPersonIsOwnerOrAdmin(username, surveyInstanceId);
        Long personId = surveyInstanceRecipientDao.getPersonIdForRecipientId(recipientId);
        boolean rc = surveyInstanceRecipientDao.delete(recipientId);

        logRecipientChange(
                username,
                surveyInstanceId,
                personId,
                Operation.REMOVE,
                "Survey Instance: Removed %s as a recipient");

        return rc;
    }


    private Person getPersonByUsername(String userName) {
        Person person = personDao.getActiveByUserEmail(userName);
        checkNotNull(person, "userName %s cannot be resolved", userName);
        return person;
    }


    private Person getPersonById(Long id) {
        Person person = personDao.getById(id);
        checkNotNull(person, "Person with id %d cannot be resolved", id);
        return person;
    }


    private boolean isOwner(long instanceId, Person person) {
        return person.id()
                .map(pId -> surveyInstanceOwnerDao.isPersonInstanceOwner(pId, instanceId))
                .orElse(false);
    }


    private boolean isAdmin(String userName) {
        return userRoleService.hasRole(userName, SystemRole.SURVEY_ADMIN);
    }


    private boolean hasOwningRole(long instanceId, Person person) {
        SurveyInstance instance = surveyInstanceDao.getById(instanceId);
        return userRoleService.hasRole(person.email(), instance.owningRole());
    }


    private void logRecipientChange(String username, long instanceId, long personId, Operation op, String msg) {
        Person recipient = getPersonById(personId);

        changeLogService.write(
                ImmutableChangeLog.builder()
                        .operation(op)
                        .userId(username)
                        .parentReference(EntityReference.mkRef(EntityKind.SURVEY_INSTANCE, instanceId))
                        .childKind(EntityKind.PERSON)
                        .message(String.format(msg, recipient.name()))
                        .build());
    }


    public List<SurveyInstanceAction> findPossibleActionsForInstance(String userName, long instanceId) {
        SurveyInstance surveyInstance = surveyInstanceDao.getById(instanceId);
        SurveyInstancePermissions permissions = getPermissions(userName, instanceId);
        SurveyInstanceStateMachine stateMachine = simple(surveyInstance.status());
        return stateMachine.nextPossibleActions(permissions, surveyInstance);
    }


    public SurveyInstancePermissions getPermissions(String userName, Long instanceId) {
        Person person = personDao.getActiveByUserEmail(userName);
        SurveyInstance instance = surveyInstanceDao.getById(instanceId);
        SurveyRun run = surveyRunDao.getById(instance.surveyRunId());

        boolean isAdmin = userRoleService.hasRole(userName, SystemRole.SURVEY_ADMIN);
        boolean isParticipant = surveyInstanceRecipientDao.isPersonInstanceRecipient(person.id().get(), instanceId);
        boolean isOwner = person.id()
                .map(pid -> surveyInstanceOwnerDao.isPersonInstanceOwner(instanceId, pid) || Objects.equals(run.ownerId(), pid))
                .orElse(false);
        boolean hasOwningRole = userRoleService.hasRole(person.email(), instance.owningRole());
        boolean isLatest = instance.originalInstanceId() == null;

        return ImmutableSurveyInstancePermissions.builder()
                .isAdmin(isAdmin)
                .isParticipant(isParticipant)
                .isOwner(isOwner)
                .hasOwnerRole(hasOwningRole)
                .isMetaEdit(isLatest && (isAdmin || isOwner || hasOwningRole))
                .build();
    }


    public boolean reportProblemWithQuestionResponse(Long instanceId,
                                                     Long questionId,
                                                     String message,
                                                     String username) {

        List<SurveyQuestion> surveyQuestions = surveyQuestionService
                .findForSurveyInstance(instanceId);

        return find(d -> d.id().get().equals(questionId), surveyQuestions)
                .map(q -> {
                    changeLogService.write(
                            ImmutableChangeLog.builder()
                                    .operation(Operation.UPDATE)
                                    .userId(username)
                                    .parentReference(EntityReference.mkRef(EntityKind.SURVEY_INSTANCE, instanceId))
                                    .childKind(EntityKind.SURVEY_QUESTION)
                                    .message(String.format("Question [%s]: %s", q.questionText(), message))
                                    .build());
                    return true;
                })
                .orElse(false);
    }
}
