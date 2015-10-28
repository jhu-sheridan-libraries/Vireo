package org.tdl.vireo.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.UUID;

import javax.validation.constraints.AssertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.tdl.vireo.Application;
import org.tdl.vireo.annotations.Order;
import org.tdl.vireo.enums.Role;
import org.tdl.vireo.model.repo.ActionLogRepo;
import org.tdl.vireo.model.repo.AttachmentRepo;
import org.tdl.vireo.model.repo.CustomActionDefinitionRepo;
import org.tdl.vireo.model.repo.CustomActionValueRepo;
import org.tdl.vireo.model.repo.EmbargoTypeRepo;
import org.tdl.vireo.model.repo.NamedSearchFilterRepo;
import org.tdl.vireo.model.repo.OrganizationCategoryRepo;
import org.tdl.vireo.model.repo.OrganizationRepo;
import org.tdl.vireo.model.repo.SubmissionRepo;
import org.tdl.vireo.model.repo.SubmissionStateRepo;
import org.tdl.vireo.model.repo.UserRepo;
import org.tdl.vireo.runner.OrderedRunner;

@RunWith(OrderedRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class NamedSearchFilterTest extends AbstractEntityTest{
	
	
	@Before
    public void setUp() {
		assertEquals("SearchFilter Repo is not empty", 0,namedSearchFilterRepo.count());
		
		creator = userRepo.create(TEST_USER_EMAIL, TEST_USER_FIRSTNAME, TEST_USER_LASTNAME, TEST_USER_ROLE);
		
		includedSubmitter = userRepo.create(TEST_INCLUDED_SUBMITTER_EMAIL, TEST_INCLUDED_SUBMITTER_FIRSTNAME, TEST_INCLUDED_SUBMITTER_LASTNAME, TEST_SUBMITTER_ROLE);
		includedSubmissionState = submissionStateRepo.create(TEST_INCLUDED_SUBMISSION_STATE_NAME, TEST_SUBMISSION_STATE_ARCHIVED, TEST_SUBMISSION_STATE_PUBLISHABLE, TEST_SUBMISSION_STATE_DELETABLE, TEST_SUBMISSION_STATE_EDITABLE_BY_REVIEWER, TEST_SUBMISSION_STATE_EDITABLE_BY_STUDENT, TEST_SUBMISSION_STATE_ACTIVE);
		includedSubmission = submissionRepo.create(includedSubmitter, includedSubmissionState);
		
		
		excludedSubmitter = userRepo.create(TEST_EXCLUDED_SUBMITTER_EMAIL, TEST_EXCLUDED_SUBMITTER_FIRSTNAME, TEST_EXCLUDED_SUBMITTER_LASTNAME, TEST_SUBMITTER_ROLE);
		excludedSubmissionState = submissionStateRepo.create(TEST_EXCLUDED_SUBMISSION_STATE_NAME, TEST_SUBMISSION_STATE_ARCHIVED, TEST_SUBMISSION_STATE_PUBLISHABLE, TEST_SUBMISSION_STATE_DELETABLE, TEST_SUBMISSION_STATE_EDITABLE_BY_REVIEWER, TEST_SUBMISSION_STATE_EDITABLE_BY_STUDENT, TEST_SUBMISSION_STATE_ACTIVE);
		excludedSubmission = submissionRepo.create(excludedSubmitter, excludedSubmissionState);
		assertEquals("The submission does not exist!", 2, submissionRepo.count());
		
		attachment = attachmentRepo.create(TEST_ATTACHMENT_NAME,TEST_UUID );
		assertEquals("The attachment repository is not empty!", 1, attachmentRepo.count());
		
		includedActionLog = actionLogRepo.create(includedSubmission, includedSubmissionState, includedSubmitter, TEST_ACTION_LOG_ACTION_DATE, attachment,TEST_ACTION_LOG_ENTRY, TEST_ACTION_LOG_FLAG);      	
		excludedActionLog = actionLogRepo.create(excludedSubmission, excludedSubmissionState, excludedSubmitter, TEST_ACTION_LOG_ACTION_DATE, attachment,TEST_ACTION_LOG_ENTRY, TEST_ACTION_LOG_FLAG);
		assertEquals("The actionLog repository is not empty!", 2, actionLogRepo.count());
		
		submissionState = submissionStateRepo.create(TEST_SUBMISSION_STATE_NAME, TEST_SUBMISSION_STATE_ARCHIVED, TEST_SUBMISSION_STATE_PUBLISHABLE, TEST_SUBMISSION_STATE_DELETABLE, TEST_SUBMISSION_STATE_EDITABLE_BY_REVIEWER, TEST_SUBMISSION_STATE_EDITABLE_BY_STUDENT, TEST_SUBMISSION_STATE_ACTIVE);
		
		embargoType = embargoTypeRepo.create(TEST_EMBARGO_NAME, TEST_EMBARGO_DESCRIPTION, TEST_EMBARGO_DURATION);
		assertEquals("The embargoType repository is not empty!", 1, embargoTypeRepo.count());
		
		assignee = userRepo.create(TEST_ASSIGNEE_EMAIL, TEST_ASSIGNEE_FIRSTNAME, TEST_ASSIGNEE_LASTNAME, TEST_ASSIGNEE_ROLE);
		assertEquals("The assignee is not in the user repo",4,userRepo.count());
		
       	customActionDefinition = customActionDefinitionRepo.create(TEST_CUSTOM_ACTION_DEFINITION_LABEL, TEST_CUSTOM_ACTION_DEFINITION_VISIBLE_BY_STUDENT);
       	customActionValue = customActionValueRepo.create(includedSubmission, customActionDefinition, TEST_CUSTOM_ACTION_VALUE);
       	assertEquals("The customActionValue Repo is empty",1,customActionValueRepo.count());
       	organizationCategory = organizationCategoryRepo.create(TEST_ORGANIZATION_CATEGORY_NAME, TEST_ORGANIZATION_CATEGORY_LEVEL);
       	assertEquals("The category does not exist!", 1, organizationCategoryRepo.count());
       	organization = organizationRepo.create(TEST_ORGANIZATION_NAME, organizationCategory);
		assertEquals("The organization Repo is empty",1,organizationRepo.count());
	}
	
	@Override
    @Transactional
    public void testCreate() {
		NamedSearchFilter namedSearchFilter = namedSearchFilterRepo.create(creator, TEST_NAMED_SEARCH_FILTER_NAME);
		assertEquals("SearchFilter was not created in the repo", 1,namedSearchFilterRepo.count());
		
		assertEquals("Saved Filter does not have the correct user", true, namedSearchFilter.getCreator().equals(creator));
		assertEquals("Saved Filter does not have the correct name",TEST_NAMED_SEARCH_FILTER_NAME,namedSearchFilter.getName());
		
		namedSearchFilter.addIncludedSubmission(includedSubmission);			
		namedSearchFilter.addExcludedSubmission(excludedSubmission);		
		namedSearchFilter.addIncludedActionLog(includedActionLog);		
		namedSearchFilter.addExcludedActionLog(excludedActionLog);
		namedSearchFilter.addSubmissionState(submissionState);		
		namedSearchFilter.addEmbargoType(embargoType);		
		namedSearchFilter.addAssignee(assignee);		
		namedSearchFilter.addCustomActionValue(customActionValue);		
		namedSearchFilter.addOrganization(organization);
		
		namedSearchFilter = namedSearchFilterRepo.save(namedSearchFilter);
		
		assertEquals("Filter does not have includedSubmission ",true, namedSearchFilter.getIncludedSubmissions().contains(includedSubmission));
		assertEquals("Filter does not have excludedSubmission ",true, namedSearchFilter.getExcludedSubmissions().contains(excludedSubmission));
		assertEquals("Filter does not have includedActionLog ",true, namedSearchFilter.getIncludedActionLogs().contains(includedActionLog));
		assertEquals("Filter does not have submission state",true,namedSearchFilter.getSubmissionStates().contains(submissionState));
		assertEquals("Filter does not have the embargo ",true,namedSearchFilter.getEmbargoTypes().contains(embargoType));
		assertEquals("Filter does not contain the assignee",true,namedSearchFilter.getAssignees().contains(assignee));
		assertEquals("Filter does not have CustomActionValue ",true, namedSearchFilter.getCustomActionValues().contains(customActionValue));
		assertEquals("Filter does not have includedSubmission ",true, namedSearchFilter.getOrganizations().contains(organization));
		
		
	}
	
	@Override
	@Order(value = 2)
	public void testDuplication() {
		namedSearchFilterRepo.create(creator, TEST_NAMED_SEARCH_FILTER_NAME);
		try{
		namedSearchFilterRepo.create(creator, TEST_NAMED_SEARCH_FILTER_NAME);
		} catch(DataIntegrityViolationException e) {
		/* SUCCESS*/
		}
		assertEquals("SearchFilter was not created in the repo", 1,namedSearchFilterRepo.count());
	}
	
	@Override
    public void testFind() {
		// TODO
	}
	
	@Override
    public void testDelete() { 
		NamedSearchFilter namedSearchFilter = namedSearchFilterRepo.create(creator, TEST_NAMED_SEARCH_FILTER_NAME);
		assertEquals("SearchFilter was not created in the repo", 1,namedSearchFilterRepo.count());
		namedSearchFilterRepo.delete(namedSearchFilter);
		assertEquals("SearchFilter was not deleted in the repo", 0,namedSearchFilterRepo.count());
	}
	
	@Override
    @Transactional
    public void testCascade() {
		NamedSearchFilter namedSearchFilter = namedSearchFilterRepo.create(creator, TEST_NAMED_SEARCH_FILTER_NAME);
		namedSearchFilter.addIncludedSubmission(includedSubmission);			
		namedSearchFilter.addExcludedSubmission(excludedSubmission);		
		namedSearchFilter.addIncludedActionLog(includedActionLog);		
		namedSearchFilter.addExcludedActionLog(excludedActionLog);
		namedSearchFilter.addSubmissionState(submissionState);		
		namedSearchFilter.addEmbargoType(embargoType);		
		namedSearchFilter.addAssignee(assignee);		
		namedSearchFilter.addCustomActionValue(customActionValue);		
		namedSearchFilter.addOrganization(organization);
		
		namedSearchFilter = namedSearchFilterRepo.save(namedSearchFilter);
		
		namedSearchFilterRepo.delete(namedSearchFilter);
		
		namedSearchFilter = namedSearchFilterRepo.findOne(namedSearchFilter.getId());
		assertEquals("namedSearchFilter was not deleted",null, namedSearchFilter);
		assertEquals("The submissions were deleted",2,submissionRepo.count());
		assertEquals("The action logs were deleted",2,actionLogRepo.count());
		assertEquals("The submissionState was deleted",3,submissionStateRepo.count());
		assertEquals("The embargoType was deleted",1,embargoTypeRepo.count());
		assertEquals("The assignee was deleted",4,userRepo.count());
		assertEquals("The organization was deleted",1,organizationRepo.count());
	}
	


	@After
    public void cleanUp() {
		namedSearchFilterRepo.deleteAll();		
		embargoTypeRepo.deleteAll();		
		organizationRepo.deleteAll();
		organizationCategoryRepo.deleteAll();
		actionLogRepo.deleteAll();		
		attachmentRepo.deleteAll();		
		customActionValueRepo.deleteAll();
		
		submissionRepo.deleteAll();submissionStateRepo.deleteAll();
		userRepo.deleteAll();
		customActionDefinitionRepo.deleteAll();
	}
	
}