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
import org.openmrs.*;
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
		if(res.getAllergen()==null) res.setAllergen(new Allergen());

		// Now we have to dive into the allergen a little bit
		if(observation.getParticipant().size() == 1 &&
				observation.getParticipant().get(0).getParticipantRole() != null &&
				observation.getParticipant().get(0).getParticipantRole().getPlayingEntityChoiceIfPlayingEntity() != null &&
				observation.getParticipant().get(0).getParticipantRole().getPlayingEntityChoiceIfPlayingEntity().getCode() != null){
			Concept concept=this.m_conceptUtil.getOrCreateConcept(observation.getParticipant().get(0).getParticipantRole().getPlayingEntityChoiceIfPlayingEntity().getCode());
			res.getAllergen().setCodedAllergen(concept);
		}
		else if(obs.getValueCoded() != null){
			Concept concept=obs.getValueCoded();
			res.getAllergen().setCodedAllergen(concept);
		}
		else
			throw new DocumentImportException("Allergen must be of type CD");

		// Populate allergy contents ... What is the allergy type?
		if(observation.getCode().getCode().equals("FALG") ||
				observation.getCode().getCode().equals("FINT") ||
				observation.getCode().getCode().equals("FNAINT"))
			res.setAllergenType(AllergenType.FOOD);
		else if(observation.getCode().getCode().equals("DALG") ||
				observation.getCode().getCode().equals("DINT") ||
				observation.getCode().getCode().equals("DNAINT"))
			res.setAllergenType(AllergenType.DRUG);
		else if(observation.getCode().getCode().equals("EALG") ||
				observation.getCode().getCode().equals("EINT") ||
				observation.getCode().getCode().equals("ENAINT"))
			res.setAllergenType(AllergenType.ENVIRONMENT);
		else
			res.setAllergenType(AllergenType.OTHER);
				
		// Set severity (if possible)
		List<EntryRelationship> severityRelationship = this.findEntryRelationship(observation, CdaHandlerConstants.ENT_TEMPLATE_SEVERITY_OBSERVATION);
		if(severityRelationship.size() == 1) // Only if there is one
		{
			// Get the severity code
			Observation severityObservation = severityRelationship.get(0).getClinicalStatementIfObservation();
			CS<String> severityObservationValue = (CS<String>)severityObservation.getValue();
			if(severityObservationValue.getCode().equals("L"))
				res.setSeverity(getConceptByGlobalProperty("allergy.concept.severity.mild"));
			if(severityObservationValue.getCode().equals("M"))
				res.setSeverity(getConceptByGlobalProperty("allergy.concept.severity.moderate"));
			if(severityObservationValue.getCode().equals("H"))
					res.setSeverity(getConceptByGlobalProperty("allergy.concept.severity.severe"));
		}
		else if(observation.getCode().getCode().endsWith("INT"))
			res.setSeverity(getConceptByGlobalProperty("allergy.concept.unknown"));
		else
			res.setSeverity(getConceptByGlobalProperty("allergy.concept.unknown"));
		
		// Are there manifestations (reactions)?
		List<EntryRelationship> manifestationRelationship = this.findEntryRelationship(observation, CdaHandlerConstants.ENT_TEMPLATE_MANIFESTATION_RELATION);
		if(manifestationRelationship.size() == 1) // Only if there is one
		{
			Observation manifestationObservation = manifestationRelationship.get(0).getClinicalStatementIfObservation();
			// Get the concept
			Concept reactionConcept = this.m_conceptUtil.getOrCreateConcept((CV)manifestationObservation.getValue());
			if(reactionConcept!=null)
				res.addReaction(new AllergyReaction(res,reactionConcept,null));

		}
		else if(manifestationRelationship.size() > 1)
			throw new DocumentImportException("Allergy importer only supports one manifestation relationship");

		//save allergy
		Context.getPatientService().saveAllergy(res);
    }

	private Concept getConceptByGlobalProperty(String globalPropertyName) {
		String globalProperty = Context.getAdministrationService().getGlobalProperty(globalPropertyName);
		Concept concept = Context.getConceptService().getConceptByUuid(globalProperty);
		if (concept == null) {
			throw new IllegalStateException("Configuration required: " + globalPropertyName);
		}
		return concept;
	}
	
}
