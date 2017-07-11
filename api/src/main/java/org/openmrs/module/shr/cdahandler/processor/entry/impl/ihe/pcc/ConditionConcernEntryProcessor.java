package org.openmrs.module.shr.cdahandler.processor.entry.impl.ihe.pcc;

import java.util.Arrays;
import java.util.List;

import org.marc.everest.datatypes.II;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Act;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.ClinicalStatement;
import org.marc.everest.rmim.uv.cdar2.vocabulary.ActStatus;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Condition;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.conditionslist.ConditionService;
import org.openmrs.module.shr.cdahandler.CdaHandlerConstants;
import org.openmrs.module.shr.cdahandler.exception.DocumentImportException;
import org.openmrs.module.shr.cdahandler.obs.ExtendedObs;
import org.openmrs.module.shr.cdahandler.processor.annotation.ProcessTemplates;
import org.openmrs.module.shr.cdahandler.processor.entry.EntryProcessor;
import org.openmrs.module.shr.cdahandler.processor.factory.impl.EntryProcessorFactory;

/**
 * A concern entry processor
 * 
 * See: PCC TF-2: 6.3.4.12
 */
@ProcessTemplates(
	templateIds = {
			CdaHandlerConstants.ENT_TEMPLATE_PROBLEM_CONCERN
	})
public class ConditionConcernEntryProcessor extends ConcernEntryProcessor {

	/**
	 * Get expected entries which in this case are Problem Entries
	 * @see org.openmrs.module.shr.cdahandler.processor.entry.impl.ihe.pcc.ConcernEntryProcessor#getExpectedEntries()
	 */
	@Override
    protected List<String> getExpectedEntryRelationships() {
		return Arrays.asList(CdaHandlerConstants.ENT_TEMPLATE_PROBLEM_OBSERVATION);
    }

	/**
	 * Get template name
	 * @see org.openmrs.module.shr.cdahandler.processor.entry.impl.ihe.pcc.ConcernEntryProcessor#getTemplateName()
	 */
	@Override
    public String getTemplateName() {
		return "Problem Concern Entry";
    }

	/**
	 * Parse the contents of the Act to a Problem
	 * @throws DocumentImportException 
	 * @see org.openmrs.module.shr.cdahandler.processor.entry.impl.ihe.pcc.ConcernEntryProcessor#parseActContents(org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Act, org.openmrs.Obs)
	 */
	@Override
    protected void parseActContents(Act act, ClinicalStatement statement) throws DocumentImportException {
		EntryProcessor processor = EntryProcessorFactory.getInstance().createProcessor(statement);
		processor.setContext(this.getContext());

		
		BaseOpenmrsData processed = processor.process(statement);
		
		// Not a problem observation so don't create a problem
		
		if(!statement.getTemplateId().contains(new II(CdaHandlerConstants.ENT_TEMPLATE_PROBLEM_OBSERVATION)))
			return;
		
		ExtendedObs obs = (ExtendedObs)processed;
		
		// Correct the act based on the effective time of the entry relationship?
		Condition res = super.createItem(act, obs, Condition.class);
		this.updateItem(res,act,obs);
			
		// Concept
		if(obs.getValueCoded() == null)
			throw new DocumentImportException("Observation for this problem must be of type Coded");
		else if(res.getConcept() == null)
			res.setConcept(obs.getValueCoded());
		
		
		// Status
		if(act.getNegationInd() != null && act.getNegationInd().toBoolean())
			res.setStatus(Condition.Status.INACTIVE);
		else if(act.getStatusCode().getCode().equals(ActStatus.Completed))
			res.setStatus(Condition.Status.HISTORY_OF);

		//save condition
		Context.getService(ConditionService.class).save(res);
    }

	/**
	 * Parse the contents of the Act to a Problem
	 * @throws DocumentImportException
	 */
	protected void updateItem(Condition res,Act act, ExtendedObs obs) throws DocumentImportException {
		// Effective time?
		if(act.getEffectiveTime() != null)
		{
			// Can only update start date if currentStatus is New or Active
			if(act.getEffectiveTime().getLow() != null && !act.getEffectiveTime().getLow().isNull())
			{
				// Does this report it to be prior to the currently known start date?
				if(res.getOnsetDate() == null || act.getEffectiveTime().getLow().getDateValue().getTime().compareTo(res.getOnsetDate()) < 0)
				{
					res.setOnsetDate(act.getEffectiveTime().getLow().getDateValue().getTime());
				}
			}
			if(act.getEffectiveTime().getHigh() != null && !act.getEffectiveTime().getHigh().isNull())
			{
				// Does this report it to be after the currently known end date?
				if(res.getEndDate() == null || act.getEffectiveTime().getHigh().getDateValue().getTime().compareTo(res.getEndDate()) > 0)
				{
					res.setEndDate(act.getEffectiveTime().getHigh().getDateValue().getTime());
				}
			}
		}
		else if(act.getStatusCode().getCode() != ActStatus.Completed)
			throw new DocumentImportException("Missing effective time of the problem");
	}
}
