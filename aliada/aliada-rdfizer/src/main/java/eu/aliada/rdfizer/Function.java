// ALIADA - Automatic publication under Linked Data paradigm
//          of library and museum data
//
// Component: aliada-rdfizer
// Responsible: ALIADA Consortiums
package eu.aliada.rdfizer;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.hp.hpl.jena.sparql.util.FmtUtils;

import eu.aliada.rdfizer.datasource.Cache;
import eu.aliada.rdfizer.datasource.rdbms.JobInstance;
import eu.aliada.rdfizer.pipeline.format.marc.frbr.cluster.Cluster;
import eu.aliada.rdfizer.pipeline.format.marc.frbr.cluster.ClusterService;
import eu.aliada.rdfizer.pipeline.format.marc.frbr.model.FrbrDocument;
import eu.aliada.rdfizer.pipeline.format.xml.OXPath;
import eu.aliada.rdfizer.pipeline.nlp.NERSingletonService;
import eu.aliada.shared.ID;
import eu.aliada.shared.Strings;
import eu.aliada.shared.rdfstore.RDFStoreDAO;

/**
 * A generic tool used in templates to invoke some useful functions.
 * 
 * @author Andrea Gazzarini
 * @since 1.0
 */
@Component
public class Function {
	
	@Autowired
	private Cache cache;
	
	private RDFStoreDAO rdfStore = new RDFStoreDAO();
	
	@Autowired
	private OXPath xpath;
	
	@Autowired
	private NERSingletonService ner;
	
	@Autowired
	private ClusterService clusterService;
	
	@Autowired
	protected Configurations configurations;

