package org.openmrs.module.shr.cdahandler.processor.entry.impl.ihe.pcc;

import java.util.Arrays;
import java.util.List;

import org.marc.everest.datatypes.II;
import org.marc.everest.datatypes.generic.CS;
import org.marc.everest.datatypes.generic.CV;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Act;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.ClinicalStatement;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.EntryRelationship;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Observation;
import org.marc.everest.rmim.uv.cdar2.vocabulary.ActStatus;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Concept;
import org.openmrs.Allergy;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.cdahandler.CdaHandlerConstants;
import org.openmrs.module.shr.cdahandler.api.CdaImportService;
import org.openmrs.module.shr.cdahandler.exception.DocumentImportException;
import org.openmrs.module.shr.cdahandler.obs.ExtendedObs;
import org.openmrs.module.shr.cdahandler.processor.annotation.ProcessTemplates;
import org.openmrs.module.shr.cdahandler.processor.entry.EntryProcessor;
import org.openmrs.module.shr.cdahandler.processor.factory.impl.EntryProcessorFactory;

/**
 * Represents a processor for the Allergis and Intolerances Entry
 * 
 * See: PCC TF-2: 6.3.4.13
 */
@ProcessTemplates(templateIds = {
		CdaHandlerConstants.ENT_TEMPLATE_ALLERGIES_AND_INTOLERANCES_CONCERN
})
public class AllergiesAndIntolerancesConcernEntryProcessor extends ConcernEntryProcessor {
			
	/**
	 * Get expected entries
	 * @see org.openmrs.module.shr.cdahandler.processor.entry.impl.ihe.pcc.ConcernEntryProcessor#getExpectedEntries()
	 */
	@Override
    protected List<String> getExpectedEntryRelationships() {
		return Arrays.asList(CdaHandlerConstants.ENT_TEMPLATE_ALLERGY_AND_INTOLERANCE_OBSERVATION);
    }

	/**
	 * Get the template name
	 */
	@Override
    public String getTemplateName() {
	    return "Allergies and Intolerances Concern";
    }

	/**
	 * Parse the act contents into an allergy
	 * @see org.openmrs.module.shr.cdahandler.processor.entry.impl.ihe.pcc.ConcernEntryProcessor#parseActContents(org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Act, org.openmrs.Obs)
	 */
	@Override
    protected void parseActContents(Act act, ClinicalStatement statement) throws DocumentImportException {
		
		// Get processor factory
		EntryProcessor processor = EntryProcessorFactory.getInstance().createProcessor(statement);
		processor.setContext(this.getContext());
		
		BaseOpenmrsData processedData = processor.process(statement);

		// Not an allergy so process like normal
		if(!statement.getTemplateId().contains(new II(CdaHandlerConstants.ENT_TEMPLATE_ALLERGY_AND_INTOLERANCE_OBSERVATION)))
			return;

		// Get Some information that assists in processing
		ExtendedObs obs = Context.getService(CdaImportService.class).getExtendedObs(processedData.getId());
		Observation observation = (Observation)statement;
		
		// We don't track the allergy to an obs if we can help it..
		Allergy res = super.createItem(act, obs, Allergy.class);
		this.updateItem(res,act,obs);
		res.setActiveListType(Allergy.ACTIVE_LIST_TYPE);
		
		// Populate allergy contents ... What is the allergy type?
		if(observation.getCode().getCode().equals("FALG") ||
				observation.getCode().getCode().equals("FINT") ||
				observation.getCode().getCode().equals("FNAINT"))
			res.setAllergyType(AllergyType.FOOD);
		else if(observation.getCode().getCode().equals("DALG") ||
				observation.getCode().getCode().equals("DINT") ||
				observation.getCode().getCode().equals("DNAINT"))
			res.setAllergyType(AllergyType.DRUG);
		else if(observation.getCode().getCode().equals("EALG") ||
				observation.getCode().getCode().equals("EINT") ||
				observation.getCode().getCode().equals("ENAINT"))
			res.setAllergyType(AllergyType.ENVIRONMENT);
		else 
			res.setAllergyType(AllergyType.OTHER);
		
		// Now we have to dive into the allergen a little bit
		if(observation.getParticipant().size() == 1 &&
				observation.getParticipant().get(0).getParticipantRole() != null &&
				observation.getParticipant().get(0).getParticipantRole().getPlayingEntityChoiceIfPlayingEntity() != null &&
				observation.getParticipant().get(0).getParticipantRole().getPlayingEntityChoiceIfPlayingEntity().getCode() != null)
			res.setAllergen(this.m_conceptUtil.getOrCreateConcept(observation.getParticipant().get(0).getParticipantRole().getPlayingEntityChoiceIfPlayingEntity().getCode()));
		else if(obs.getValueCoded() != null)
			res.setAllergen(obs.getValueCoded());
		else
			throw new DocumentImportException("Allergen must be of type CD");
				
		// Set severity (if possible)
		List<EntryRelationship> severityRelationship = this.findEntryRelationship(observation, CdaHandlerConstants.ENT_TEMPLATE_SEVERITY_OBSERVATION);
		if(severityRelationship.size() == 1) // Only if there is one
		{
			// Get the severity code
			Observation severityObservation = severityRelationship.get(0).getClinicalStatementIfObservation();
			CS<String> severityObservationValue = (CS<String>)severityObservation.getValue();
			if(severityObservationValue.getCode().equals("L"))
				res.setSeverity(AllergySeverity.MILD);
			if(severityObservationValue.getCode().equals("M"))
				res.setSeverity(AllergySeverity.MODERATE);			
			if(severityObservationValue.getCode().equals("H"))
					res.setSeverity(AllergySeverity.SEVERE);
		}
		else if(observation.getCode().getCode().endsWith("INT"))
			res.setSeverity(AllergySeverity.INTOLERANCE);
		else
			res.setSeverity(AllergySeverity.UNKNOWN);
		
		// Are there manifestations (reactions)?
		List<EntryRelationship> manifestationRelationship = this.findEntryRelationship(observation, CdaHandlerConstants.ENT_TEMPLATE_MANIFESTATION_RELATION);
		if(manifestationRelationship.size() == 1) // Only if there is one
		{
			Observation manifestationObservation = manifestationRelationship.get(0).getClinicalStatementIfObservation();
			// Get the concept
			Concept reaction = this.m_conceptUtil.getOrCreateConcept((CV)manifestationObservation.getValue());
			res.setReaction(reaction);
		}
		else if(manifestationRelationship.size() > 1)
			throw new DocumentImportException("Allergy importer only supports one manifestation relationship");
		
		
		return res;
		if(listItem != null)
			Context.getActiveListService().saveActiveListItem(listItem);
    }

