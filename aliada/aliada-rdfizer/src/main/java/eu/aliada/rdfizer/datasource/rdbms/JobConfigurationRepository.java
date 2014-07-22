// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-rdfizer
// Responsible: ALIADA Consortium
package eu.aliada.rdfizer.datasource.rdbms;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Job Configuration repository.
 * Provides CRUD access to job configuration entity.
 * 
 * @author Andrea Gazzarini
 * @since 1.0
 */
public interface JobConfigurationRepository extends PagingAndSortingRepository<JobConfiguration, Integer> {
	// No need of other methods at the moment
}