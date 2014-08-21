package org.openmrs.module.shr.cdahandler.api.impl.test.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.marc.everest.datatypes.BL;
import org.marc.everest.datatypes.II;
import org.marc.everest.datatypes.PQ;
import org.marc.everest.datatypes.TS;
import org.marc.everest.datatypes.generic.IVL;
import org.marc.everest.datatypes.generic.LIST;
import org.marc.everest.formatters.xml.datatypes.r1.DatatypeFormatter;
import org.marc.everest.formatters.xml.datatypes.r1.R1FormatterCompatibilityMode;
import org.marc.everest.formatters.xml.its1.XmlIts1Formatter;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.ClinicalDocument;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Component2;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Component3;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.Custodian;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.DocumentationOf;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.ServiceEvent;
import org.marc.everest.rmim.uv.cdar2.pocd_mt000040uv.StructuredBody;
import org.marc.everest.rmim.uv.cdar2.vocabulary.ActRelationshipHasComponent;
import org.marc.everest.rmim.uv.cdar2.vocabulary.x_BasicConfidentialityKind;
import org.openmrs.module.shr.cdahandler.CdaHandlerConstants;

/**
 * A test utility that creates CDA Document
 */
public class CdaDocumentCreatorUtil {

	protected final static Log log = LogFactory.getLog(CdaDocumentCreatorUtil.class);

	/**
	 * Create the document
	 * @return
	 */
	private final static ClinicalDocument createDocumentHeader(String documentTypeCode, String... templateIds)
	{
		ClinicalDocument retVal = new ClinicalDocument();
		// Type and template
		retVal.setTypeId("2.16.840.1.113883.1.3", "POCD_HD000040");
		retVal.setTemplateId(new LIST<II>());
		for(String tplId : templateIds)
			retVal.getTemplateId().add(new II(tplId));
		
		// Set ID and code
		retVal.setId(UUID.randomUUID());
		retVal.setCode(documentTypeCode, CdaHandlerConstants.CODE_SYSTEM_LOINC);
		retVal.setTitle("Autogenerated Test CDA");
		retVal.setEffectiveTime(TS.now());
		retVal.setConfidentialityCode(x_BasicConfidentialityKind.Normal);
		retVal.setLanguageCode("en-US");
		
		// Set participants
		retVal.getRecordTarget().add(EntityCreatorUtil.createRecordTarget());
		retVal.getAuthor().add(EntityCreatorUtil.createAuthor("1", "Bob", "Dolin"));
		retVal.getAuthor().add(EntityCreatorUtil.createAuthor("2", "Sally", "Smith"));
		retVal.setCustodian(EntityCreatorUtil.createCustodian());
		retVal.getParticipant().add(EntityCreatorUtil.createFatherParticipant());
		retVal.getParticipant().add(EntityCreatorUtil.createSpouseParticipant());
		retVal.getParticipant().add(EntityCreatorUtil.createFatherOfBabyParticipant());
		
		// Set service event
		DocumentationOf documentationOf = new DocumentationOf();
		ServiceEvent serviceEvent = new ServiceEvent();
		TS fourMonthsAgo = TS.now().subtract(new PQ(BigDecimal.valueOf(4), "mo"));
		fourMonthsAgo.setDateValuePrecision(TS.HOUR);
		serviceEvent.setEffectiveTime(new IVL<TS>(fourMonthsAgo, TS.now()));
		documentationOf.setServiceEvent(serviceEvent);
		retVal.getDocumentationOf().add(documentationOf);
		
		retVal.setComponent(new Component2());
		retVal.getComponent().setBodyChoice(new StructuredBody());
		// Return the header
		return retVal;
	}
	
	/**
	 * Create an APS Document
	 */
	public final static InputStream createApsDocument()
	{

		ClinicalDocument retVal = createDocumentHeader("57055-6", "1.3.6.1.4.1.19376.1.5.3.1.1.2", "1.3.6.1.4.1.19376.1.5.3.1.1.11.2");
		
		// Add sections
		StructuredBody body = retVal.getComponent().getBodyChoiceIfStructuredBody();
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createEstimatedDeliveryDatesSection()));
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createVisitSummaryFlowsheetSection()));
		/*body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createHistoryOfSurgicalProceduresSection()));*/
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createCodedAntenatalTestingAndSurveillanceSection()));
		/*
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createAllergiesAndOtherAdverseReactionsSection()));
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createMedicationsSection()));
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createCarePlanSection()));
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createAdvanceDirectivesSection()));
		body.getComponent().add(new Component3(ActRelationshipHasComponent.HasComponent, BL.TRUE, SectionCreatorUtil.createProblemsSection()));*/
		
		return streamDocument(retVal);
	}
	
	/**
	 * Log document
	 */
	public final static InputStream streamDocument(ClinicalDocument doc)
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		XmlIts1Formatter formatter = new XmlIts1Formatter();
		formatter.getGraphAides().add(new DatatypeFormatter(R1FormatterCompatibilityMode.ClinicalDocumentArchitecture));
		formatter.setValidateConformance(false);
		formatter.graph(outStream, doc);
		log.error(outStream.toString());
		return new ByteArrayInputStream(outStream.toByteArray());
	}
	
}