	/**
	 * Parse the contents of the Act to a Problem
	 * @throws DocumentImportException
	 */
	protected void updateItem(Allergy res, Act act, ExtendedObs obs){
		// Effective time?
		if(act.getEffectiveTime() != null)
		{
			// Can only update start date if currentStatus is New or Active
			if(act.getEffectiveTime().getLow() != null && !act.getEffectiveTime().getLow().isNull())
			{
				// Does this report it to be prior to the currently known start date?
				if(res.getStartDate() == null || act.getEffectiveTime().getLow().getDateValue().getTime().compareTo(res.getStartDate()) < 0)
				{
					// Void and previous version
					if(res.getStartObs() != null)
					{
						Context.getObsService().voidObs(res.getStartObs(), "Replaced");
						obs.setPreviousVersion(res.getStartObs());
					}
					res.setStartObs(obs);
					res.setStartDate(act.getEffectiveTime().getLow().getDateValue().getTime());
				}
			}
			if(act.getEffectiveTime().getHigh() != null && !act.getEffectiveTime().getHigh().isNull())
			{
				// Does this report it to be after the currently known end date?
				if(res.getEndDate() == null || act.getEffectiveTime().getHigh().getDateValue().getTime().compareTo(res.getEndDate()) > 0)
				{
					// Void and previous version
					if(res.getStopObs() != null)
					{
						Context.getObsService().voidObs(res.getStopObs(), "Replaced");
						obs.setPreviousVersion(res.getStopObs());
					}
					res.setStopObs(obs);
					res.setEndDate(act.getEffectiveTime().getHigh().getDateValue().getTime());
				}
			}
		}
		else if(act.getStatusCode().getCode() != ActStatus.Completed)
			throw new DocumentImportException("Missing effective time of the problem");

		// we have to assign a start or else OMRS will assign one for us!
		if(obs.getObsStartDate() != null && res.getStartDate() == null)
		{
			res.setStartObs(obs);
			res.setStartDate(obs.getObsStartDate());
		}
		if(obs.getObsEndDate() != null && res.getEndDate() == null)
		{
			res.setEndDate(obs.getObsEndDate());
			res.setStopObs(obs);
		}
		// We don't know when it started or stopped
		if(res.getStartDate() == null && res.getEndDate() == null && obs.getObsDatePrecision() == 0)
			res.setStartObs(obs);

		// Void this?
		if(act.getStatusCode().getCode() == ActStatus.Aborted ||
				act.getStatusCode().getCode() == ActStatus.Suspended)
		{
			res.setVoided(true);
			res.setVoidReason(act.getStatusCode().getCode().getCode());
			res.setDateVoided(encounterInfo.getDateCreated());
		}

		// Copy attributes
		res.setPerson(encounterInfo.getPatient());
	}
	
}