	/**
	 * Returns the title {@link Cluster} associated with the given heading.
	 * NOTE: although the heading is a string, the current implementation assumes that is actually 
	 * the cluster identifier (i.e. an integer).
	 * 
	 * @param heading the cluster search criterion.
	 * @return the title {@link Cluster} associated with the given heading.
	 */
	public Cluster getTitleCluster(final String heading) {
		try {	
			return clusterService.getTitleCluster(heading);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
 	}
	
	/**
	 * Returns the name {@link Cluster} associated with the given heading.
	 * NOTE: although the heading is a string, the current implementation assumes that is actually 
	 * the cluster identifier (i.e. an integer).
	 * 
	 * @param heading the cluster search criterion.
	 * @param titlesClusters the list of title clusters associated with this heading.
	 * @return the name {@link Cluster} associated with the given heading.
	 */
	public Cluster getNameCluster(final String heading, final Set<Cluster> titlesClusters) {
		try {
			return clusterService.getNameCluster(heading, titlesClusters);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
 	}
	
	/**
	 * Checks if the title cluster associated with the given identifier has been already processed.
	 * 
	 * @param cluster the cluster.
	 * @return true if the title cluster associated with the given identifier has been already processed.
	 */
	public boolean titleClusterHasBeenAlreadyProcessed(final Cluster cluster) {
		return clusterService.titleClusterAlreadyProcessed(cluster.getId());
	}
	
	/**
	 * Marks the title cluster associated with the given identifier as processed.
	 * 
	 * @param cluster the cluster.
	 */
	public void markTitleClusterAsProcessed(final Cluster cluster) {
		clusterService.markTitleClusterAsProcessed(cluster.getId());
	}
	
	/**
	 * Checks if the name cluster associated with the given identifier has been already processed.
	 * 
	 * @param cluster the cluster.
	 * @return true if the name cluster associated with the given identifier has been already processed.
	 */
	public boolean nameClusterHasBeenAlreadyProcessed(final Cluster cluster) {
		return clusterService.nameClusterAlreadyProcessed(cluster.getId());
	}
	
	/**
	 * Marks the name cluster associated with the given identifier as processed.
	 * 
	 * @param cluster the cluster.
	 */
	public void markNameClusterAsProcessed(final Cluster cluster) {
		clusterService.markNameClusterAsProcessed(cluster.getId());
	}
	
	/**
	 * Returns a new generated UID.
	 * 
	 * @return a new generated UID.
	 */
	public long getId() {
		return ID.get();
	}
	
	/**
	 * Returns a {@link List} version of the given array.
	 * 
	 * @param array the input array.
	 * @return a {@link List} version of the given array.
	 */
	public List<String> asList(final String [] array) {
		return array != null ? Arrays.asList(array) : null;
	}
	
	/**
	 * Normalizes and lowercases a given string.
	 * Diacritics are normalized and space are replaced with underscores.
	 * 
	 * @param value the string to be normalized.
	 * @return the normalized string.
	 */
	public String normalize(final String value) {
		return value != null ? uuid(Strings.toURILocalName(value).toLowerCase()) : UUID.randomUUID().toString();
	}
	
	/**
	 * Normalizes a given string.
	 * Diacritics are normalized and space are replaced with underscores.
	 * 
	 * @param value the string to be normalized.
	 * @return the normalized string.
	 */
	public String normalizeWithoutLowercase(final String value) {
		return uuid(Strings.toURILocalName(value));
	}	
	
	public String uuid(final String value) {
		return UUID.nameUUIDFromBytes(value.getBytes()).toString();
	}
	
	/**
	 * Normalizes a given string as {@link Function#normalize} but also removing all spaces and punctuation.
	 * 
	 * @param value the string to be normalized.
	 * @return the normalized string.
	 */
	public String normalizeStrong(final String value) {
		   return value == null ? UUID.randomUUID().toString()
			        : uuid(Normalizer.normalize(value, Form.NFD)
			            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
			            .replaceAll("[^A-Za-z0-9]", ""));
	}		
	
	public String lidoClass(final Integer id, final String value) {
		try {
			JobInstance instance = cache.getJobInstance(id);
			if (instance != null) {
				final String midx = instance.getNamespace();
				int indexOfIdResource = value.indexOf(midx);
				if (indexOfIdResource != -1) {
					int indexOfSlash = value.indexOf("/", (indexOfIdResource + midx.length() + 1));
					if (indexOfSlash != -1) {
						return value.substring((indexOfIdResource + midx.length()), indexOfSlash);
					}
				}
			}
			return null;
		} catch (Exception exception) {
			return null;
		}
	}
	
	/**
	 * Splits out a date expression and returns the first (presumably the birth date) or the second member (the death date).
	 * 
	 * @param dateExpression the date expression (e.g. 1988-1999, -1999,1888-).
	 * @param first true if we want the first member to be returned, false if we want the second.
	 * @return one of the split dates.
	 */
	public String splitDateAndPartialGet(final String dateExpression, final boolean first) {
		final StringBuilder builder = new StringBuilder();
		boolean secondSectionStarted = false;
		boolean atLeastOneDigitHasBeenCollected = false;
		
		for (int i = 0; i < dateExpression.length(); i++) {
			final char ch = dateExpression.charAt(i);
			if (Character.isDigit(ch) ) {
				atLeastOneDigitHasBeenCollected = true;
				if (first || secondSectionStarted) {
					builder.append(ch);
				} 
			} else if (atLeastOneDigitHasBeenCollected || ch == '-') {
				if (first) {
					return builder.length() > 0 ? builder.toString() : null; 
				}
				
				if (!secondSectionStarted) {
					secondSectionStarted = true;
					builder.setLength(0);
				}
			}
		}
		
		return builder.length() > 0 ? builder.toString() : null; 
	}
	
	/**
	 * Returns the ALIADA event type class corresponding to the given CIDOC-CRM class.
	 * 
	 * @param from the CIDOC-CRM event type class.
	 * @return the ALIADA class that corresponds to the given input class.
	 */
	public String toAliadaEventTypeClass(final String from) {
		return cache.getAliadaEventTypeClassFrom(from);
	}
	
	/**
	 * Returns true if the given string is not null and not empty.
	 * 
	 * @param value the string to check.
	 * @return true if the given string is not null and not empty.
	 */
	public boolean isNotNullAndNotEmpty(final String value) {
		return Strings.isNotNullAndNotEmpty(value);
	}
	
	/**
	 * Returns true if the given string is not null, not empty and not "|||||".
	 * 
	 * @param value the string to check.
	 * @return true if the given string is not null, not empty and not "|||||".
	 */
	public boolean isNotNullAndNotEmptyMarc(final String value) {
		return Strings.isNotNullAndNotEmptyMarc(value);
	}
	
	public boolean isNumber(final String value) {
		try {
			Integer.parseInt(value);
			return true;
		} catch (Exception exception) {
			return false;
		}
	}
	
	/**
	 * Returns true if the given string is not null and not empty.
	 * 
	 * @param value the string to check.
	 * @return true if the given string is not null and not empty.
	 */
	public boolean isNullOrEmpty(final String value) {
		return Strings.isNullOrEmpty(value);
	}	
	
	private Map<String, String> typeCache = new HashMap<String, String>();
	
	public String getOntologyTypeURI(final Integer id, final String term) {
		String result = typeCache.get(term);
		if (result == null) {
			try {
				JobInstance instance = cache.getJobInstance(id);
				if (instance != null) {
					final String [] uris = rdfStore.getOntologyTypeURI(instance.getSparqlEndpointUrl(), instance.getSparqlUsername(), instance.getSparqlPassword(), term);
					if (uris != null && uris.length > 0) {
						typeCache.put(term, uris[0]);
						return uris[0];
					}
				}
			} catch(Exception exception){
				// Ignore
			}
		}
		return result;	
	}
	
	
	/**
	 * Extracts a set of named entities from the input data.
	 * The context object is supposed to be a MARC record, so the tag/code input values are trasformed in a MARCXML XPATH.  
	 * 
	 * @param tag the target tag.
	 * @param code the subfield code.
	 * @param record the MARC record.
	 * @return a map of named entities from the input data (ENTITY->TYPE).
	 * @throws XPathExpressionException in case of XPATH failure.
	 */
	public Map<String, String> mner(final String tag, final String code, final Object record) throws XPathExpressionException {
		final List<Node> list = xpath.dfs(tag, code, record);
		final StringBuilder builder = new StringBuilder();
		for (final Node node : list) {
			builder.append(node.getTextContent()).append("\n");
		}
		return ner.detectEntities(builder.toString());
	}
	
	/**
	 * Extracts a set of named entities from the input data.
	 * The context object is supposed to be a MARC record, so the tag/code input values are trasformed in a MARCXML XPATH.  
	 * 
	 * @param tag the target tag.
	 * @param code the subfield code.
	 * @param record the MARC record.
	 * @return a map of named entities from the input data (ENTITY->TYPE).
	 * @throws XPathExpressionException in case of XPATH failure.
	 */
	public Map<String, String> ner(final String text) {
		return ner.detectEntities(text.toString());
	}
	
	public String escape(final String textContent){
		return textContent != null ? FmtUtils.stringEsc(textContent) : null;
	}
	
	public String clean(final String value){
		return Strings.clean(value);
	}
	
	public String removePunct (final String text){		
		String lastChar = String.valueOf(text.charAt(text.length()-1));
		return ":".equals(lastChar) ? text.substring(0, text.length()-2) : text; 	
	}
	
	public String fixPunctuatorMarc21(String text){			
		return text.replaceAll(":", ": ").replaceAll(",", ", ").replaceAll(Pattern.quote((String)"("), Pattern.quote((String)" ("));
	
	}
	
	public String filterString (String text, String filter){		
		String [] filters = filter.split("-");
		int index_1 = Integer.parseInt(filters[0])-1;
		int index_2 = Integer.parseInt(filters[1])-1;
		index_2 = index_2 > text.length() ? text.length()-1 : index_2;
		return text.substring(index_1, index_2);
	}
	
	public String brutalEscape (final String text){
		return text.replaceAll( "\"", "");
	}
	
	public String substringFirstSpace(final String text){		
		if(text.indexOf(" ") > -1){
			return text.substring(0, text.indexOf(" "));	
		}
		return text;
	}
	
	public String substringByPosition(final String text, final String position){
		String result = "";
		try {
			String [] positions = position.split("-");
			if(positions.length == 1){
				result = text.charAt(Integer.parseInt(positions[0])) + "";
			}
			else{
				result = text.substring(Integer.parseInt(positions[0]), Integer.parseInt(positions[1]) + 1);
			}			
		}
		catch(Exception e){			
			e.printStackTrace();			
			return "";		
		}		
		return result.trim();		
	}
	
	public String urlValidator (final String text){
		return text.replaceAll(" ", "-");
	}
	
	public String printAuthors (final FrbrDocument doc){
		StringBuilder result = new StringBuilder(); 
		Collection<List<Cluster>> list = doc.getPersonIDs().values();		
		for (List s : list){
			for (Object c : s){
				Cluster cl = (Cluster) c;
				
				result.append(cl.getId() + ", " );
			}
		}
		return result.toString();
	}
	
	public String mapNameType (final String nameType) {
		if ("1".equals(nameType)){
			return "Person";
		}
		if ("2".equals(nameType)){
			return "Organization";		
		}
		if ("3".equals(nameType)){
			return "Meeting";
		}
		else {
			return "Agent";
		}
	}
	
	public String contractName(String phrase) {
		StringBuilder result = new StringBuilder();
		String [] phraseArray = phrase.split(" ");
		if (phraseArray != null){
			for (int i = 0; i < phraseArray.length; i++ ){
				result.append(phraseArray[i].substring(0, 1).toUpperCase() + phraseArray[i].substring(1));
			}
			return result.toString();
		}
		else return phrase;
	}
	
	public boolean isTagNameEqual(String name, Element root){
		boolean result = false;
		if(root != null && name != null){
			return root.getTagName().startsWith(name);
		}
		return result;
	}
	
	public String getSourceUrl (final String sourceCode) {		
		return configurations.getProperty(sourceCode);
	}
	
	public String mapBookCode (final String code) {
		String cleanedCode =  code;
		String pipe = Pattern.quote("|");
		String ap = Pattern.quote("^");
		cleanedCode = cleanedCode.replaceAll(pipe, "").replaceAll(ap, "");
		return configurations.getProperty(cleanedCode + ".book.label");
	}
	
	public String mapBookUrl (final String code) {
		String cleanedCode =  code;
		String pipe = Pattern.quote("|");
		String ap = Pattern.quote("^");
		cleanedCode = cleanedCode.replaceAll(pipe, "").replaceAll(ap, "");
		return configurations.getProperty(cleanedCode + ".book.url");
	}
	
	public void print(String a) {
		System.out.println(a);
	}
	
	public String[] splitFirstLastIssue (String a) {
		String [] result = new String[2];
		try {
			result[0] = a.split("-")[0];
			result[1] = a.split("-")[1];
		} catch (Exception e) {
			//System.out.println("splitFirstLastIssue is not completed");
		}
		return result;
	}
}
