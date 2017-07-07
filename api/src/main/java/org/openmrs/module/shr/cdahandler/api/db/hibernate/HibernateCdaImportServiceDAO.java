package org.openmrs.module.shr.cdahandler.api.db.hibernate;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.openmrs.*;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.api.db.hibernate.HibernateConceptDAO;
import org.openmrs.module.emrapi.conditionslist.impl.ConditionServiceImpl;
import org.openmrs.module.shr.cdahandler.api.db.CdaImportServiceDAO;
import org.openmrs.module.shr.cdahandler.obs.ExtendedObs;
import org.openmrs.module.shr.contenthandler.api.Content;

/**
 * Hibernate DAO for CDA import service
 */
public class HibernateCdaImportServiceDAO implements CdaImportServiceDAO {
	
	// Hibernate session factory
	private DbSessionFactory m_sessionFactory;
	
    /**
     * @param sessionFactory the sessionFactory to set
     */
    public void setSessionFactory(DbSessionFactory sessionFactory) {
    	this.m_sessionFactory = sessionFactory;
    }

    /**
     * Wrapped DAO
     * @see org.openmrs.module.shr.cdahandler.api.db.CdaImportServiceDAO#saveConceptQuick(org.openmrs.Concept)
     */
	@Override
	public Concept saveConceptQuick(Concept concept) {
		HibernateConceptDAO wrappedDao = new HibernateConceptDAO();
		wrappedDao.setSessionFactory(this.m_sessionFactory.getHibernateSessionFactory());
		return wrappedDao.saveConcept(concept);
	}
	
	@Override
	public List<Order> getOrdersByAccessionNumber(String an, boolean includeVoided) {
		Criteria crit = this.m_sessionFactory.getCurrentSession().createCriteria(Order.class)
				.add(Restrictions.eq("accessionNumber", an));
		if(!includeVoided)
				crit.add(Restrictions.eq("voided", includeVoided));
		return (List<Order>)crit.list();
	}
	
	@Override
	public List<Obs> getObsByAccessionNumber(String an, boolean includeVoided) {
		Criteria crit = this.m_sessionFactory.getCurrentSession().createCriteria(Obs.class)
				.add(Restrictions.eq("accessionNumber", an));
		if(!includeVoided)
				crit.add(Restrictions.eq("voided", includeVoided));
		return (List<Obs>)crit.list();
	}

	@Override
    public ConceptReferenceTerm saveReferenceTermQuick(ConceptReferenceTerm referenceTerm) {
		HibernateConceptDAO wrappedDao = new HibernateConceptDAO();
		wrappedDao.setSessionFactory(this.m_sessionFactory.getHibernateSessionFactory());
		return wrappedDao.saveConceptReferenceTerm(referenceTerm);
    }

	/**
	 * Get an extended obs
	 * @see org.openmrs.module.shr.cdahandler.api.db.CdaImportServiceDAO#getExtendedObs(java.lang.Integer)
	 */
	@Override
    public ExtendedObs getExtendedObs(Integer id) {
		return (ExtendedObs)this.m_sessionFactory.getCurrentSession().get(ExtendedObs.class, id);
    }

	/**
	 * Get concept source by name
	 * @see org.openmrs.module.shr.cdahandler.api.db.CdaImportServiceDAO#getConceptSourceByHl7(java.lang.String)
	 */
	@Override
    public ConceptSource getConceptSourceByHl7(String hl7) {

		Criteria crit = this.m_sessionFactory.getCurrentSession().createCriteria(ConceptSource.class)
				.add(Restrictions.eq("hl7Code", hl7));
		return (ConceptSource)crit.uniqueResult();
	}

}
